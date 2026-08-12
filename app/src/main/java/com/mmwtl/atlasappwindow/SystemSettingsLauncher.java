package com.mmwtl.atlasappwindow;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

/** Opens an app-specific system settings page, then falls back to its generic page. */
final class SystemSettingsLauncher {
    private SystemSettingsLauncher() {}

    static boolean open(Context context, String action, String label) {
        Intent appSpecific = new Intent(action,
                Uri.parse("package:" + context.getPackageName()));
        try {
            context.startActivity(appSpecific);
            return true;
        } catch (ActivityNotFoundException | SecurityException appSpecificError) {
            AppLog.warn("Cannot open app-specific " + label + "; trying system fallback",
                    appSpecificError);
        }

        try {
            context.startActivity(new Intent(action));
            return true;
        } catch (ActivityNotFoundException | SecurityException fallbackError) {
            AppLog.warn("Cannot open " + label, fallbackError);
            return false;
        }
    }
}
