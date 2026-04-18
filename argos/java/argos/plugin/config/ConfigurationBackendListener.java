/*
 * Argos - AI-powered binary analysis
 * Copyright (c) 2024-2025
 * 
 * Licensed under the Apache License, Version 2.0
 */

package argos.plugin.config;

/**
 * Listener interface for configuration backend changes.
 * Backends that support change notifications will call this when values change.
 */
public interface ConfigurationBackendListener {

    /**
     * Called when a configuration value changes
     * @param category The configuration category
     * @param name The configuration name
     * @param oldValue The previous value
     * @param newValue The new value
     */
    void onConfigurationChanged(String category, String name, Object oldValue, Object newValue);
}
