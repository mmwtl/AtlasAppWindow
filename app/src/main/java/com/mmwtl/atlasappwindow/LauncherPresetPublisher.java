package com.mmwtl.atlasappwindow;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import java.util.List;

/** Enables only launcher aliases whose stable preset slots are occupied. */
final class LauncherPresetPublisher {
    private LauncherPresetPublisher() {}

    static void sync(Context context, List<Preset> presets) {
        boolean[] occupied = new boolean[Preset.MAX_COUNT + 1];
        for (Preset preset : presets) {
            if (preset.slot >= 1 && preset.slot <= Preset.MAX_COUNT) occupied[preset.slot] = true;
        }
        PackageManager packages = context.getPackageManager();
        for (int slot = 1; slot <= Preset.MAX_COUNT; slot++) {
            ComponentName alias = new ComponentName(
                    context.getPackageName(), PresetLauncherActivity.ALIAS_PREFIX + slot);
            int state = occupied[slot]
                    ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
            try {
                int current = packages.getComponentEnabledSetting(alias);
                boolean currentlyEnabled = current
                        == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
                if (currentlyEnabled == occupied[slot]) continue;
                packages.setComponentEnabledSetting(
                        alias, state, PackageManager.DONT_KILL_APP);
            } catch (IllegalArgumentException | SecurityException error) {
                AppLog.warn("Cannot update launcher preset " + slot, error);
            }
        }
    }
}
