package com.mmwtl.atlasappwindow;

import java.util.regex.Pattern;

final class CommandValidation {
    private static final Pattern COMPONENT_PATTERN =
            Pattern.compile("[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+");
    private static final Pattern PACKAGE_PATTERN =
            Pattern.compile("[A-Za-z0-9_.$]+");
    private static final Pattern PRESET_PATTERN =
            Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");

    private CommandValidation() {
    }

    static String requireComponent(String component) {
        return requireMatching(component, COMPONENT_PATTERN, "component");
    }

    static String requirePackageName(String packageName) {
        return requireMatching(packageName, PACKAGE_PATTERN, "packageName");
    }

    static String requirePreset(String preset) {
        return requireMatching(preset, PRESET_PATTERN, "preset");
    }

    static int requirePositiveTaskId(int taskId) {
        if (taskId <= 0) {
            throw new IllegalArgumentException("taskId must be positive");
        }
        return taskId;
    }

    static String shellQuote(String validatedValue) {
        // All accepted token grammars reject a single quote, so this is a complete shell escape.
        return "'" + validatedValue + "'";
    }

    private static String requireMatching(String value, Pattern pattern, String label) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(label + " has an invalid format");
        }
        return value;
    }
}
