package com.mmwtl.atlasappwindow;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class AppRepository {
    private AppRepository() {}

    static List<AppEntry> loadLaunchable(Context context) {
        PackageManager packages = context.getPackageManager();
        Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved = packages.queryIntentActivities(probe, PackageManager.MATCH_ALL);
        Map<String, AppEntry> unique = new LinkedHashMap<>();
        for (ResolveInfo info : resolved) {
            ActivityInfo activity = info.activityInfo;
            if (activity == null || !activity.exported || !activity.enabled
                    || activity.applicationInfo == null || !activity.applicationInfo.enabled
                    || context.getPackageName().equals(activity.packageName)) continue;
            ComponentName component = new ComponentName(activity.packageName, activity.name);
            String label = safe(activity.applicationInfo.loadLabel(packages), activity.packageName);
            String activityLabel = safe(info.loadLabel(packages), activity.name);
            AppEntry entry = new AppEntry(component, label, activityLabel);
            unique.put(entry.componentKey, entry);
        }
        ArrayList<AppEntry> result = new ArrayList<>(unique.values());
        result.sort(Comparator
                .comparing((AppEntry item) -> item.label.toLowerCase(Locale.getDefault()))
                .thenComparing(item -> item.activityLabel.toLowerCase(Locale.getDefault())));
        return result;
    }

    private static String safe(CharSequence value, String fallback) {
        return value == null || value.toString().trim().isEmpty()
                ? fallback : value.toString().trim();
    }
}
