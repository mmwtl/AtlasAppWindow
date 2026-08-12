package com.mmwtl.atlasappwindow;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WindowTogglePolicyTest {
    @Test
    public void closesActiveWindowBeforeConsideringLastPreset() {
        assertEquals(WindowTogglePolicy.Action.CLOSE,
                WindowTogglePolicy.decide(true, "maps"));
        assertEquals(WindowTogglePolicy.Action.CLOSE,
                WindowTogglePolicy.decide(true, ""));
    }

    @Test
    public void showsOnlyAStoredLastPreset() {
        assertEquals(WindowTogglePolicy.Action.SHOW_LAST,
                WindowTogglePolicy.decide(false, "maps"));
        assertEquals(WindowTogglePolicy.Action.NO_PRESET,
                WindowTogglePolicy.decide(false, ""));
        assertEquals(WindowTogglePolicy.Action.NO_PRESET,
                WindowTogglePolicy.decide(false, null));
    }
}
