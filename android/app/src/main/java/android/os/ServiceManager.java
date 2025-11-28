/*
 * Copyright (C) 2007 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0
 * 
 * Stub class for Hidden API access - will be replaced at runtime
 */
package android.os;

/**
 * @hide
 */
public final class ServiceManager {
    
    /**
     * Returns a reference to a service with the given name.
     * @hide
     */
    public static IBinder getService(String name) {
        throw new RuntimeException("Stub!");
    }
    
    /**
     * Returns a reference to a service with the given name, or throws
     * NullPointerException if none is found.
     * @hide
     */
    public static IBinder getServiceOrThrow(String name) {
        throw new RuntimeException("Stub!");
    }
    
    /**
     * Place a new service named @a name into the service manager.
     * @hide
     */
    public static void addService(String name, IBinder service) {
        throw new RuntimeException("Stub!");
    }
    
    /**
     * Retrieve an existing service called @a name from the service manager.
     * @hide
     */
    public static IBinder checkService(String name) {
        throw new RuntimeException("Stub!");
    }
    
    /**
     * Return a list of all currently running services.
     * @hide
     */
    public static String[] listServices() {
        throw new RuntimeException("Stub!");
    }
    
    /**
     * This is only intended to be called when the process is first being brought
     * up and bound by the activity manager.
     * @hide
     */
    public static void initServiceCache(java.util.Map<String, IBinder> cache) {
        throw new RuntimeException("Stub!");
    }
}
