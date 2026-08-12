package com.mmwtl.atlasappwindow;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

public final class CommandActivity extends Activity {
    @SuppressWarnings("deprecation")
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        dispatch(getIntent());
        finish();
        overridePendingTransition(0, 0);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        dispatch(intent);
        finish();
    }

    private void dispatch(Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (CommandContract.ACTION_HIDE.equals(action)) {
            OverlayService.hide(this);
            return;
        }
        if (!CommandContract.ACTION_SHOW.equals(action)
                && !CommandContract.ACTION_SWITCH.equals(action)) {
            Toast.makeText(this, "Неизвестная команда Atlas App Window", Toast.LENGTH_SHORT).show();
            return;
        }
        String presetId = intent.getStringExtra(CommandContract.EXTRA_PRESET);
        try {
            CommandValidation.requirePreset(presetId);
        } catch (IllegalArgumentException error) {
            Toast.makeText(this, "Некорректный preset id", Toast.LENGTH_SHORT).show();
            return;
        }
        if (new Prefs(this).preset(presetId) == null) {
            Toast.makeText(this, "Пресет не найден: " + presetId, Toast.LENGTH_SHORT).show();
            return;
        }
        if (CommandContract.ACTION_SWITCH.equals(action)) {
            OverlayService.switchTo(this, presetId);
        } else {
            OverlayService.show(this, presetId);
        }
    }
}
