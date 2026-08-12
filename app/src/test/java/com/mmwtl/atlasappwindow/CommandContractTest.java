package com.mmwtl.atlasappwindow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class CommandContractTest {
    private static final WindowBounds DEFAULT_BOUNDS = new WindowBounds(40, 30, 1000, 650);

    @Test
    public void recognizesOnlyPublicActions() {
        assertTrue(CommandContract.isSupportedAction(CommandContract.ACTION_SHOW));
        assertTrue(CommandContract.isSupportedAction(CommandContract.ACTION_SWITCH));
        assertTrue(CommandContract.isSupportedAction(CommandContract.ACTION_HIDE));
        assertFalse(CommandContract.isSupportedAction("com.example.UNKNOWN"));
        assertFalse(CommandContract.isSupportedAction(null));
    }

    @Test
    public void parsesDirectComponentWithDefaultBounds() {
        Map<String, Object> extras = new HashMap<>();
        extras.put(CommandContract.EXTRA_COMPONENT, " com.example/.MainActivity ");

        LaunchRequest request = CommandContract.parse(extras, new HashMap<>(), DEFAULT_BOUNDS);

        assertEquals("com.example/.MainActivity", request.getComponent());
        assertEquals(DEFAULT_BOUNDS, request.getBounds());
    }

    @Test
    public void resolvesPresetAndParsesStringBounds() {
        Map<String, Object> extras = new HashMap<>();
        extras.put(CommandContract.EXTRA_PRESET, "maps");
        extras.put(CommandContract.EXTRA_LEFT, "12");
        extras.put(CommandContract.EXTRA_TOP, 24);
        extras.put(CommandContract.EXTRA_RIGHT, 900L);
        extras.put(CommandContract.EXTRA_BOTTOM, "600");
        Map<String, String> presets = new HashMap<>();
        presets.put("maps", "com.maps/.MapActivity");

        LaunchRequest request = CommandContract.parse(extras, presets, DEFAULT_BOUNDS);

        assertEquals("com.maps/.MapActivity", request.getComponent());
        assertEquals("maps", request.getPreset());
        assertEquals(new WindowBounds(12, 24, 900, 600), request.getBounds());
    }

    @Test
    public void rejectsAmbiguousOrUnknownTargets() {
        Map<String, Object> ambiguous = new HashMap<>();
        ambiguous.put(CommandContract.EXTRA_COMPONENT, "com.example/.MainActivity");
        ambiguous.put(CommandContract.EXTRA_PRESET, "maps");

        assertThrows(
                IllegalArgumentException.class,
                () -> CommandContract.parse(ambiguous, new HashMap<>(), DEFAULT_BOUNDS));

        Map<String, Object> unknownPreset = new HashMap<>();
        unknownPreset.put(CommandContract.EXTRA_PRESET, "missing");
        assertThrows(
                IllegalArgumentException.class,
                () -> CommandContract.parse(unknownPreset, new HashMap<>(), DEFAULT_BOUNDS));
    }

    @Test
    public void rejectsPresetIdsOutsidePublicAllowlist() {
        Map<String, Object> uppercase = new HashMap<>();
        uppercase.put(CommandContract.EXTRA_PRESET, "Maps");
        assertThrows(
                IllegalArgumentException.class,
                () -> CommandContract.parse(uppercase, new HashMap<>(), DEFAULT_BOUNDS));

        Map<String, Object> tooLong = new HashMap<>();
        tooLong.put(CommandContract.EXTRA_PRESET, "a".repeat(65));
        assertThrows(
                IllegalArgumentException.class,
                () -> CommandContract.parse(tooLong, new HashMap<>(), DEFAULT_BOUNDS));
    }

    @Test
    public void rejectsPartialOrNonIntegralBounds() {
        Map<String, Object> partial = new HashMap<>();
        partial.put(CommandContract.EXTRA_COMPONENT, "com.example/.MainActivity");
        partial.put(CommandContract.EXTRA_LEFT, 10);
        assertThrows(
                IllegalArgumentException.class,
                () -> CommandContract.parse(partial, new HashMap<>(), DEFAULT_BOUNDS));

        Map<String, Object> decimal = new HashMap<>();
        decimal.put(CommandContract.EXTRA_COMPONENT, "com.example/.MainActivity");
        decimal.put(CommandContract.EXTRA_LEFT, 10);
        decimal.put(CommandContract.EXTRA_TOP, 10);
        decimal.put(CommandContract.EXTRA_RIGHT, 100.5);
        decimal.put(CommandContract.EXTRA_BOTTOM, 100);
        assertThrows(
                IllegalArgumentException.class,
                () -> CommandContract.parse(decimal, new HashMap<>(), DEFAULT_BOUNDS));
    }
}
