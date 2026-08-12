package com.mmwtl.atlasappwindow;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.UserManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class Prefs {
    private static final String NAME = "atlas_app_window_settings";
    private static final Object MIGRATION_LOCK = new Object();
    private static volatile boolean migrationAttempted;

    static final String KEY_AUTO_START = "auto_start";
    static final String KEY_SERVICE_ENABLED = "service_enabled";
    static final String KEY_UI_SCALE = "ui_scale_tenths";
    static final String KEY_ADB_PORT = "adb_port";
    static final String KEY_LEFT = "window_left";
    static final String KEY_TOP = "window_top";
    static final String KEY_RIGHT = "window_right";
    static final String KEY_BOTTOM = "window_bottom";
    static final String KEY_ACTIVE_PRESET = "active_preset";
    static final String KEY_PRESETS = "presets_json";
    static final String KEY_ACTIVE_TASK_ID = "active_task_id";
    static final String KEY_HEADER_VISIBLE = "header_visible";
    static final String KEY_TITLE_VISIBLE = "header_title_visible";
    static final String KEY_CONTROLS_VISIBLE = "header_controls_visible";
    static final String KEY_HEADER_HEIGHT = "header_height_dp";
    static final int DEFAULT_ADB_PORT = 5555;
    static final int NO_TASK = -1;
    static final WindowBounds DEFAULT_BOUNDS = new WindowBounds(24, 600, 696, 1450);

    private final SharedPreferences values;

    Prefs(Context context) {
        Context app = context.getApplicationContext();
        Context storage = app.createDeviceProtectedStorageContext();
        migrateWhenAvailable(app, storage);
        values = storage.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    private static void migrateWhenAvailable(Context credential, Context device) {
        if (migrationAttempted) return;
        UserManager users = credential.getSystemService(UserManager.class);
        if (users != null && !users.isUserUnlocked()) return;
        synchronized (MIGRATION_LOCK) {
            if (migrationAttempted) return;
            try {
                device.moveSharedPreferencesFrom(credential, NAME);
            } catch (RuntimeException error) {
                AppLog.warn("Cannot migrate preferences to Direct Boot storage", error);
            }
            migrationAttempted = true;
        }
    }

    boolean getBoolean(String key, boolean fallback) {
        return values.getBoolean(key, fallback);
    }

    void putBoolean(String key, boolean value) {
        values.edit().putBoolean(key, value).apply();
    }

    int getInt(String key, int fallback) {
        return values.getInt(key, fallback);
    }

    void putInt(String key, int value) {
        values.edit().putInt(key, value).apply();
    }

    String getString(String key, String fallback) {
        return values.getString(key, fallback);
    }

    void putString(String key, String value) {
        values.edit().putString(key, value).apply();
    }

    WindowBounds bounds() {
        try {
            return new WindowBounds(
                    getInt(KEY_LEFT, DEFAULT_BOUNDS.left),
                    getInt(KEY_TOP, DEFAULT_BOUNDS.top),
                    getInt(KEY_RIGHT, DEFAULT_BOUNDS.right),
                    getInt(KEY_BOTTOM, DEFAULT_BOUNDS.bottom));
        } catch (IllegalArgumentException ignored) {
            return DEFAULT_BOUNDS;
        }
    }

    void putBounds(WindowBounds bounds) {
        values.edit()
                .putInt(KEY_LEFT, bounds.left)
                .putInt(KEY_TOP, bounds.top)
                .putInt(KEY_RIGHT, bounds.right)
                .putInt(KEY_BOTTOM, bounds.bottom)
                .apply();
    }

    ChromeStyle chromeStyle() {
        return new ChromeStyle(
                getBoolean(KEY_HEADER_VISIBLE, true),
                getBoolean(KEY_TITLE_VISIBLE, true),
                getBoolean(KEY_CONTROLS_VISIBLE, true),
                getInt(KEY_HEADER_HEIGHT, ChromeStyle.DEFAULT_HEADER_HEIGHT_DP));
    }

    synchronized List<Preset> presets() {
        ArrayList<Preset> result = new ArrayList<>();
        String raw = getString(KEY_PRESETS, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int index = 0; index < array.length(); index++) {
                JSONObject item = array.optJSONObject(index);
                if (item == null) continue;
                try {
                    Preset preset = new Preset(
                            item.optString("id", ""), item.optString("component", ""),
                            item.optString("label", ""));
                    if (find(result, preset.id) == null) result.add(preset);
                } catch (IllegalArgumentException ignored) {
                    // Ignore a corrupt entry without discarding the rest of the settings.
                }
            }
        } catch (JSONException error) {
            AppLog.warnRateLimited("presets-json", "Preset settings are corrupt", error);
        }
        return result;
    }

    synchronized Preset preset(String id) {
        return find(presets(), id);
    }

    synchronized void savePreset(Preset preset) {
        List<Preset> current = presets();
        current.removeIf(item -> item.id.equals(preset.id));
        current.add(preset);
        writePresets(current);
    }

    synchronized void deletePreset(String id) {
        List<Preset> current = presets();
        if (current.removeIf(item -> item.id.equals(id))) writePresets(current);
        if (id.equals(getString(KEY_ACTIVE_PRESET, ""))) putString(KEY_ACTIVE_PRESET, "");
    }

    private void writePresets(List<Preset> presets) {
        JSONArray array = new JSONArray();
        for (Preset preset : presets) {
            JSONObject item = new JSONObject();
            try {
                item.put("id", preset.id);
                item.put("component", preset.component);
                item.put("label", preset.label);
                array.put(item);
            } catch (JSONException impossible) {
                throw new IllegalStateException(impossible);
            }
        }
        putString(KEY_PRESETS, array.toString());
    }

    private static Preset find(List<Preset> presets, String id) {
        if (id == null) return null;
        for (Preset preset : presets) if (id.equals(preset.id)) return preset;
        return null;
    }
}
