package com.mmwtl.atlasappwindow;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public final class OverlayService extends Service
        implements WindowBackend.Listener, ChromeController.Listener {
    static final String ACTION_START = "com.mmwtl.atlasappwindow.internal.START";
    static final String ACTION_STOP = "com.mmwtl.atlasappwindow.internal.STOP";
    static final String ACTION_PROBE = "com.mmwtl.atlasappwindow.internal.PROBE";
    static final String ACTION_RESIZE = "com.mmwtl.atlasappwindow.internal.RESIZE";
    static final String ACTION_UPDATE_CHROME =
            "com.mmwtl.atlasappwindow.internal.UPDATE_CHROME";
    static final String ACTION_STATUS_CHANGED =
            "com.mmwtl.atlasappwindow.internal.STATUS_CHANGED";

    private static final String CHANNEL_ID = "atlas_app_window_service";
    private static final int NOTIFICATION_ID = 2811;
    private static volatile boolean running;
    private static volatile BackendStatus lastStatus = new BackendStatus(
            BackendStatus.State.IDLE, "Сервис не запущен", null, Prefs.NO_TASK);

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService foregroundWorker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "atlas-app-window-visibility");
        thread.setDaemon(true);
        return thread;
    });
    private Prefs prefs;
    private WindowBackend backend;
    private ChromeController chrome;
    private ForegroundAppDetector foregroundDetector;
    private Preset activePreset;
    private boolean foregroundCheckInFlight;
    private boolean destroyed;
    private boolean stopAfterHide;

    private final Runnable foregroundPoll = new Runnable() {
        @Override public void run() {
            if (destroyed || activePreset == null) {
                if (chrome != null) chrome.setVisible(false);
                return;
            }
            boolean permissions = Settings.canDrawOverlays(OverlayService.this)
                    && ForegroundAppDetector.hasUsageAccess(OverlayService.this);
            if (!permissions) {
                chrome.setVisible(false);
                main.postDelayed(this, ForegroundPollPolicy.HIDDEN_DELAY_MS);
                return;
            }
            if (foregroundCheckInFlight) return;
            foregroundCheckInFlight = true;
            String packageName = activePreset.component.substring(
                    0, activePreset.component.indexOf('/'));
            try {
                foregroundWorker.execute(() -> {
                    ChromeVisibilityPolicy.Decision decision =
                            foregroundDetector.chromeVisibility(packageName);
                    main.post(() -> {
                        foregroundCheckInFlight = false;
                        if (destroyed || activePreset == null) return;
                        if (decision == ChromeVisibilityPolicy.Decision.SHOW) {
                            chrome.setVisible(true);
                        } else if (decision == ChromeVisibilityPolicy.Decision.HIDE) {
                            chrome.setVisible(false);
                        }
                        main.postDelayed(foregroundPoll,
                                decision == ChromeVisibilityPolicy.Decision.SHOW
                                ? ForegroundPollPolicy.VISIBLE_DELAY_MS
                                : ForegroundPollPolicy.HIDDEN_DELAY_MS);
                    });
                });
            } catch (RejectedExecutionException ignored) {
                foregroundCheckInFlight = false;
            }
        }
    };

    public static void start(Context context) {
        startWith(context, ACTION_START, null);
    }

    public static void show(Context context, String presetId) {
        startWith(context, CommandContract.ACTION_SHOW, presetId);
    }

    public static void switchTo(Context context, String presetId) {
        startWith(context, CommandContract.ACTION_SWITCH, presetId);
    }

    public static void hide(Context context) {
        startWith(context, CommandContract.ACTION_HIDE, null);
    }

    public static void stop(Context context) {
        startWith(context, ACTION_STOP, null);
    }

    public static void probe(Context context) {
        startWith(context, ACTION_PROBE, null);
    }

    public static void resize(Context context) {
        startWith(context, ACTION_RESIZE, null);
    }

    public static void updateChrome(Context context) {
        if (running) startWith(context, ACTION_UPDATE_CHROME, null);
    }

    public static boolean isRunning() { return running; }
    public static BackendStatus lastStatus() { return lastStatus; }

    private static void startWith(Context context, String action, String presetId) {
        Intent intent = new Intent(context, OverlayService.class).setAction(action);
        if (presetId != null) intent.putExtra(CommandContract.EXTRA_PRESET, presetId);
        context.startForegroundService(intent);
    }

    @Override public void onCreate() {
        super.onCreate();
        prefs = new Prefs(this);
        foregroundDetector = new ForegroundAppDetector(this);
        chrome = new ChromeController(this, this);
        backend = new FreeformBackend(this, prefs, this);
        createNotificationChannel();
        Notification notification = buildNotification(lastStatus);
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        running = true;
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopAfterHide = true;
            hideChromeImmediately();
            backend.hide();
            return START_NOT_STICKY;
        }
        if (CommandContract.ACTION_HIDE.equals(action)) {
            stopAfterHide = true;
            hideChromeImmediately();
            backend.hide();
            return START_NOT_STICKY;
        }
        if (ACTION_PROBE.equals(action)) {
            backend.probe();
            return START_STICKY;
        }
        if (ACTION_RESIZE.equals(action)) {
            backend.resize(clampedBounds());
            return START_STICKY;
        }
        if (ACTION_UPDATE_CHROME.equals(action)) {
            if (activePreset != null) {
                chrome.setWindow(activePreset, clampedBounds(), prefs.chromeStyle());
            }
            return START_STICKY;
        }
        if (CommandContract.ACTION_SHOW.equals(action)
                || CommandContract.ACTION_SWITCH.equals(action)) {
            String presetId = intent.getStringExtra(CommandContract.EXTRA_PRESET);
            Preset preset = prefs.preset(presetId);
            if (preset == null) {
                onBackendStatus(new BackendStatus(BackendStatus.State.ERROR,
                        "Пресет не найден: " + presetId, null, Prefs.NO_TASK));
            } else {
                prefs.putBoolean(Prefs.KEY_SERVICE_ENABLED, true);
                stopAfterHide = false;
                backend.show(preset, clampedBounds());
            }
            return START_STICKY;
        }
        if (ACTION_START.equals(action)) {
            String activeId = prefs.getString(Prefs.KEY_ACTIVE_PRESET, "");
            Preset preset = prefs.preset(activeId);
            if (preset != null && prefs.getBoolean(Prefs.KEY_SERVICE_ENABLED, false)) {
                backend.show(preset, clampedBounds());
            } else {
                backend.probe();
            }
        }
        return START_STICKY;
    }

    @Override public void onDestroy() {
        destroyed = true;
        main.removeCallbacksAndMessages(null);
        foregroundWorker.shutdownNow();
        if (chrome != null) chrome.hide();
        if (backend != null) backend.close();
        stopForeground(STOP_FOREGROUND_REMOVE);
        running = false;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onBackendStatus(BackendStatus status) {
        lastStatus = status;
        if (status.state == BackendStatus.State.LAUNCHING && status.activePreset != null) {
            // Draw against the requested bounds immediately. ADB verification may take seconds,
            // and the OEM freeform task must not appear as an unstyled resizable rectangle first.
            chrome.setWindow(status.activePreset, clampedBounds(), prefs.chromeStyle());
            chrome.setVisible(true);
        } else if (status.state == BackendStatus.State.ACTIVE && status.activePreset != null) {
            activePreset = status.activePreset;
            chrome.setWindow(activePreset, clampedBounds(), prefs.chromeStyle());
            // Start visible. Some OEM launchers do not publish HOME through UsageStats; an
            // unknown answer must preserve this state instead of removing the header.
            chrome.setVisible(true);
            main.removeCallbacks(foregroundPoll);
            main.post(foregroundPoll);
        } else if (status.state == BackendStatus.State.ERROR) {
            if (activePreset == null) {
                chrome.hide();
            } else {
                chrome.setWindow(activePreset, clampedBounds(), prefs.chromeStyle());
                chrome.setVisible(true);
            }
        } else if (status.state == BackendStatus.State.IDLE) {
            activePreset = null;
            chrome.hide();
            main.removeCallbacks(foregroundPoll);
            if (stopAfterHide) {
                prefs.putBoolean(Prefs.KEY_SERVICE_ENABLED, false);
                stopSelf();
            }
        }
        getSystemService(NotificationManager.class).notify(
                NOTIFICATION_ID, buildNotification(status));
        sendBroadcast(new Intent(ACTION_STATUS_CHANGED).setPackage(getPackageName()));
    }

    @Override public void onSwitchRelative(int delta) {
        List<Preset> presets = prefs.presets();
        if (presets.isEmpty()) return;
        int current = 0;
        if (activePreset != null) {
            for (int index = 0; index < presets.size(); index++) {
                if (presets.get(index).id.equals(activePreset.id)) {
                    current = index;
                    break;
                }
            }
        }
        int next = Math.floorMod(current + delta, presets.size());
        backend.show(presets.get(next), clampedBounds());
    }

    @Override public void onCloseRequested() {
        stopAfterHide = true;
        hideChromeImmediately();
        backend.hide();
    }

    @Override public void onMoveRequested(WindowBounds bounds) {
        prefs.putBounds(bounds);
        backend.resize(bounds);
    }

    private WindowBounds clampedBounds() {
        android.graphics.Rect screen = getSystemService(android.view.WindowManager.class)
                .getMaximumWindowMetrics().getBounds();
        return prefs.bounds().clampTo(screen.width(), screen.height());
    }

    private void hideChromeImmediately() {
        activePreset = null;
        main.removeCallbacks(foregroundPoll);
        if (chrome != null) chrome.hide();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                getString(R.string.overlay_notification_channel),
                NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification buildNotification(BackendStatus status) {
        PendingIntent settings = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent hide = PendingIntent.getService(this, 1,
                new Intent(this, OverlayService.class).setAction(CommandContract.ACTION_HIDE),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.overlay_notification_title))
                .setContentText(status.detail)
                .setContentIntent(settings)
                .setOnlyAlertOnce(true)
                .setOngoing(status.state == BackendStatus.State.ACTIVE
                        || status.state == BackendStatus.State.LAUNCHING)
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(new Notification.Action.Builder(null, "Закрыть окно", hide).build())
                .build();
    }
}
