package com.mmwtl.atlasappwindow;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;

abstract class ScaledActivity extends Activity {
    static final int MIN_SCALE_TENTHS = 10;
    static final int MAX_SCALE_TENTHS = 20;
    static final int DEFAULT_SCALE_TENTHS = 15;

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(scaledContext(base));
    }

    static int configuredScaleTenths(Context context) {
        return Math.max(MIN_SCALE_TENTHS, Math.min(MAX_SCALE_TENTHS,
                new Prefs(context).getInt(Prefs.KEY_UI_SCALE, DEFAULT_SCALE_TENTHS)));
    }

    private static Context scaledContext(Context base) {
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        int density = configuration.densityDpi;
        if (density == Configuration.DENSITY_DPI_UNDEFINED) {
            density = base.getResources().getDisplayMetrics().densityDpi;
        }
        configuration.densityDpi = Math.round(
                density * configuredScaleTenths(base) / 10f);
        return base.createConfigurationContext(configuration);
    }
}
