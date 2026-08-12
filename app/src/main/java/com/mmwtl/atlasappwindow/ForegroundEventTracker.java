package com.mmwtl.atlasappwindow;

final class ForegroundEventTracker {
    private long lastTimestamp;
    private String foregroundPackage;
    private boolean observed;

    void onResumed(long timestamp, String packageName) {
        if (timestamp < lastTimestamp || packageName == null) return;
        lastTimestamp = timestamp;
        foregroundPackage = packageName;
        observed = true;
    }

    void onStopped(long timestamp, String packageName) {
        if (timestamp < lastTimestamp || packageName == null) return;
        lastTimestamp = timestamp;
        if (packageName.equals(foregroundPackage)) foregroundPackage = null;
        observed = true;
    }

    void seed(long timestamp, String packageName) {
        if (!observed && timestamp >= lastTimestamp && packageName != null) {
            lastTimestamp = timestamp;
            foregroundPackage = packageName;
        }
    }

    boolean observed() { return observed; }
    String foregroundPackage() { return foregroundPackage; }
}
