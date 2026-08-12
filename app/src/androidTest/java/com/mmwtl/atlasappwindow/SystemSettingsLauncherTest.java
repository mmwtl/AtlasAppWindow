package com.mmwtl.atlasappwindow;

import android.content.ActivityNotFoundException;
import android.content.ContextWrapper;
import android.content.Intent;

import junit.framework.TestCase;

public final class SystemSettingsLauncherTest extends TestCase {
    private static final String ACTION = "com.mmwtl.test.SETTINGS";
    private static final String PACKAGE_NAME = "com.mmwtl.test";

    public void testPrimaryIntentTargetsCurrentPackage() {
        RecordingContext context = new RecordingContext((RuntimeException) null);

        assertTrue(SystemSettingsLauncher.open(context, ACTION, "test settings"));
        assertEquals(1, context.calls);
        assertEquals(ACTION, context.lastIntent.getAction());
        assertEquals("package:" + PACKAGE_NAME, context.lastIntent.getDataString());
    }

    public void testActivityNotFoundFallsBackWithoutPackageUri() {
        RecordingContext context = new RecordingContext(new ActivityNotFoundException());

        assertTrue(SystemSettingsLauncher.open(context, ACTION, "test settings"));
        assertEquals(2, context.calls);
        assertEquals(ACTION, context.lastIntent.getAction());
        assertNull(context.lastIntent.getData());
    }

    public void testSecurityExceptionFallsBackWithoutPackageUri() {
        RecordingContext context = new RecordingContext(new SecurityException("blocked"));

        assertTrue(SystemSettingsLauncher.open(context, ACTION, "test settings"));
        assertEquals(2, context.calls);
        assertEquals(ACTION, context.lastIntent.getAction());
        assertNull(context.lastIntent.getData());
    }

    public void testUnavailableFallbackIsReported() {
        RecordingContext context = new RecordingContext(
                new ActivityNotFoundException(), new SecurityException("blocked"));

        assertFalse(SystemSettingsLauncher.open(context, ACTION, "test settings"));
        assertEquals(2, context.calls);
    }

    private static final class RecordingContext extends ContextWrapper {
        private final RuntimeException[] failures;
        private int calls;
        private Intent lastIntent;

        RecordingContext(RuntimeException... failures) {
            super(null);
            this.failures = failures;
        }

        @Override public String getPackageName() {
            return PACKAGE_NAME;
        }

        @Override public void startActivity(Intent intent) {
            lastIntent = intent;
            int index = calls++;
            if (index < failures.length && failures[index] != null) throw failures[index];
        }
    }
}
