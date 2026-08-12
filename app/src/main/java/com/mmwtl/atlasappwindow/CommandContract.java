package com.mmwtl.atlasappwindow;

import java.util.Map;
import java.util.Objects;

/**
 * Android-independent names and parsing rules. The exported Android activity deliberately accepts
 * only {@link #EXTRA_PRESET}; component/bounds parsing is for trusted in-process adapters.
 */
public final class CommandContract {
    public static final String ACTION_SHOW =
            "com.mmwtl.atlasappwindow.action.SHOW";
    public static final String ACTION_SWITCH =
            "com.mmwtl.atlasappwindow.action.SWITCH";
    public static final String ACTION_HIDE =
            "com.mmwtl.atlasappwindow.action.HIDE";

    public static final String EXTRA_COMPONENT =
            "com.mmwtl.atlasappwindow.extra.COMPONENT";
    public static final String EXTRA_PRESET =
            "com.mmwtl.atlasappwindow.extra.PRESET";
    public static final String EXTRA_LEFT =
            "com.mmwtl.atlasappwindow.extra.LEFT";
    public static final String EXTRA_TOP =
            "com.mmwtl.atlasappwindow.extra.TOP";
    public static final String EXTRA_RIGHT =
            "com.mmwtl.atlasappwindow.extra.RIGHT";
    public static final String EXTRA_BOTTOM =
            "com.mmwtl.atlasappwindow.extra.BOTTOM";

    private CommandContract() {
    }

    public static boolean isSupportedAction(String action) {
        return ACTION_SHOW.equals(action)
                || ACTION_SWITCH.equals(action)
                || ACTION_HIDE.equals(action);
    }

    /**
     * Parses a plain map so the contract can be tested without Android's {@code Intent} class.
     * Bounds may be supplied as integral numeric values or base-10 strings.
     */
    public static LaunchRequest parse(
            Map<String, ?> extras,
            Map<String, String> presets,
            WindowBounds defaultBounds) {
        Objects.requireNonNull(extras, "extras");
        Objects.requireNonNull(presets, "presets");
        Objects.requireNonNull(defaultBounds, "defaultBounds");

        String explicitComponent = optionalString(extras.get(EXTRA_COMPONENT), EXTRA_COMPONENT);
        String preset = optionalString(extras.get(EXTRA_PRESET), EXTRA_PRESET);

        if (explicitComponent != null && preset != null) {
            throw new IllegalArgumentException("component and preset are mutually exclusive");
        }

        final String resolvedComponent;
        if (preset != null) {
            preset = CommandValidation.requirePreset(preset);
            resolvedComponent = presets.get(preset);
            if (resolvedComponent == null) {
                throw new IllegalArgumentException("unknown preset: " + preset);
            }
        } else if (explicitComponent != null) {
            resolvedComponent = explicitComponent;
        } else {
            throw new IllegalArgumentException("component or preset is required");
        }

        WindowBounds bounds = parseBounds(extras, defaultBounds);
        return new LaunchRequest(resolvedComponent, preset, bounds);
    }

    private static WindowBounds parseBounds(Map<String, ?> extras, WindowBounds defaultBounds) {
        boolean hasLeft = extras.containsKey(EXTRA_LEFT);
        boolean hasTop = extras.containsKey(EXTRA_TOP);
        boolean hasRight = extras.containsKey(EXTRA_RIGHT);
        boolean hasBottom = extras.containsKey(EXTRA_BOTTOM);
        int suppliedCount = (hasLeft ? 1 : 0)
                + (hasTop ? 1 : 0)
                + (hasRight ? 1 : 0)
                + (hasBottom ? 1 : 0);

        if (suppliedCount == 0) {
            return defaultBounds;
        }
        if (suppliedCount != 4) {
            throw new IllegalArgumentException("all four bounds values must be supplied together");
        }

        return new WindowBounds(
                parseInteger(extras.get(EXTRA_LEFT), EXTRA_LEFT),
                parseInteger(extras.get(EXTRA_TOP), EXTRA_TOP),
                parseInteger(extras.get(EXTRA_RIGHT), EXTRA_RIGHT),
                parseInteger(extras.get(EXTRA_BOTTOM), EXTRA_BOTTOM));
    }

    private static String optionalString(Object value, String label) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof String)) {
            throw new IllegalArgumentException(label + " must be a string");
        }
        String normalized = ((String) value).trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static int parseInteger(Object value, String label) {
        if (value instanceof Byte || value instanceof Short || value instanceof Integer) {
            return ((Number) value).intValue();
        }
        if (value instanceof Long) {
            long longValue = (Long) value;
            if (longValue < Integer.MIN_VALUE || longValue > Integer.MAX_VALUE) {
                throw new IllegalArgumentException(label + " is outside the integer range");
            }
            return (int) longValue;
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if (!text.matches("-?[0-9]+")) {
                throw new IllegalArgumentException(label + " must be an integer");
            }
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(label + " is outside the integer range", exception);
            }
        }
        throw new IllegalArgumentException(label + " must be an integer");
    }
}
