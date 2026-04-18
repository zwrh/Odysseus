/*
 * Argos - AI-powered binary analysis
 * Copyright (c) 2024-2025
 * 
 * Licensed under the Apache License, Version 2.0
 */

package argos.capabilities;

import ghidra.program.model.listing.Program;

/**
 * Interface for MCP capabilities.
 * Capabilities are responsible for registering and managing
 * MCP analysis features that enable interactive operations with Ghidra data.
 */
public interface Capability {
    /**
     * Register all capabilities with the MCP server
     */
    void addCapabilities();

    /**
     * Notify the capability that a program has been opened
     * @param program The program that was opened
     */
    void programOpened(Program program);

    /**
     * Notify the capability that a program has been closed
     * @param program The program that was closed
     */
    void programClosed(Program program);

    /**
     * Clean up any resources or state
     */
    void cleanup();
}
