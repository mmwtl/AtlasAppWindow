package com.mmwtl.atlasappwindow;

import android.content.ComponentName;

import java.util.Locale;

final class AppEntry {
    final ComponentName component;
    final String componentKey;
    final String label;
    final String activityLabel;
    final String searchText;

    AppEntry(ComponentName component, String label, String activityLabel) {
        this.component = component;
        componentKey = component.flattenToString();
        this.label = label;
        this.activityLabel = activityLabel;
        searchText = (label + " " + activityLabel + " " + componentKey)
                .toLowerCase(Locale.getDefault());
    }
}
