package com.mmwtl.atlasappwindow;

import java.util.Set;

/** Pure decision policy that distinguishes an absent UsageStats answer from a real app switch. */
final class ChromeVisibilityPolicy {
    enum Decision { SHOW, HIDE, KEEP }

    private ChromeVisibilityPolicy() {}

    static Decision decide(
            boolean deviceReady,
            String foregroundPackage,
            boolean transientAtlasActivity,
            String targetPackage,
            Set<String> homePackages) {
        if (!deviceReady) return Decision.HIDE;
        if (foregroundPackage == null || foregroundPackage.isBlank()) return Decision.KEEP;
        if (transientAtlasActivity) return Decision.KEEP;
        if (foregroundPackage.equals(targetPackage)
                || homePackages.contains(foregroundPackage)) {
            return Decision.SHOW;
        }
        return Decision.HIDE;
    }
}
