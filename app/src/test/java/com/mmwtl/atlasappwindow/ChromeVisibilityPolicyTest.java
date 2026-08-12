package com.mmwtl.atlasappwindow;

import static org.junit.Assert.assertEquals;

import java.util.Set;
import org.junit.Test;

public class ChromeVisibilityPolicyTest {
    private static final String TARGET = "com.music";
    private static final Set<String> HOMES = Set.of("com.oem.home");

    @Test
    public void showsChromeOnTargetOrHome() {
        assertEquals(ChromeVisibilityPolicy.Decision.SHOW,
                ChromeVisibilityPolicy.decide(true, TARGET, TARGET, HOMES));
        assertEquals(ChromeVisibilityPolicy.Decision.SHOW,
                ChromeVisibilityPolicy.decide(true, "com.oem.home", TARGET, HOMES));
    }

    @Test
    public void preservesChromeWhenOemUsageStatsHasNoAnswer() {
        assertEquals(ChromeVisibilityPolicy.Decision.KEEP,
                ChromeVisibilityPolicy.decide(true, null, TARGET, HOMES));
    }

    @Test
    public void hidesChromeForAnotherAppOrLockedDevice() {
        assertEquals(ChromeVisibilityPolicy.Decision.HIDE,
                ChromeVisibilityPolicy.decide(true, "com.maps", TARGET, HOMES));
        assertEquals(ChromeVisibilityPolicy.Decision.HIDE,
                ChromeVisibilityPolicy.decide(false, TARGET, TARGET, HOMES));
    }
}
