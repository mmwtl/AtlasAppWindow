package com.mmwtl.atlasappwindow;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class PresetSlotsTest {
    @Test
    public void fillsFirstGapWithoutShiftingExistingSlots() {
        List<Preset> presets = List.of(
                preset("one", 1), preset("three", 3), preset("five", 5));

        assertEquals(2, PresetSlots.firstFree(presets));
        assertEquals(1, presets.get(0).slot);
        assertEquals(3, presets.get(1).slot);
        assertEquals(5, presets.get(2).slot);
    }

    @Test
    public void reportsFullFiveSlotCapacity() {
        ArrayList<Preset> presets = new ArrayList<>();
        for (int slot = 1; slot <= Preset.MAX_COUNT; slot++) {
            presets.add(preset("preset-" + slot, slot));
        }

        assertEquals(0, PresetSlots.firstFree(presets));
    }

    private static Preset preset(String id, int slot) {
        return new Preset(id, "com.example/.MainActivity", id, slot);
    }
}
