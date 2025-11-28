/*
 * Copyright (C) 2012 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0
 * 
 * Stub class for Hidden API access - will be replaced at runtime
 */
package android.hardware.input;

import android.content.Context;
import android.view.InputEvent;

/**
 * @hide
 */
public final class InputManager {
    
    /**
     * Input Event Injection Synchronization Mode: None.
     * Never blocks. Injection is asynchronous and is assumed always to be successful.
     * @hide
     */
    public static final int INJECT_INPUT_EVENT_MODE_ASYNC = 0;
    
    /**
     * Input Event Injection Synchronization Mode: Wait for result.
     * Waits for previous events to be dispatched so that the input dispatcher can determine
     * whether input event injection will be permitted based on the current input focus.
     * @hide
     */
    public static final int INJECT_INPUT_EVENT_MODE_WAIT_FOR_RESULT = 1;
    
    /**
     * Input Event Injection Synchronization Mode: Wait for finish.
     * Waits for the event to be delivered to the application and handled.
     * @hide
     */
    public static final int INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH = 2;
    
    /**
     * Gets the input manager instance.
     * @hide
     */
    public static InputManager getInstance() {
        throw new RuntimeException("Stub!");
    }
    
    /**
     * Injects an input event into the event system on behalf of an application.
     * @hide
     */
    public boolean injectInputEvent(InputEvent event, int mode) {
        throw new RuntimeException("Stub!");
    }
    
    /**
     * Gets information about all supported input devices.
     * @hide
     */
    public int[] getInputDeviceIds() {
        throw new RuntimeException("Stub!");
    }
}
