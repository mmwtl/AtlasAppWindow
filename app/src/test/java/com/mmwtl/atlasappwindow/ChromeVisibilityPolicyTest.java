package com.mmwtl.atlasappwindow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Set;
import org.junit.Test;

public class ChromeVisibilityPolicyTest {
    private static final String TARGET = "com.music";
    private static final Set<String> HOMES = Set.of("com.oem.home");

    @Test
    public void showsChromeOnTargetOrHome() {
        assertEquals(ChromeVisibilityPolicy.Decision.SHOW,
                ChromeVisibilityPolicy.decide(true, TARGET, false, TARGET, HOMES));
        assertEquals(ChromeVisibilityPolicy.Decision.SHOW,
                ChromeVisibilityPolicy.decide(true, "com.oem.home", false, TARGET, HOMES));
    }

    @Test
    public void preservesChromeWhenOemUsageStatsHasNoAnswer() {
        assertEquals(ChromeVisibilityPolicy.Decision.KEEP,
                ChromeVisibilityPolicy.decide(true, null, false, TARGET, HOMES));
        assertEquals(ChromeVisibilityPolicy.Decision.KEEP,
                ChromeVisibilityPolicy.decide(true, "com.mmwtl.atlasappwindow", true,
                        TARGET, HOMES));
    }

    @Test
    public void hidesChromeForAnotherAppOrLockedDevice() {
        assertEquals(ChromeVisibilityPolicy.Decision.HIDE,
                ChromeVisibilityPolicy.decide(true, "com.maps", false, TARGET, HOMES));
        assertEquals(ChromeVisibilityPolicy.Decision.HIDE,
                ChromeVisibilityPolicy.decide(false, TARGET, false, TARGET, HOMES));
    }

    @Test
    public void recognizesOnlyTransientAtlasEntryActivities() {
        assertTrue(ForegroundAppDetector.isTransientLauncherClass(
                WindowToggleActivity.class.getName()));
        assertTrue(ForegroundAppDetector.isTransientLauncherClass(
                PresetLauncherActivity.class.getName()));
        assertTrue(ForegroundAppDetector.isTransientLauncherClass(
                CommandActivity.class.getName()));
        assertFalse(ForegroundAppDetector.isTransientLauncherClass(
                MainActivity.class.getName()));
        assertFalse(ForegroundAppDetector.isTransientLauncherClass(null));
    }
}
