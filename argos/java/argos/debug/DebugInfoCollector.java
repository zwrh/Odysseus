/*
 * Argos - AI-powered binary analysis
 * Copyright (c) 2024-2025
 * 
 * Licensed under the Apache License, Version 2.0
 */

package argos.debug;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ghidra.framework.Application;
import ghidra.program.model.listing.Program;
import ghidra.util.extensions.ExtensionDetails;
import ghidra.util.extensions.ExtensionUtils;

import argos.plugin.Settings;
import argos.plugin.ArgosProgramManager;
import argos.core.ArgosServer;
import argos.capabilities.Capability;
import argos.util.ArgosInternalServiceRegistry;

/**
 * Collects debug information from the system, Ghidra, and Argos for troubleshooting.
 * All collected information is returned as Maps for easy JSON serialization.
 */
public class DebugInfoCollector {

    /**
     * Collect all debug information into a single map.
     * @param userMessage Optional user-provided message describing the issue
     * @return Map containing all collected debug information
     */
    public Map<String, Object> collectAll(String userMessage) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("captureTimestamp", Instant.now().toString());
        info.put("userMessage", userMessage != null ? userMessage : "(No message provided)");
        info.put("system", collectSystemInfo());
        info.put("ghidra", collectGhidraInfo());
        info.put("argos", collectArgosInfo());
        info.put("mcpServer", collectMcpServerInfo());
        info.put("programs", collectOpenPrograms());
        return info;
    }

    /**
     * Collect system information (Java, OS).
     */
    public Map<String, Object> collectSystemInfo() {
        Map<String, Object> system = new LinkedHashMap<>();
        system.put("javaVersion", System.getProperty("java.version"));
        system.put("javaVendor", System.getProperty("java.vendor"));
        system.put("osName", System.getProperty("os.name"));
        system.put("osVersion", System.getProperty("os.version"));
        system.put("osArch", System.getProperty("os.arch"));
        return system;
    }

    /**
     * Collect Ghidra information (version, extensions).
     */
    public Map<String, Object> collectGhidraInfo() {
        Map<String, Object> ghidra = new LinkedHashMap<>();

        try {
            ghidra.put("version", Application.getApplicationVersion());
        } catch (Exception e) {
            ghidra.put("version", "Error: " + e.getMessage());
        }

        // Collect installed extensions
        List<Map<String, Object>> extensions = new ArrayList<>();
        try {
            Set<ExtensionDetails> installedExtensions = ExtensionUtils.getInstalledExtensions();
            for (ExtensionDetails ext : installedExtensions) {
                Map<String, Object> extInfo = new LinkedHashMap<>();
                extInfo.put("name", ext.getName());
                extInfo.put("version", ext.getVersion());
                extInfo.put("author", ext.getAuthor());
                extInfo.put("description", ext.getDescription());
                extensions.add(extInfo);
            }
        } catch (Exception e) {
            Map<String, Object> errorInfo = new LinkedHashMap<>();
            errorInfo.put("error", "Failed to get extensions: " + e.getMessage());
            extensions.add(errorInfo);
        }
        ghidra.put("extensions", extensions);

        return ghidra;
    }

    /**
     * Collect Argos configuration and status.
     */
    public Map<String, Object> collectArgosInfo() {
        Map<String, Object> argos = new LinkedHashMap<>();
        argos.put("version", getArgosVersion());

        // Get configuration
        Settings config = ArgosInternalServiceRegistry.getService(Settings.class);
        if (config != null) {
            Map<String, Object> configInfo = new LinkedHashMap<>();
            configInfo.put("serverEnabled", config.isServerEnabled());
            configInfo.put("serverPort", config.getServerPort());
            configInfo.put("serverHost", config.getServerHost());
            configInfo.put("debugMode", config.isDebugMode());
            configInfo.put("apiKeyEnabled", config.isApiKeyEnabled());
            configInfo.put("decompilerTimeoutSeconds", config.getDecompilerTimeoutSeconds());
            configInfo.put("maxDecompilerSearchFunctions", config.getMaxDecompilerSearchFunctions());
            argos.put("config", configInfo);
        } else {
            argos.put("config", "Settings not available");
        }

        return argos;
    }

    /**
     * Collect MCP server status and registered tools.
     */
    public Map<String, Object> collectMcpServerInfo() {
        Map<String, Object> mcpServer = new LinkedHashMap<>();

        ArgosServer argosServer = ArgosInternalServiceRegistry.getService(ArgosServer.class);
        if (argosServer != null) {
            mcpServer.put("running", argosServer.isServerRunning());
            mcpServer.put("port", argosServer.getServerPort());
            mcpServer.put("host", argosServer.getServerHost());
            mcpServer.put("headlessMode", argosServer.isHeadlessMode());

            // Get registered tool provider names
            List<String> toolProviderNames = new ArrayList<>();
            List<Capability> capabilityProviders = argosServer.getCapabilities();
            if (capabilityProviders != null) {
                for (Capability provider : capabilityProviders) {
                    toolProviderNames.add(provider.getClass().getSimpleName());
                }
            }
            mcpServer.put("capabilityProviders", toolProviderNames);

            // Get registered PluginTools count
            mcpServer.put("registeredCapabilitiesCount", argosServer.getRegisteredToolsCount());
        } else {
            mcpServer.put("error", "ArgosServer not available");
        }

        return mcpServer;
    }

    /**
     * Collect information about open programs.
     */
    public List<Map<String, Object>> collectOpenPrograms() {
        List<Map<String, Object>> programs = new ArrayList<>();

        try {
            for (Program program : ArgosProgramManager.getOpenPrograms()) {
                Map<String, Object> progInfo = new LinkedHashMap<>();
                progInfo.put("path", program.getDomainFile().getPathname());
                progInfo.put("name", program.getName());
                progInfo.put("language", program.getLanguage().getLanguageID().getIdAsString());
                progInfo.put("compilerSpec", program.getCompilerSpec().getCompilerSpecID().getIdAsString());
                progInfo.put("functionCount", program.getFunctionManager().getFunctionCount());
                progInfo.put("symbolCount", program.getSymbolTable().getNumSymbols());
                programs.add(progInfo);
            }
        } catch (Exception e) {
            Map<String, Object> errorInfo = new LinkedHashMap<>();
            errorInfo.put("error", "Failed to get programs: " + e.getMessage());
            programs.add(errorInfo);
        }

        return programs;
    }

    /**
     * Get the Argos extension version from the installed extension metadata.
     * Falls back to "dev" if the extension is not found (e.g., running from source).
     */
    private String getArgosVersion() {
        try {
            Set<ExtensionDetails> installedExtensions = ExtensionUtils.getInstalledExtensions();
            for (ExtensionDetails ext : installedExtensions) {
                if ("Argos".equals(ext.getName())) {
                    String version = ext.getVersion();
                    // Return version if available and not the placeholder
                    if (version != null && !version.isEmpty() && !version.contains("@")) {
                        return version;
                    }
                }
            }
        } catch (Exception e) {
            // Fall through to default
        }
        return "dev";
    }
}
