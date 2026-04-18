/*
 * Argos - AI-powered binary analysis
 * Copyright (c) 2024-2025
 * 
 * Licensed under the Apache License, Version 2.0
 */

package argos.headless;

import java.io.File;
import java.io.IOException;

import ghidra.base.project.GhidraProject;
import ghidra.framework.Application;
import ghidra.framework.ApplicationConfiguration;
import ghidra.framework.HeadlessGhidraApplicationConfiguration;
import ghidra.framework.model.ProjectLocator;
import utility.application.ApplicationLayout;
import ghidra.util.Msg;
import ghidra.GhidraApplicationLayout;

import argos.plugin.Settings;
import argos.core.ArgosServer;

/**
 * Headless launcher for Argos MCP server.
 * <p>
 * This class enables Argos to run in headless Ghidra mode without the GUI plugin system.
 * It can be invoked from pyghidra or other headless contexts.
 * <p>
 * Projects are ephemeral in stdio mode - created in temp directories and cleaned up on exit.
 * <p>
 * Usage from pyghidra:
 * <pre>
 * from argos.headless import ArgosHeadlessLauncher
 *
 * launcher = ArgosHeadlessLauncher()
 * launcher.start()
 *
 * # Server is now running
 * if launcher.waitForServer(30000):
 *     print(f"Server ready on port {launcher.getPort()}")
 *
 * # Do work...
 *
 * launcher.stop()
 * </pre>
 */
public class ArgosHeadlessLauncher {

    private ArgosServer serverManager;
    private Settings configManager;
    private File configFile;
    private boolean autoInitializeGhidra;
    private boolean useRandomPort;
    private File projectLocation;
    private String projectName;
    private GhidraProject ghidraProject;

    /**
     * Constructor with default settings (in-memory configuration)
     */
    public ArgosHeadlessLauncher() {
        this(null, true, false);
    }

    /**
     * Constructor with configuration file
     * @param configFile The configuration file to load, or null for defaults
     */
    public ArgosHeadlessLauncher(File configFile) {
        this(configFile, true, false);
    }

    /**
     * Constructor with configuration file path
     * Convenience constructor for PyGhidra scripts that use string paths
     * @param configFilePath Path to the configuration file
     */
    public ArgosHeadlessLauncher(String configFilePath) {
        this(new File(configFilePath), true, false);
    }

    /**
     * Constructor with random port option
     * @param configFile The configuration file to load, or null for defaults
     * @param useRandomPort Whether to use a random available port instead of configured port
     */
    public ArgosHeadlessLauncher(File configFile, boolean useRandomPort) {
        this(configFile, true, useRandomPort);
    }

    /**
     * Constructor with full control
     * @param configFile The configuration file to load, or null for defaults
     * @param autoInitializeGhidra Whether to automatically initialize Ghidra if not already initialized
     * @param useRandomPort Whether to use a random available port instead of configured port
     */
    public ArgosHeadlessLauncher(File configFile, boolean autoInitializeGhidra, boolean useRandomPort) {
        this(configFile, autoInitializeGhidra, useRandomPort, null, null);
    }

    /**
     * Constructor with project parameters
     * @param configFile The configuration file to load, or null for defaults
     * @param useRandomPort Whether to use a random available port instead of configured port
     * @param projectLocation The directory where projects are stored (e.g., .argos/projects)
     * @param projectName The name of the project to create/open
     */
    public ArgosHeadlessLauncher(File configFile, boolean useRandomPort, File projectLocation, String projectName) {
        this(configFile, true, useRandomPort, projectLocation, projectName);
    }

    /**
     * Constructor with full control and project parameters
     * @param configFile The configuration file to load, or null for defaults
     * @param autoInitializeGhidra Whether to automatically initialize Ghidra if not already initialized
     * @param useRandomPort Whether to use a random available port instead of configured port
     * @param projectLocation The directory where projects are stored (e.g., .argos/projects), or null for no project
     * @param projectName The name of the project to create/open, or null for no project
     */
    public ArgosHeadlessLauncher(File configFile, boolean autoInitializeGhidra, boolean useRandomPort,
                               File projectLocation, String projectName) {
        this.configFile = configFile;
        this.autoInitializeGhidra = autoInitializeGhidra;
        this.useRandomPort = useRandomPort;
        this.projectLocation = projectLocation;
        this.projectName = projectName;
    }

    /**
     * Start the MCP server in headless mode
     * @throws IOException if configuration file cannot be read
     * @throws IllegalStateException if Ghidra is not initialized and autoInitializeGhidra is false
     */
    public void start() throws IOException {
        Msg.info(this, "Starting Argos MCP server in headless mode...");

        // Initialize Ghidra application if needed
        if (!Application.isInitialized()) {
            if (autoInitializeGhidra) {
                Msg.info(this, "Initializing Ghidra application in headless mode...");
                try {
                    ApplicationLayout layout = new GhidraApplicationLayout();
                    ApplicationConfiguration config = new HeadlessGhidraApplicationConfiguration();
                    Application.initializeApplication(layout, config);
                    Msg.info(this, "Ghidra application initialized");
                } catch (IOException e) {
                    throw new IOException("Failed to initialize Ghidra application layout", e);
                }
            } else {
                throw new IllegalStateException(
                    "Ghidra application is not initialized. " +
                    "Call Application.initializeApplication() first or set autoInitializeGhidra=true");
            }
        }

        // Create config manager based on mode
        if (configFile != null) {
            Msg.info(this, "Loading configuration from: " + configFile.getAbsolutePath());
            configManager = new Settings(configFile);
        } else {
            Msg.info(this, "Using default configuration (in-memory)");
            configManager = new Settings();
        }

        // Use random port if requested
        if (useRandomPort) {
            int randomPort = configManager.setRandomAvailablePort();
            Msg.info(this, "Using random port: " + randomPort);
        }

        // Create/open persistent project if location and name specified
        if (projectLocation != null && projectName != null) {
            try {
                ghidraProject = createOrOpenProject(projectLocation, projectName);
                Msg.info(this, "Opened project: " + projectName);
            } catch (Exception e) {
                throw new IOException("Failed to create/open project: " + projectName, e);
            }
        }

        // Create and start server manager
        serverManager = new ArgosServer(configManager);
        serverManager.startServer();

        Msg.info(this, "Argos MCP server started in headless mode");
    }

