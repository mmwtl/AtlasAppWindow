package com.mmwtl.atlasappwindow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PresetTest {
    @Test
    public void generatesStableAllowlistedId() {
        String first = Preset.idFor("Яндекс Карты", "ru.yandex.yandexnavi/.MainActivity");
        String second = Preset.idFor("Яндекс Карты", "ru.yandex.yandexnavi/.MainActivity");

        assertEquals(first, second);
        assertTrue(first.matches("[a-z0-9][a-z0-9_-]{0,63}"));
    }

    @Test
    public void validatesStoredPresetAndNormalizesLabel() {
        Preset preset = new Preset(
                "maps-1", "com.example/.MainActivity", "  Карты  ");

        assertEquals("maps-1", preset.id);
        assertEquals("com.example/.MainActivity", preset.component);
        assertEquals("Карты", preset.label);
        assertThrows(IllegalArgumentException.class,
                () -> new Preset("Maps", "com.example/.MainActivity", "Карты"));
    }
}
