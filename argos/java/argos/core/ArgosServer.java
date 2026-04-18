/*
 * Argos - AI-powered binary analysis
 * Copyright (c) 2024-2025
 * 
 * Licensed under the Apache License, Version 2.0
 */

package argos.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.ee10.servlet.FilterHolder;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;

import java.util.EnumSet;
import jakarta.servlet.DispatcherType;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import generic.concurrent.GThreadPool;
import ghidra.framework.plugintool.PluginTool;
import ghidra.program.model.listing.Program;
import ghidra.util.Msg;

import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import argos.plugin.Settings;
import argos.plugin.ConfigChangeListener;
import argos.resources.ResourceProvider;
import argos.resources.impl.ProgramListResource;
import argos.services.ArgosMcpService;
import argos.capabilities.Capability;
import argos.capabilities.data.DataCapability;
import argos.capabilities.datatypes.DataTypeCapability;
import argos.capabilities.decompiler.DecompilerCapability;
import argos.capabilities.functions.FunctionCapability;
import argos.capabilities.memory.MemoryCapability;
import argos.capabilities.project.ProjectCapability;
import argos.capabilities.strings.StringCapability;
import argos.capabilities.structures.StructureCapability;
import argos.capabilities.symbols.SymbolCapability;
import argos.capabilities.xrefs.CrossReferencesCapability;
import argos.capabilities.comments.CommentCapability;
import argos.capabilities.bookmarks.BookmarkCapability;
import argos.capabilities.imports.ImportExportCapability;
import argos.capabilities.dataflow.DataFlowCapability;
import argos.capabilities.callgraph.CallGraphCapability;
import argos.capabilities.constants.ConstantSearchCapability;
import argos.capabilities.vtable.VtableCapability;
import argos.util.ArgosInternalServiceRegistry;

/**
 * Manages the Model Context Protocol server at the application level.
 * This class is responsible for initializing, configuring, and starting the MCP server,
 * as well as registering all resources and tools. It handles multiple tools accessing
 * the same server instance and coordinates program lifecycle events across tools.
 */
public class ArgosServer implements ArgosMcpService, ConfigChangeListener {
    private static final String MCP_MSG_ENDPOINT = "/mcp/message";
    private static final String MCP_SERVER_NAME = "Argos";
    private static final String MCP_SERVER_VERSION = "1.0.0";

    private final McpSyncServer server;
    private ResilientStreamableServerTransportProvider currentTransportProvider;
    private Server httpServer;
    private final GThreadPool threadPool;
    private final Settings settings;

    private final List<ResourceProvider> resourceProviders = new ArrayList<>();
    private final List<Capability> capabilityProviders = new ArrayList<>();
    private volatile boolean serverReady = false;

    // Multi-tool tracking
    private final Set<PluginTool> registeredCapabilities = ConcurrentHashMap.newKeySet();
    private final Map<Program, Set<PluginTool>> programToTools = new ConcurrentHashMap<>();
    private volatile Program activeProgram;
    private volatile PluginTool activeTool;

    // Mode tracking - headless mode has no GUI context
    private final boolean headlessMode;

    /**
     * Constructor for GUI mode. Initializes the MCP server with all capabilities.
     * This constructor creates a Settings from the PluginTool for backward compatibility.
     * @param pluginTool The plugin tool, used for configuration
     */
    public ArgosServer(PluginTool pluginTool) {
        this(new Settings(pluginTool), false);
    }

    /**
     * Constructor for headless mode. Initializes the MCP server with all capabilities.
     * @param settings The configuration manager to use
     */
    public ArgosServer(Settings settings) {
        this(settings, true);
    }

    /**
     * Primary constructor with Settings and mode flag.
     * @param settings The configuration manager to use
     * @param headlessMode True if running in headless mode (no GUI context)
     */
    private ArgosServer(Settings settings, boolean headlessMode) {
        this.headlessMode = headlessMode;
        // Store configuration
        this.settings = settings;
        ArgosInternalServiceRegistry.registerService(Settings.class, settings);

        // Register as a config change listener
        settings.addConfigChangeListener(this);

        // Initialize thread pool
        threadPool = GThreadPool.getPrivateThreadPool("Argos");
        ArgosInternalServiceRegistry.registerService(GThreadPool.class, threadPool);

        // Initialize MCP transport provider with baseUrl
        recreateTransportProvider();

        // Configure server capabilities
        McpSchema.ServerCapabilities serverCapabilities = McpSchema.ServerCapabilities.builder()
            .prompts(true)
            .resources(true, true)
            .tools(true)
            .build();

        // Initialize MCP server
        server = McpServer.sync(currentTransportProvider)
            .serverInfo(MCP_SERVER_NAME, MCP_SERVER_VERSION)
            .capabilities(serverCapabilities)
            .build();

        // Make server and server manager available via service registry
        ArgosInternalServiceRegistry.registerService(McpSyncServer.class, server);
        ArgosInternalServiceRegistry.registerService(ArgosServer.class, this);

        // Create and register resource providers
        initializeResourceProviders();

        // Create and register tool providers
        initializeCapabilities();
    }

