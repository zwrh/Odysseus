/*
 * Argos - AI-powered binary analysis
 * Copyright (c) 2024-2025
 * 
 * Licensed under the Apache License, Version 2.0
 */

package argos.plugin;

import java.util.List;

import ghidra.app.plugin.PluginCategoryNames;
import ghidra.app.plugin.ProgramPlugin;
import ghidra.framework.plugintool.PluginInfo;
import ghidra.framework.plugintool.PluginTool;
import ghidra.framework.plugintool.util.PluginStatus;
import ghidra.program.model.listing.Program;
import ghidra.util.Msg;

import argos.services.ArgosMcpService;
import argos.ui.ArgosProvider;
import argos.util.ArgosInternalServiceRegistry;

/**
 * Argos tool plugin for Headless Ghidra.
 * This tool-level plugin connects to the application-level MCP server
 * and handles program lifecycle events for this specific tool.
 */
@PluginInfo(
    status = PluginStatus.RELEASED,
    packageName = "Argos",
    category = PluginCategoryNames.COMMON,
    shortDescription = "Headless Ghidra",
    description = "Tool-level Argos plugin that connects to the application-level MCP server"
)
public class ArgosPlugin extends ProgramPlugin {
    private ArgosProvider provider;
    private ArgosMcpService mcpService;

    /**
     * Plugin constructor.
     * @param tool The plugin tool that this plugin is added to.
     */
    public ArgosPlugin(PluginTool tool) {
        super(tool);
        Msg.info(this, "Argos Tool Plugin initializing...");

        // Register this plugin in the service registry so components can access it
        ArgosInternalServiceRegistry.registerService(ArgosPlugin.class, this);
    }

    @Override
    public void init() {
        super.init();

        // Get the MCP service from the application plugin
        mcpService = tool.getService(ArgosMcpService.class);

        // Fallback for testing environments where ApplicationLevelPlugin isn't available
        if (mcpService == null) {
            mcpService = ArgosInternalServiceRegistry.getService(ArgosMcpService.class);
        }

        if (mcpService == null) {
            Msg.error(this, "ArgosMcpService not available - ArgosApplicationPlugin may not be loaded and no fallback service found");
            return;
        }

        // Register this tool with the MCP server
        mcpService.registerTool(tool);

        // TODO: Create the UI provider when needed
        // provider = new ArgosProvider(this, getName());
        // tool.addComponentProvider(provider, false);

        Msg.info(this, "Argos Tool Plugin initialization complete - connected to application-level MCP server");
    }

    @Override
    protected void programOpened(Program program) {
        Msg.info(this, "Program opened: " + program.getName());
        // Notify the program manager to handle cache management
        ArgosProgramManager.programOpened(program);

        // Notify the MCP service about the program opening in this tool
        if (mcpService != null) {
            mcpService.programOpened(program, tool);
        }
    }

    @Override
    protected void programClosed(Program program) {
        Msg.info(this, "Program closed: " + program.getName());
        // Notify the program manager to clear stale cache
        ArgosProgramManager.programClosed(program);

        // Notify the MCP service about the program closing in this tool
        if (mcpService != null) {
            mcpService.programClosed(program, tool);
        }
    }

    @Override
    protected void cleanup() {
        // Remove the UI provider
        if (provider != null) {
            tool.removeComponentProvider(provider);
        }

        // Unregister this tool from the MCP service
        if (mcpService != null) {
            mcpService.unregisterTool(tool);
        }

        // Only clear tool-specific services, not the application-level ones
        ArgosInternalServiceRegistry.unregisterService(ArgosPlugin.class);

        super.cleanup();
    }

    /**
     * Get all currently open programs in any Ghidra tool
     * @return List of open programs
     */
    public List<Program> getOpenPrograms() {
        return ArgosProgramManager.getOpenPrograms();
    }

    /**
     * Get the MCP service instance
     * @return The MCP service, or null if not available
     */
    public ArgosMcpService getMcpService() {
        return mcpService;
    }
}
