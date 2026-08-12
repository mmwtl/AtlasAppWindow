package com.mmwtl.atlasappwindow;

final class ForegroundEventTracker {
    private long lastTimestamp;
    private String foregroundPackage;
    private String foregroundClassName;
    private boolean observed;

    void onResumed(long timestamp, String packageName, String className) {
        if (timestamp < lastTimestamp || packageName == null) return;
        lastTimestamp = timestamp;
        foregroundPackage = packageName;
        foregroundClassName = className;
        observed = true;
    }

    void onStopped(long timestamp, String packageName) {
        if (timestamp < lastTimestamp || packageName == null) return;
        lastTimestamp = timestamp;
        if (packageName.equals(foregroundPackage)) {
            foregroundPackage = null;
            foregroundClassName = null;
        }
        observed = true;
    }

    void seed(long timestamp, String packageName) {
        if (!observed && timestamp >= lastTimestamp && packageName != null) {
            lastTimestamp = timestamp;
            foregroundPackage = packageName;
            foregroundClassName = null;
        }
    }

    boolean observed() { return observed; }
    String foregroundPackage() { return foregroundPackage; }
    String foregroundClassName() { return foregroundClassName; }
}
