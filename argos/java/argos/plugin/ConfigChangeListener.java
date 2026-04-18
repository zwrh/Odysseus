/*
 * Argos - AI-powered binary analysis
 * Copyright (c) 2024-2025
 * 
 * Licensed under the Apache License, Version 2.0
 */

package argos.plugin;

/**
 * Interface for listening to configuration changes in the Argos plugin.
 * Implementations can register with Settings to receive notifications
 * when configuration values change.
 */
public interface ConfigChangeListener {
    
    /**
     * Called when a configuration option has changed.
     * 
     * @param category The category of the option that changed
     * @param name The name of the option that changed
     * @param oldValue The previous value of the option
     * @param newValue The new value of the option
     */
    void onConfigChanged(String category, String name, Object oldValue, Object newValue);
}
