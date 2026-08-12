package com.mmwtl.atlasappwindow;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PresetLauncherActivityTest {
    @Test
    public void resolvesOnlyFiveDeclaredLauncherAliases() {
        for (int slot = 1; slot <= Preset.MAX_COUNT; slot++) {
            assertEquals(slot, PresetLauncherActivity.slotFromClassName(
                    PresetLauncherActivity.ALIAS_PREFIX + slot));
        }
        assertEquals(0, PresetLauncherActivity.slotFromClassName(null));
        assertEquals(0, PresetLauncherActivity.slotFromClassName(
                PresetLauncherActivity.ALIAS_PREFIX));
        assertEquals(0, PresetLauncherActivity.slotFromClassName(
                PresetLauncherActivity.ALIAS_PREFIX + "6"));
        assertEquals(0, PresetLauncherActivity.slotFromClassName(
                "com.example.PresetLauncher1"));
    }
}
