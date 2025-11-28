/*
 * Copyright (C) 2006 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0
 * 
 * Simplified AIDL for Hidden API access
 */
package android.view;

import android.view.IWindowSession;

/** @hide */
interface IWindowManager {
    /**
     * Synchronize input transactions
     * API 30+ 에서는 boolean 파라미터가 있음
     */
    void syncInputTransactions(boolean waitForAnimations);
}
