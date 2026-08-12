package com.mmwtl.atlasappwindow;

/** Pure, validated presentation settings for Atlas-owned window chrome. */
final class ChromeStyle {
    static final int MIN_HEADER_HEIGHT_DP = 44;
    static final int MAX_HEADER_HEIGHT_DP = 96;
    static final int DEFAULT_HEADER_HEIGHT_DP = 56;

    final boolean headerVisible;
    final boolean titleVisible;
    final boolean controlsVisible;
    final int headerHeightDp;

    ChromeStyle(
            boolean headerVisible,
            boolean titleVisible,
            boolean controlsVisible,
            int headerHeightDp) {
        this.headerVisible = headerVisible;
        this.titleVisible = titleVisible;
        this.controlsVisible = controlsVisible;
        this.headerHeightDp = clampHeaderHeight(headerHeightDp);
    }

    static int clampHeaderHeight(int value) {
        return Math.max(MIN_HEADER_HEIGHT_DP, Math.min(value, MAX_HEADER_HEIGHT_DP));
    }
}
