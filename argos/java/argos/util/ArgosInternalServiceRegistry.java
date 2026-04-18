/*
 * Argos - AI-powered binary analysis
 * Copyright (c) 2024-2025
 * 
 * Licensed under the Apache License, Version 2.0
 */

package argos.util;

import java.util.HashMap;
import java.util.Map;

/**
 * A simple service registry to allow components to locate each other at runtime.
 * This is a static registry that provides global access to core services.
 */
public class ArgosInternalServiceRegistry {
    private static final Map<Class<?>, Object> services = new HashMap<>();

    /**
     * Register a service implementation
     * @param <T> The service type
     * @param serviceClass The service interface class
     * @param implementation The service implementation
     */
    public static <T> void registerService(Class<T> serviceClass, T implementation) {
        services.put(serviceClass, implementation);
    }

    /**
     * Get a registered service
     * @param <T> The service type
     * @param serviceClass The service interface class
     * @return The service implementation or null if not found
     */
    @SuppressWarnings("unchecked")
    public static <T> T getService(Class<T> serviceClass) {
        return (T) services.get(serviceClass);
    }

    /**
     * Remove a service from the registry
     * @param <T> The service type
     * @param serviceClass The service interface class
     */
    public static <T> void unregisterService(Class<T> serviceClass) {
        services.remove(serviceClass);
    }

    /**
     * Clear all registered services
     */
    public static void clearAllServices() {
        services.clear();
    }
}
