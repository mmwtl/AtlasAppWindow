package com.mmwtl.atlasappwindow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class ShellCommandBuilderTest {
    @Test
    public void buildsAndroid11FreeformCommands() {
        assertEquals(
                "dumpsys activity activities 'com.example'",
                ShellCommandBuilder.buildProbeCommand("com.example"));
        assertEquals("am stack list", ShellCommandBuilder.buildStackListProbeCommand());
        assertEquals(
                "am start --user 0 -W --windowingMode 5 -n 'com.example/.MainActivity'",
                ShellCommandBuilder.buildLaunchCommand("com.example/.MainActivity"));
        assertEquals(
                "am task resize 42 20 40 1000 650",
                ShellCommandBuilder.buildResizeCommand(
                        42, new WindowBounds(20, 40, 1000, 650)));
        assertEquals("am task remove 42", ShellCommandBuilder.buildRemoveCommand(42));
    }

    @Test
    public void quotesDollarSignsAllowedInAndroidClassNames() {
        assertEquals(
                "am start --user 0 -W --windowingMode 5 -n 'com.example/com.example.Main$Inner'",
                ShellCommandBuilder.buildLaunchCommand(
                        "com.example/com.example.Main$Inner"));
    }

    @Test
    public void rejectsShellInjectionAndMalformedTargets() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ShellCommandBuilder.buildLaunchCommand(
                        "com.example/.MainActivity;reboot"));
        assertThrows(
                IllegalArgumentException.class,
                () -> ShellCommandBuilder.buildLaunchCommand("com.example/.Main Activity"));
        assertThrows(
                IllegalArgumentException.class,
                () -> ShellCommandBuilder.buildProbeCommand("com.example && reboot"));
        assertThrows(
                IllegalArgumentException.class,
                () -> ShellCommandBuilder.buildProbeCommand("-help"));
    }

    @Test
    public void rejectsNonPositiveTaskIds() {
        WindowBounds bounds = new WindowBounds(0, 0, 100, 100);

        assertThrows(
                IllegalArgumentException.class,
                () -> ShellCommandBuilder.buildResizeCommand(0, bounds));
        assertThrows(
                IllegalArgumentException.class,
                () -> ShellCommandBuilder.buildRemoveCommand(-1));
    }
}
