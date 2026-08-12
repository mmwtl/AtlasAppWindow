package com.mmwtl.atlasappwindow;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        Prefs prefs = new Prefs(context);
        String action = intent.getAction();
        boolean boot = Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_USER_UNLOCKED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action);
        boolean replace = Intent.ACTION_MY_PACKAGE_REPLACED.equals(action);
        if (!boot && !replace) return;
        if (boot && !prefs.getBoolean(Prefs.KEY_AUTO_START, false)) return;
        if (replace && !prefs.getBoolean(Prefs.KEY_SERVICE_ENABLED, false)
                && !prefs.getBoolean(Prefs.KEY_AUTO_START, false)) return;
        if (!Settings.canDrawOverlays(context)
                || !ForegroundAppDetector.hasUsageAccess(context)) {
            AppLog.info("Boot start skipped: overlay or usage permission is missing");
            return;
        }
        try {
            OverlayService.start(context);
        } catch (RuntimeException error) {
            AppLog.warn("Boot start failed", error);
        }
    }
}
