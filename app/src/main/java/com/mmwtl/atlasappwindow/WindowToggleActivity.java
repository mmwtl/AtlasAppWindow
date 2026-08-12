package com.mmwtl.atlasappwindow;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/** Permanent ordinary launcher entry that toggles the last selected Atlas window. */
public final class WindowToggleActivity extends Activity {
    @SuppressWarnings("deprecation")
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        toggle();
        finish();
        overridePendingTransition(0, 0);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        toggle();
        finish();
    }

    private void toggle() {
        Prefs prefs = new Prefs(this);
        BackendStatus status = OverlayService.lastStatus();
        boolean active = OverlayService.isRunning()
                && status != null
                && (status.state == BackendStatus.State.ACTIVE
                        || status.state == BackendStatus.State.LAUNCHING)
                && status.activePreset != null;
        String lastPresetId = prefs.getString(Prefs.KEY_ACTIVE_PRESET, "");
        WindowTogglePolicy.Action action = WindowTogglePolicy.decide(active, lastPresetId);
        if (action == WindowTogglePolicy.Action.CLOSE) {
            OverlayService.hide(this);
            return;
        }
        Preset preset = action == WindowTogglePolicy.Action.SHOW_LAST
                ? prefs.preset(lastPresetId) : null;
        if (preset == null) {
            Toast.makeText(this, "Сначала выберите и покажите пресет в Atlas",
                    Toast.LENGTH_LONG).show();
            return;
        }
        OverlayService.show(this, preset.id);
    }
}
