package com.mmwtl.atlasappwindow;

/** Pure toggle decision so a stale process flag cannot select an arbitrary preset. */
final class WindowTogglePolicy {
    enum Action { CLOSE, SHOW_LAST, NO_PRESET }

    private WindowTogglePolicy() {}

    static Action decide(boolean active, String lastPresetId) {
        if (active) return Action.CLOSE;
        return lastPresetId == null || lastPresetId.isBlank()
                ? Action.NO_PRESET : Action.SHOW_LAST;
    }
}
