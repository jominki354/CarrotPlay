/*
 * Copyright (C) 2012 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0
 * 
 * Stub class for Hidden API access - will be replaced at runtime
 */
package android.view;

import android.os.IBinder;
import android.os.ServiceManager;

/**
 * @hide
 */
public class WindowManagerGlobal {
    
    /**
     * @hide
     */
    public static IWindowManager getWindowManagerService() {
        throw new RuntimeException("Stub!");
    }
    
    /**
     * @hide
     */
    public static IWindowManager peekWindowManagerService() {
        throw new RuntimeException("Stub!");
    }
    
    /**
     * @hide
     */
    public static WindowManagerGlobal getInstance() {
        throw new RuntimeException("Stub!");
    }
}
