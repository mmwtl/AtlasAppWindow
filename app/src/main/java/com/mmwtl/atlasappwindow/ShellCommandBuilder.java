package com.mmwtl.atlasappwindow;

/** Builds the small, allow-listed set of commands used by the freeform-window controller. */
public final class ShellCommandBuilder {
    private ShellCommandBuilder() {
    }

    /** Validates a flattened component and returns it unchanged. */
    public static String requireComponent(String component) {
        return CommandValidation.requireComponent(component);
    }

    /** Preferred task discovery command on Android 11 and later. */
    public static String buildProbeCommand(String packageName) {
        String validatedPackage = CommandValidation.requirePackageName(packageName);
        return "dumpsys activity activities " + CommandValidation.shellQuote(validatedPackage);
    }

    /** Compatibility probe for vendor Android builds whose dumpsys output is incomplete. */
    public static String buildStackListProbeCommand() {
        return "am stack list";
    }

    public static String buildLaunchCommand(String component) {
        String validatedComponent = CommandValidation.requireComponent(component);
        return "am start --user 0 -W --windowingMode 5 -n "
                + CommandValidation.shellQuote(validatedComponent);
    }

    public static String buildResizeCommand(int taskId, WindowBounds bounds) {
        CommandValidation.requirePositiveTaskId(taskId);
        if (bounds == null) {
            throw new NullPointerException("bounds");
        }
        return "am task resize " + taskId
                + " " + bounds.getLeft()
                + " " + bounds.getTop()
                + " " + bounds.getRight()
                + " " + bounds.getBottom();
    }

    public static String buildRemoveCommand(int taskId) {
        CommandValidation.requirePositiveTaskId(taskId);
        return "am task remove " + taskId;
    }
}
