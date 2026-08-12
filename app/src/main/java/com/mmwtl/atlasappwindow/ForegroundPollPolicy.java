package com.mmwtl.atlasappwindow;

final class ForegroundPollPolicy {
    static final long INITIAL_QUERY_WINDOW_MS = 5L * 60L * 1_000L;
    static final long INCREMENTAL_QUERY_WINDOW_MS = 60_000L;
    static final long QUERY_OVERLAP_MS = 2_000L;
    static final long VISIBLE_DELAY_MS = 750L;
    static final long HIDDEN_DELAY_MS = 1_500L;

    private ForegroundPollPolicy() {}

    static long queryBegin(long now, long lastQuery) {
        if (lastQuery <= 0 || lastQuery > now) {
            return Math.max(0, now - INITIAL_QUERY_WINDOW_MS);
        }
        return Math.max(0,
                Math.max(now - INCREMENTAL_QUERY_WINDOW_MS, lastQuery - QUERY_OVERLAP_MS));
    }
}