    /**
     * Initialize and register all resource providers
     */
    private void initializeResourceProviders() {
        resourceProviders.add(new ProgramListResource(server));

        // Register all resources with the server
        for (ResourceProvider provider : resourceProviders) {
            provider.register();
        }
    }

    /**
     * Initialize and register all tool providers
     */
    private void initializeCapabilities() {
        // Create tool providers
        capabilityProviders.add(new SymbolCapability(server));
        capabilityProviders.add(new StringCapability(server));
        capabilityProviders.add(new FunctionCapability(server));
        capabilityProviders.add(new DataCapability(server));
        capabilityProviders.add(new DecompilerCapability(server));
        capabilityProviders.add(new MemoryCapability(server));
        capabilityProviders.add(new ProjectCapability(server, headlessMode));
        capabilityProviders.add(new CrossReferencesCapability(server));
        capabilityProviders.add(new DataTypeCapability(server));
        capabilityProviders.add(new StructureCapability(server));
        capabilityProviders.add(new CommentCapability(server));
        capabilityProviders.add(new BookmarkCapability(server));
        capabilityProviders.add(new ImportExportCapability(server));
        capabilityProviders.add(new DataFlowCapability(server));
        capabilityProviders.add(new CallGraphCapability(server));
        capabilityProviders.add(new ConstantSearchCapability(server));
        capabilityProviders.add(new VtableCapability(server));

        // Register all tools with the server
        // Note: As of MCP SDK v0.14.0, tool registration is idempotent and replaces duplicates
        for (Capability capability : capabilityProviders) {
            capability.addCapabilities();
        }
    }

    /**
     * Start the MCP server
     */
    public void startServer() {
        // Check if server is enabled in config
        if (!settings.isServerEnabled()) {
            Msg.info(this, "MCP server is disabled in configuration. Not starting server.");
            return;
        }

        // Check if server is already running
        if (httpServer != null && httpServer.isRunning()) {
            Msg.warn(this, "MCP server is already running.");
            return;
        }

        int serverPort = settings.getServerPort();
        String serverHost = settings.getServerHost();
        String baseUrl = "http://" + serverHost + ":" + serverPort;
        Msg.info(this, "Starting MCP server on " + baseUrl);

        ServletContextHandler servletContextHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
        servletContextHandler.setContextPath("/");

        // Add API key authentication filter if enabled
        if (settings.isApiKeyEnabled()) {
            FilterHolder filterHolder = new FilterHolder(new ApiKeyAuthFilter(settings));
            servletContextHandler.addFilter(filterHolder, "/*", EnumSet.of(DispatcherType.REQUEST));
            Msg.info(this, "API key authentication enabled for MCP server");
        }

        // Add request logging filter for debugging (only logs when debug mode is enabled)
        FilterHolder loggingFilter = new FilterHolder(new RequestLoggingFilter(settings));
        servletContextHandler.addFilter(loggingFilter, "/*", EnumSet.of(DispatcherType.REQUEST));

        ServletHolder servletHolder = new ServletHolder(currentTransportProvider);
        servletHolder.setAsyncSupported(true);
        servletContextHandler.addServlet(servletHolder, "/*");

        // Create server with specific host binding for security
        httpServer = new Server();
        ServerConnector connector = new ServerConnector(httpServer);
        connector.setHost(serverHost);
        connector.setPort(serverPort);
        connector.setIdleTimeout(600000); // 10 minutes - defense in depth against stale connections
        httpServer.addConnector(connector);
        httpServer.setHandler(servletContextHandler);

        threadPool.submit(() -> {
            try {
                httpServer.start();
                Msg.info(this, "MCP server started successfully");

                // Mark server as ready
                serverReady = true;

                // join() blocks until the server stops, which is expected behavior
                httpServer.join();
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Msg.info(this, "MCP server was interrupted - this is normal during shutdown");
                    Thread.currentThread().interrupt(); // Restore interrupt status
                } else {
                    Msg.error(this, "Error starting MCP server", e);
                }
            }
        });