    /**
     * Create or open a persistent Ghidra project
     * @param location The directory where projects are stored
     * @param name The project name
     * @return The opened GhidraProject
     * @throws IOException if project creation/opening fails
     */
    private GhidraProject createOrOpenProject(File location, String name) throws IOException {
        try {
            // Ensure project directory exists
            if (!location.exists()) {
                if (!location.mkdirs()) {
                    throw new IOException("Failed to create project directory: " + location.getAbsolutePath());
                }
            }

            String projectLocationPath = location.getAbsolutePath();
            ProjectLocator locator = new ProjectLocator(projectLocationPath, name);

            // Check if project already exists
            if (locator.getMarkerFile().exists() && locator.getProjectDir().exists()) {
                Msg.info(this, "Opening existing project: " + name + " at " + projectLocationPath);
                return GhidraProject.openProject(projectLocationPath, name, true);
            } else {
                Msg.info(this, "Creating new project: " + name + " at " + projectLocationPath);
                return GhidraProject.createProject(projectLocationPath, name, false);
            }
        } catch (Exception e) {
            throw new IOException("Failed to create/open project: " + name, e);
        }
    }

    /**
     * Stop the server and cleanup
     */
    public void stop() {
        Msg.info(this, "Stopping Argos MCP server...");

        if (serverManager != null) {
            serverManager.shutdown();
            serverManager = null;
        }

        if (configManager != null) {
            configManager.dispose();
            configManager = null;
        }

        // Close Ghidra project (but don't delete it - it's persistent)
        if (ghidraProject != null) {
            try {
                Msg.info(this, "Closing project: " + projectName);
                ghidraProject.close();
            } catch (Exception e) {
                Msg.error(this, "Error closing project: " + e.getMessage(), e);
            } finally {
                ghidraProject = null;
            }
        }

        Msg.info(this, "Argos MCP server stopped");
    }

    /**
     * Get the server port
     * @return The server port, or -1 if server is not running
     */
    public int getPort() {
        if (serverManager != null) {
            return serverManager.getServerPort();
        }
        return -1;
    }

    /**
     * Check if server is running
     * @return True if the server is running
     */
    public boolean isRunning() {
        return serverManager != null && serverManager.isServerRunning();
    }

    /**
     * Check if server is ready to accept connections
     * @return True if the server is ready
     */
    public boolean isServerReady() {
        return serverManager != null && serverManager.isServerReady();
    }

    /**
     * Wait for server to be ready
     * @param timeoutMs Maximum time to wait in milliseconds
     * @return True if server became ready within timeout, false otherwise
     */
    public boolean waitForServer(long timeoutMs) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (isRunning() && isServerReady()) {
                return true;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /**
     * Get the configuration manager
     * @return The configuration manager, or null if not started
     */
    public Settings getSettings() {
        return configManager;
    }

    /**
     * Get the server manager
     * @return The server manager, or null if not started
     */
    public ArgosServer getServerManager() {
        return serverManager;
    }

    /**
     * Main method for standalone execution
     * <p>
     * Example usage:
     * <pre>
     * java -cp ... argos.headless.ArgosHeadlessLauncher [configFile] [projectDir] [projectName]
     * </pre>
     *
     * @param args Optional: configFile, projectDir, projectName
     */
    public static void main(String[] args) {
        // Parse arguments
        File configFile = null;
        File projectDir = null;
        String projectName = "default";

        if (args.length > 0 && !args[0].isEmpty()) {
            configFile = new File(args[0]);
            if (!configFile.exists()) {
                System.err.println("Configuration file not found: " + configFile.getAbsolutePath());
                System.exit(1);
            }
        }

        if (args.length > 1 && !args[1].isEmpty()) {
            projectDir = new File(args[1]);
        } else {
            // Default to /work/projects
            projectDir = new File("/work/projects");
        }

        if (args.length > 2 && !args[2].isEmpty()) {
            projectName = args[2];
        }

        System.out.println("Project directory: " + projectDir.getAbsolutePath());
        System.out.println("Project name: " + projectName);

        // Create and start launcher with project
        ArgosHeadlessLauncher launcher = new ArgosHeadlessLauncher(configFile, false, projectDir, projectName);

        try {
            launcher.start();

            // Wait for server to be ready
            if (launcher.waitForServer(30000)) {
                System.out.println("Argos MCP server ready on port " + launcher.getPort());
                System.out.println("Press Ctrl+C to stop");

                // Add shutdown hook for clean exit
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    System.out.println("\nShutting down...");
                    launcher.stop();
                }));

                // Keep running until interrupted
                try {
                    Thread.currentThread().join();
                } catch (InterruptedException e) {
                    // Normal exit
                }
            } else {
                System.err.println("Failed to start server within timeout");
                System.exit(1);
            }
        } catch (Exception e) {
            System.err.println("Error starting server: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
