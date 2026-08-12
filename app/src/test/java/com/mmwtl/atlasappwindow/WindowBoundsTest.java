package com.mmwtl.atlasappwindow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class WindowBoundsTest {
    @Test
    public void constructorAcceptsValidBounds() {
        WindowBounds bounds = new WindowBounds(24, 48, 1024, 600);

        assertEquals(24, bounds.getLeft());
        assertEquals(48, bounds.getTop());
        assertEquals(1024, bounds.getRight());
        assertEquals(600, bounds.getBottom());
        assertEquals(1000, bounds.width());
        assertEquals(552, bounds.height());
    }

    @Test
    public void constructorRejectsNegativeOrEmptyBounds() {
        assertThrows(IllegalArgumentException.class, () -> new WindowBounds(-1, 0, 20, 20));
        assertThrows(IllegalArgumentException.class, () -> new WindowBounds(0, 0, 0, 20));
        assertThrows(IllegalArgumentException.class, () -> new WindowBounds(0, 20, 20, 20));
    }

    @Test
    public void clampedLimitsEveryEdgeToDisplay() {
        WindowBounds bounds = WindowBounds.clamped(-50, -10, 1400, 900, 1280, 720);

        assertEquals(new WindowBounds(0, 0, 1280, 720), bounds);
    }

    @Test
    public void clampedKeepsOnePixelWhenRequestIsEntirelyOutsideDisplay() {
        WindowBounds bounds = WindowBounds.clamped(1400, 900, 1600, 1000, 1280, 720);

        assertEquals(new WindowBounds(1279, 719, 1280, 720), bounds);
    }

    @Test
    public void clampRejectsInvalidDisplaySize() {
        WindowBounds bounds = new WindowBounds(0, 0, 10, 10);

        assertThrows(IllegalArgumentException.class, () -> bounds.clampTo(0, 100));
        assertThrows(IllegalArgumentException.class, () -> bounds.clampTo(100, -1));
    }
}