        // Wait for server to be ready
        int maxWaitTime = 10000; // 10 seconds
        int waitInterval = 100; // 100ms
        int totalWait = 0;

        while (!serverReady && totalWait < maxWaitTime) {
            try {
                Thread.sleep(waitInterval);
                totalWait += waitInterval;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Msg.warn(this, "Interrupted while waiting for server startup");
                return;
            }
        }

        if (serverReady) {
        } else {
            Msg.error(this, "Server failed to start within timeout");
        }

    }

    @Override
    public void registerTool(PluginTool tool) {
        registeredCapabilities.add(tool);
        Msg.debug(this, "Registered tool with MCP server: " + tool.getName());
    }

    @Override
    public void unregisterTool(PluginTool tool) {
        registeredCapabilities.remove(tool);

        // Remove tool from all program mappings
        for (Set<PluginTool> tools : programToTools.values()) {
            tools.remove(tool);
        }

        // Clear active tool if it's the one being unregistered
        if (activeTool == tool) {
            activeTool = null;
            activeProgram = null;
        }

        Msg.debug(this, "Unregistered tool from MCP server: " + tool.getName());
    }

    @Override
    public void programOpened(Program program, PluginTool tool) {
        // Add to program-tool mapping
        programToTools.computeIfAbsent(program, k -> ConcurrentHashMap.newKeySet()).add(tool);

        // Set as active program
        setActiveProgram(program, tool);

        // Notify providers
        for (ResourceProvider provider : resourceProviders) {
            provider.programOpened(program);
        }

        for (Capability capability : capabilityProviders) {
            capability.programOpened(program);
        }

        Msg.debug(this, "Program opened in tool " + tool.getName() + ": " + program.getName());
    }

    @Override
    public void programClosed(Program program, PluginTool tool) {
        // Remove from program-tool mapping
        Set<PluginTool> tools = programToTools.get(program);
        if (tools != null) {
            tools.remove(tool);
            if (tools.isEmpty()) {
                programToTools.remove(program);
            }
        }

        // Clear active program if it was the one being closed
        if (activeProgram == program && activeTool == tool) {
            activeProgram = null;
            activeTool = null;
        }

        // Notify providers only if this was the last tool with the program
        if (tools == null || tools.isEmpty()) {
            for (ResourceProvider provider : resourceProviders) {
                provider.programClosed(program);
            }

            for (Capability capability : capabilityProviders) {
                capability.programClosed(program);
            }
        }

        Msg.debug(this, "Program closed in tool " + tool.getName() + ": " + program.getName());
    }

    @Override
    public boolean isServerRunning() {
        return httpServer != null && httpServer.isRunning() && serverReady;
    }

    /**
     * Check if running in headless mode (no GUI context)
     * @return true if running in headless mode
     */
    public boolean isHeadlessMode() {
        return headlessMode;
    }

    @Override
    public int getServerPort() {
        if (settings != null) {
            return settings.getServerPort();
        }
        return -1;
    }

    @Override
    public Program getActiveProgram() {
        return activeProgram;
    }

    @Override
    public void setActiveProgram(Program program, PluginTool tool) {
        this.activeProgram = program;
        this.activeTool = tool;
        Msg.debug(this, "Active program changed to: " + (program != null ? program.getName() : "null") +
                  " in tool: " + (tool != null ? tool.getName() : "null"));
    }

    /**
     * Check if the server is ready to accept connections
     * @return true if the server is ready
     */
    public boolean isServerReady() {
        return httpServer != null && httpServer.getState().equals(org.eclipse.jetty.server.Server.STARTED);
    }

    /**
     * Restart the MCP server with new configuration.
     * This method gracefully stops the current server and starts a new one.
     */
    public void restartServer() {
        Msg.info(this, "Restarting MCP server...");

        // Check if server is enabled in config
        if (!settings.isServerEnabled()) {
            Msg.info(this, "MCP server is disabled in configuration. Stopping server.");
            stopServer();
            return;
        }

        // Stop the current server
        stopServer();

        // Recreate transport provider with new port configuration
        recreateTransportProvider();

        // Start the server with new configuration
        startServer();

        Msg.info(this, "MCP server restart complete");
    }

    /**
     * Recreate the transport provider with updated configuration.
     * This is necessary when configuration changes during server restart.
     */
    private void recreateTransportProvider() {
        int serverPort = settings.getServerPort();
        String serverHost = settings.getServerHost();
        String baseUrl = "http://" + serverHost + ":" + serverPort;

        // Create ObjectMapper configured to ignore unknown properties
        // This is a workaround for MCP SDK issue #724 where the SDK doesn't handle
        // newer protocol fields (e.g., from VS Code MCP client using protocol 2025-11-25)
        // See: https://github.com/modelcontextprotocol/java-sdk/issues/724
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(objectMapper);

        // Create new transport provider with updated configuration
        // Uses ResilientStreamableServerTransportProvider (forked from MCP SDK) to fix
        // a bug where serialization errors permanently kill the session.
        currentTransportProvider = ResilientStreamableServerTransportProvider.builder()
            .mcpEndpoint(MCP_MSG_ENDPOINT)
            .jsonMapper(jsonMapper)
            .keepAliveInterval(java.time.Duration.ofSeconds(30))
            .build();
    }

    /**
     * Stop the MCP server without full shutdown cleanup.
     * This is used internally for restart operations.
     */
    private void stopServer() {
        Msg.info(this, "Stopping MCP server...");

        // Mark server as not ready
        serverReady = false;

        // Shut down the HTTP server
        if (httpServer != null) {
            try {
                httpServer.stop();
                httpServer = null;
            } catch (Exception e) {
                Msg.error(this, "Error stopping HTTP server", e);
            }
        }

        Msg.info(this, "MCP server stopped");
    }

    @Override
    public void onConfigChanged(String category, String name, Object oldValue, Object newValue) {
        // Handle server configuration changes
        if (Settings.SERVER_OPTIONS.equals(category)) {
            if (Settings.SERVER_PORT.equals(name)) {
                Msg.info(this, "Server port changed from " + oldValue + " to " + newValue + ". Restarting server...");
                restartServer();
            } else if (Settings.SERVER_HOST.equals(name)) {
                Msg.info(this, "Server host changed from " + oldValue + " to " + newValue + ". Restarting server...");
                restartServer();
            } else if (Settings.SERVER_ENABLED.equals(name)) {
                Msg.info(this, "Server enabled setting changed from " + oldValue + " to " + newValue + ". Restarting server...");
                restartServer();
            } else if (Settings.API_KEY_ENABLED.equals(name)) {
                Msg.info(this, "API key authentication setting changed from " + oldValue + " to " + newValue + ". Restarting server...");
                restartServer();
            } else if (Settings.API_KEY.equals(name)) {
                Msg.info(this, "API key changed. Restarting server...");
                restartServer();
            }
        }
    }

    /**
     * Shut down the MCP server and clean up resources
     */
    public void shutdown() {
        Msg.info(this, "Shutting down MCP server...");

        // Remove config change listener and dispose
        if (settings != null) {
            settings.removeConfigChangeListener(this);
        }

        // Clear all tool registrations
        registeredCapabilities.clear();
        programToTools.clear();
        activeProgram = null;
        activeTool = null;

        // Notify all providers to clean up
        for (ResourceProvider provider : resourceProviders) {
            provider.cleanup();
        }

        for (Capability capability : capabilityProviders) {
            capability.cleanup();
        }

        // Shut down the HTTP server
        if (httpServer != null) {
            try {
                httpServer.stop();
            } catch (Exception e) {
                Msg.error(this, "Error stopping HTTP server", e);
            }
        }

        // Close the MCP server gracefully
        if (server != null) {
            server.closeGracefully();
        }

        // Shut down the thread pool
        if (threadPool != null) {
            threadPool.shutdownNow();
        }

        serverReady = false;

        Msg.info(this, "MCP server shutdown complete");
    }

    /**
     * Get the list of registered tool providers for debug/diagnostic purposes.
     * @return List of tool providers, or empty list if none registered
     */
    public List<Capability> getCapabilities() {
        return new ArrayList<>(capabilityProviders);
    }

    /**
     * Get the number of registered PluginTools for debug/diagnostic purposes.
     * @return Number of registered tools
     */
    public int getRegisteredToolsCount() {
        return registeredCapabilities.size();
    }

    /**
     * Get the server host binding for debug/diagnostic purposes.
     * @return Server host string, or null if not configured
     */
    public String getServerHost() {
        if (settings != null) {
            return settings.getServerHost();
        }
        return null;
    }
}
