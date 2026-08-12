package com.mmwtl.atlasappwindow;

import java.util.List;

/** Pure slot selection shared by preferences and covered without Android dependencies. */
final class PresetSlots {
    private PresetSlots() {}

    static int firstFree(List<Preset> presets) {
        boolean[] occupied = new boolean[Preset.MAX_COUNT + 1];
        for (Preset preset : presets) {
            if (preset.slot >= 1 && preset.slot <= Preset.MAX_COUNT) occupied[preset.slot] = true;
        }
        for (int slot = 1; slot <= Preset.MAX_COUNT; slot++) {
            if (!occupied[slot]) return slot;
        }
        return 0;
    }
}
