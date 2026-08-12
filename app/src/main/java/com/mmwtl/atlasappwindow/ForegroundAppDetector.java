package com.mmwtl.atlasappwindow;

import android.app.AppOpsManager;
import android.app.KeyguardManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.PowerManager;
import android.os.Process;
import android.os.UserManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ForegroundAppDetector {
    private final Context context;
    private final UsageStatsManager usage;
    private final PowerManager power;
    private final KeyguardManager keyguard;
    private final UserManager users;
    private final ForegroundEventTracker tracker = new ForegroundEventTracker();
    private final Set<String> homePackages = new HashSet<>();
    private long lastQuery;
    private long lastHomeRefresh;

    ForegroundAppDetector(Context context) {
        this.context = context.getApplicationContext();
        usage = context.getSystemService(UsageStatsManager.class);
        power = context.getSystemService(PowerManager.class);
        keyguard = context.getSystemService(KeyguardManager.class);
        users = context.getSystemService(UserManager.class);
    }

    static boolean hasUsageAccess(Context context) {
        AppOpsManager ops = context.getSystemService(AppOpsManager.class);
        if (ops == null) return false;
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), context.getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }

    boolean isDeviceReady() {
        return power != null && power.isInteractive()
                && (keyguard == null || !keyguard.isKeyguardLocked())
                && (users == null || users.isUserUnlocked());
    }

    boolean shouldShowChrome(String targetPackage) {
        if (!isDeviceReady()) return false;
        String foreground = currentForegroundPackage();
        if (foreground == null) return false;
        refreshHomesIfNeeded();
        return foreground.equals(targetPackage) || homePackages.contains(foreground);
    }

    String currentForegroundPackage() {
        if (usage == null || !hasUsageAccess(context)) return null;
        long now = System.currentTimeMillis();
        long begin = ForegroundPollPolicy.queryBegin(now, lastQuery);
        try {
            UsageEvents events = usage.queryEvents(begin, now);
            UsageEvents.Event event = new UsageEvents.Event();
            while (events != null && events.hasNextEvent()) {
                events.getNextEvent(event);
                if (event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) {
                    tracker.onResumed(event.getTimeStamp(), event.getPackageName());
                } else if (event.getEventType() == UsageEvents.Event.ACTIVITY_PAUSED
                        || event.getEventType() == UsageEvents.Event.ACTIVITY_STOPPED) {
                    tracker.onStopped(event.getTimeStamp(), event.getPackageName());
                }
            }
            lastQuery = now;
            if (!tracker.observed()) {
                List<UsageStats> stats = usage.queryUsageStats(
                        UsageStatsManager.INTERVAL_DAILY, now - 86_400_000L, now);
                if (stats != null) for (UsageStats item : stats) {
                    tracker.seed(item.getLastTimeUsed(), item.getPackageName());
                }
            }
            return tracker.foregroundPackage();
        } catch (RuntimeException error) {
            AppLog.warnRateLimited("foreground-query", "Cannot query foreground app", error);
            return null;
        }
    }

    private void refreshHomesIfNeeded() {
        long now = System.currentTimeMillis();
        if (!homePackages.isEmpty() && now - lastHomeRefresh < 30_000L) return;
        homePackages.clear();
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        try {
            for (ResolveInfo info : context.getPackageManager().queryIntentActivities(
                    home, PackageManager.MATCH_ALL)) {
                if (info.activityInfo != null) homePackages.add(info.activityInfo.packageName);
            }
        } catch (RuntimeException error) {
            AppLog.warnRateLimited("home-query", "Cannot query HOME packages", error);
        }
        lastHomeRefresh = now;
    }
}
