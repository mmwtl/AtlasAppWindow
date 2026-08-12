package com.mmwtl.atlasappwindow;

import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;

final class DirectFreeformLauncher {
    // Hidden ActivityOptions key used by the Android 11 framework. Putting the value in the
    // options Bundle avoids reflective hidden-API calls while preserving the standard start path.
    static final String KEY_WINDOWING_MODE = "android.activity.windowingMode";
    static final int WINDOWING_MODE_FREEFORM = 5;

    private DirectFreeformLauncher() {}

    static void launch(Context context, Preset preset, WindowBounds bounds) {
        ComponentName component = ComponentName.unflattenFromString(preset.component);
        if (component == null) throw new IllegalArgumentException("Invalid component");
        Intent launch = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        ActivityOptions options = ActivityOptions.makeBasic()
                .setLaunchBounds(new Rect(bounds.left, bounds.top, bounds.right, bounds.bottom));
        Bundle bundle = options.toBundle();
        bundle.putInt(KEY_WINDOWING_MODE, WINDOWING_MODE_FREEFORM);
        context.startActivity(launch, bundle);
    }

    static void goHome(Context context) {
        Intent home = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        context.startActivity(home);
    }
}
