package com.example.otpfrauddetection;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

public class MyAccessibilityService extends AccessibilityService {
    public static String currentForegroundPackage = null;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (event.getPackageName() != null) {
                currentForegroundPackage = event.getPackageName().toString();
            }
        }
    }

    @Override
    public void onInterrupt() { }
}