package com.mmwtl.atlasappwindow;

import java.util.Objects;

/** Immutable screen-space bounds with an exclusive right and bottom edge. */
public final class WindowBounds {
    public final int left;
    public final int top;
    public final int right;
    public final int bottom;

    public WindowBounds(int left, int top, int right, int bottom) {
        validateCoordinates(left, top, right, bottom);
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    /**
     * Clamps arbitrary, ordered bounds to a display while always retaining at least one pixel.
     */
    public static WindowBounds clamped(
            int left,
            int top,
            int right,
            int bottom,
            int displayWidth,
            int displayHeight) {
        if (right <= left) {
            throw new IllegalArgumentException("right must be greater than left");
        }
        if (bottom <= top) {
            throw new IllegalArgumentException("bottom must be greater than top");
        }
        requirePositiveDisplaySize(displayWidth, displayHeight);

        int clampedLeft = clamp(left, 0, displayWidth - 1);
        int clampedTop = clamp(top, 0, displayHeight - 1);
        int clampedRight = clamp(right, clampedLeft + 1, displayWidth);
        int clampedBottom = clamp(bottom, clampedTop + 1, displayHeight);
        return new WindowBounds(clampedLeft, clampedTop, clampedRight, clampedBottom);
    }

    public WindowBounds clampTo(int displayWidth, int displayHeight) {
        return clamped(left, top, right, bottom, displayWidth, displayHeight);
    }

    public int getLeft() {
        return left;
    }

    public int getTop() {
        return top;
    }

    public int getRight() {
        return right;
    }

    public int getBottom() {
        return bottom;
    }

    public int width() {
        return right - left;
    }

    public int height() {
        return bottom - top;
    }

    private static void validateCoordinates(int left, int top, int right, int bottom) {
        if (left < 0 || top < 0 || right < 0 || bottom < 0) {
            throw new IllegalArgumentException("bounds must be non-negative");
        }
        if (right <= left) {
            throw new IllegalArgumentException("right must be greater than left");
        }
        if (bottom <= top) {
            throw new IllegalArgumentException("bottom must be greater than top");
        }
    }

    private static void requirePositiveDisplaySize(int displayWidth, int displayHeight) {
        if (displayWidth <= 0 || displayHeight <= 0) {
            throw new IllegalArgumentException("display dimensions must be positive");
        }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WindowBounds)) {
            return false;
        }
        WindowBounds that = (WindowBounds) other;
        return left == that.left
                && top == that.top
                && right == that.right
                && bottom == that.bottom;
    }

    @Override
    public int hashCode() {
        return Objects.hash(left, top, right, bottom);
    }

    @Override
    public String toString() {
        return "WindowBounds{"
                + "left=" + left
                + ", top=" + top
                + ", right=" + right
                + ", bottom=" + bottom
                + '}';
    }
}
