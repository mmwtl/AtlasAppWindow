package com.mmwtl.atlasappwindow;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Owns launcher shortcuts without leaking Android shortcut details into settings UI. */
final class ShortcutPublisher {
    private static final String ID_PREFIX = "preset-";
    private ShortcutPublisher() {}

    static void sync(Context context, List<Preset> presets) {
        ShortcutManager manager = context.getSystemService(ShortcutManager.class);
        if (manager == null) return;
        try {
            int limit = Math.max(0, manager.getMaxShortcutCountPerActivity());
            ArrayList<ShortcutInfo> shortcuts = new ArrayList<>();
            for (int index = 0; index < presets.size() && shortcuts.size() < limit; index++) {
                shortcuts.add(build(context, presets.get(index), shortcuts.size()));
            }
            if (shortcuts.isEmpty()) manager.removeAllDynamicShortcuts();
            else manager.setDynamicShortcuts(shortcuts);
        } catch (IllegalArgumentException | IllegalStateException | SecurityException error) {
            AppLog.warn("Cannot synchronize launcher shortcuts", error);
        }
    }

    static boolean requestPinned(Context context, Preset preset) {
        ShortcutManager manager = context.getSystemService(ShortcutManager.class);
        if (manager == null || !manager.isRequestPinShortcutSupported()) return false;
        try {
            manager.enableShortcuts(Collections.singletonList(id(preset)));
        } catch (IllegalArgumentException ignored) {
            // A new shortcut has nothing to re-enable; requestPinShortcut can still create it.
        } catch (IllegalStateException | SecurityException error) {
            AppLog.warn("Cannot pin preset shortcut", error);
            return false;
        }
        try {
            return manager.requestPinShortcut(build(context, preset, 0), null);
        } catch (IllegalArgumentException | IllegalStateException | SecurityException error) {
            AppLog.warn("Cannot pin preset shortcut", error);
            return false;
        }
    }

    static void presetDeleted(Context context, Preset preset) {
        ShortcutManager manager = context.getSystemService(ShortcutManager.class);
        if (manager == null) return;
        try {
            manager.removeDynamicShortcuts(Collections.singletonList(id(preset)));
            manager.disableShortcuts(Collections.singletonList(id(preset)), "Пресет удалён");
        } catch (IllegalArgumentException | IllegalStateException | SecurityException error) {
            AppLog.warn("Cannot disable deleted preset shortcut", error);
        }
    }

    private static ShortcutInfo build(Context context, Preset preset, int rank) {
        Intent command = new Intent(CommandContract.ACTION_SHOW)
                .setClass(context, CommandActivity.class)
                .putExtra(CommandContract.EXTRA_PRESET, preset.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return new ShortcutInfo.Builder(context, id(preset))
                .setShortLabel(shortLabel(preset.label))
                .setLongLabel(longLabel(preset.label))
                .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
                .setIntent(command)
                .setRank(rank)
                .build();
    }

    private static String id(Preset preset) {
        return ID_PREFIX + preset.id;
    }

    private static String shortLabel(String label) {
        String value = label == null || label.isBlank() ? "Atlas App Window" : label.trim();
        return value.length() <= 32 ? value : value.substring(0, 32);
    }

    private static String longLabel(String label) {
        String value = "Открыть " + shortLabel(label) + " в Atlas App Window";
        return value.length() <= 64 ? value : value.substring(0, 64);
    }
}
