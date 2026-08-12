package com.mmwtl.atlasappwindow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class LaunchRequestTest {
    private static final WindowBounds BOUNDS = new WindowBounds(10, 20, 800, 600);

    @Test
    public void directRequestHasNoPreset() {
        LaunchRequest request = new LaunchRequest("com.example/.MainActivity", BOUNDS);

        assertEquals("com.example/.MainActivity", request.getComponent());
        assertNull(request.getPreset());
        assertEquals(BOUNDS, request.getBounds());
    }

    @Test
    public void resolvedPresetRetainsItsKey() {
        LaunchRequest request =
                new LaunchRequest("com.example/com.example.MapActivity", "maps", BOUNDS);

        assertEquals("maps", request.getPreset());
    }

    @Test
    public void constructorRejectsInvalidComponentAndPreset() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LaunchRequest("com.example/.MainActivity; reboot", BOUNDS));
        assertThrows(
                IllegalArgumentException.class,
                () -> new LaunchRequest("com.example/.MainActivity", "bad preset", BOUNDS));
    }
}
