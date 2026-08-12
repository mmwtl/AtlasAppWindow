package com.mmwtl.atlasappwindow;

import java.util.Objects;

/** A fully resolved request that is safe to pass to the shell command layer. */
public final class LaunchRequest {
    private final String component;
    private final String preset;
    private final WindowBounds bounds;

    public LaunchRequest(String component, String preset, WindowBounds bounds) {
        this.component = CommandValidation.requireComponent(component);
        this.preset = preset == null ? null : CommandValidation.requirePreset(preset);
        this.bounds = Objects.requireNonNull(bounds, "bounds");
    }

    public LaunchRequest(String component, WindowBounds bounds) {
        this(component, null, bounds);
    }

    public String getComponent() {
        return component;
    }

    /** Returns the preset key used to resolve this request, or {@code null} for a direct target. */
    public String getPreset() {
        return preset;
    }

    public WindowBounds getBounds() {
        return bounds;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LaunchRequest)) {
            return false;
        }
        LaunchRequest that = (LaunchRequest) other;
        return component.equals(that.component)
                && Objects.equals(preset, that.preset)
                && bounds.equals(that.bounds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(component, preset, bounds);
    }

    @Override
    public String toString() {
        return "LaunchRequest{"
                + "component='" + component + '\''
                + ", preset=" + (preset == null ? "null" : "'" + preset + '\'')
                + ", bounds=" + bounds
                + '}';
    }
}
