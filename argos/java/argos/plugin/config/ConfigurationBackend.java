/*
 * Argos - AI-powered binary analysis
 * Copyright (c) 2024-2025
 * 
 * Licensed under the Apache License, Version 2.0
 */

package argos.plugin.config;

/**
 * Backend interface for configuration storage.
 * This abstraction allows Settings to work with different storage
 * mechanisms: ToolOptions (GUI mode), files (headless mode), or in-memory (testing).
 */
public interface ConfigurationBackend {

    /**
     * Get an integer configuration value
     * @param category The configuration category
     * @param name The configuration name
     * @param defaultValue The default value if not found
     * @return The configured value or default
     */
    int getInt(String category, String name, int defaultValue);

    /**
     * Set an integer configuration value
     * @param category The configuration category
     * @param name The configuration name
     * @param value The value to set
     */
    void setInt(String category, String name, int value);

    /**
     * Get a string configuration value
     * @param category The configuration category
     * @param name The configuration name
     * @param defaultValue The default value if not found
     * @return The configured value or default
     */
    String getString(String category, String name, String defaultValue);

    /**
     * Set a string configuration value
     * @param category The configuration category
     * @param name The configuration name
     * @param value The value to set
     */
    void setString(String category, String name, String value);

    /**
     * Get a boolean configuration value
     * @param category The configuration category
     * @param name The configuration name
     * @param defaultValue The default value if not found
     * @return The configured value or default
     */
    boolean getBoolean(String category, String name, boolean defaultValue);

    /**
     * Set a boolean configuration value
     * @param category The configuration category
     * @param name The configuration name
     * @param value The value to set
     */
    void setBoolean(String category, String name, boolean value);

    /**
     * Check if this backend supports change notifications
     * @return True if this backend can notify listeners of changes
     */
    boolean supportsChangeNotifications();

    /**
     * Register a listener for configuration changes (if supported)
     * @param listener The listener to register
     */
    void addChangeListener(ConfigurationBackendListener listener);

    /**
     * Unregister a configuration change listener
     * @param listener The listener to remove
     */
    void removeChangeListener(ConfigurationBackendListener listener);

    /**
     * Clean up resources when done with this backend
     */
    void dispose();
}
