package com.mmwtl.atlasappwindow;

import android.os.SystemClock;
import android.util.Log;

import java.util.concurrent.ConcurrentHashMap;

final class AppLog {
    private static final String TAG = "AtlasAppWindow";
    private static final long RATE_LIMIT_MS = 60_000L;
    private static final ConcurrentHashMap<String, Long> LAST_WARNING = new ConcurrentHashMap<>();

    private AppLog() {}

    static void info(String message) {
        Log.i(TAG, message);
    }

    static void warn(String message, Throwable error) {
        Log.w(TAG, message, error);
    }

    static void warnRateLimited(String key, String message, Throwable error) {
        long now = SystemClock.elapsedRealtime();
        Long previous = LAST_WARNING.get(key);
        if (previous != null && now - previous < RATE_LIMIT_MS) return;
        LAST_WARNING.put(key, now);
        Log.w(TAG, message, error);
    }
}
