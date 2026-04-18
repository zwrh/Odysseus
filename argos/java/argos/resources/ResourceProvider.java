/*
 * Argos - AI-powered binary analysis
 * Copyright (c) 2024-2025
 * 
 * Licensed under the Apache License, Version 2.0
 */

package argos.resources;

import ghidra.program.model.listing.Program;

/**
 * Interface for MCP resource providers.
 * Resource providers are responsible for registering and managing
 * MCP resources that provide read-only access to Ghidra data.
 */
public interface ResourceProvider {
    /**
     * Register all resources with the MCP server
     */
    void register();

    /**
     * Notify the provider that a program has been opened
     * @param program The program that was opened
     */
    void programOpened(Program program);

    /**
     * Notify the provider that a program has been closed
     * @param program The program that was closed
     */
    void programClosed(Program program);

    /**
     * Clean up any resources or state
     */
    void cleanup();
}
