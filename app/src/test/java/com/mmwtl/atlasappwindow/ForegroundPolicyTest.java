package com.mmwtl.atlasappwindow;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ForegroundPolicyTest {
    @Test
    public void queryWindowStartsBroadThenUsesOverlap() {
        long now = 1_000_000L;
        assertEquals(now - ForegroundPollPolicy.INITIAL_QUERY_WINDOW_MS,
                ForegroundPollPolicy.queryBegin(now, 0));
        assertEquals(978_000L,
                ForegroundPollPolicy.queryBegin(now, 980_000L));
    }

    @Test
    public void trackerIgnoresStaleEventsAndStopsOnlyCurrentPackage() {
        ForegroundEventTracker tracker = new ForegroundEventTracker();
        tracker.onResumed(100, "com.maps");
        tracker.onResumed(90, "com.stale");
        tracker.onStopped(110, "com.other");
        assertEquals("com.maps", tracker.foregroundPackage());

        tracker.onStopped(120, "com.maps");
        assertEquals(null, tracker.foregroundPackage());
    }
}
