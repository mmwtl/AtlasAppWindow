package com.mmwtl.atlasappwindow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ChromeStyleTest {
    @Test
    public void clampsHeaderHeightToTouchSafeRange() {
        assertEquals(ChromeStyle.MIN_HEADER_HEIGHT_DP,
                new ChromeStyle(true, true, true, 1).headerHeightDp);
        assertEquals(ChromeStyle.MAX_HEADER_HEIGHT_DP,
                new ChromeStyle(true, true, true, 500).headerHeightDp);
    }

    @Test
    public void keepsChromeElementsIndependent() {
        ChromeStyle style = new ChromeStyle(false, true, false, 60);

        assertFalse(style.headerVisible);
        assertTrue(style.titleVisible);
        assertFalse(style.controlsVisible);
        assertEquals(60, style.headerHeightDp);
    }
}
