package com.mmwtl.atlasappwindow;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/** Transparent target for the five ordinary launcher entries backed by stable preset slots. */
public final class PresetLauncherActivity extends Activity {
    static final String ALIAS_PREFIX =
            "com.mmwtl.atlasappwindow.PresetLauncher";

    @SuppressWarnings("deprecation")
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        launch(getIntent());
        finish();
        overridePendingTransition(0, 0);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        launch(intent);
        finish();
    }

    private void launch(Intent intent) {
        int slot = slotFromClassName(intent == null || intent.getComponent() == null
                ? null : intent.getComponent().getClassName());
        Preset preset = new Prefs(this).presetAtSlot(slot);
        if (preset == null) {
            Toast.makeText(this, "Пресет " + slot + " не настроен", Toast.LENGTH_SHORT).show();
            LauncherPresetPublisher.sync(this, new Prefs(this).presets());
            return;
        }
        OverlayService.show(this, preset.id);
    }

    static int slotFromClassName(String className) {
        if (className == null || !className.startsWith(ALIAS_PREFIX)) return 0;
        String suffix = className.substring(ALIAS_PREFIX.length());
        if (suffix.length() != 1 || suffix.charAt(0) < '1' || suffix.charAt(0) > '5') return 0;
        return suffix.charAt(0) - '0';
    }
}
