package com.mmwtl.atlasappwindow;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

final class ChromeController {
    interface Listener {
        void onSwitchRelative(int delta);
        void onCloseRequested();
        void onMoveRequested(WindowBounds bounds);
    }

    private final Context context;
    private final WindowManager windows;
    private final Listener listener;
    private final List<View> attached = new ArrayList<>();
    private Preset preset;
    private WindowBounds bounds;
    private boolean desiredVisible;
    private float dragStartX;
    private float dragStartY;
    private WindowBounds dragStartBounds;

    ChromeController(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
        windows = context.getSystemService(WindowManager.class);
    }

    void setWindow(Preset preset, WindowBounds bounds) {
        this.preset = preset;
        this.bounds = bounds;
        rebuild();
    }

    void setVisible(boolean visible) {
        if (desiredVisible == visible) return;
        desiredVisible = visible;
        rebuild();
    }

    void hide() {
        desiredVisible = false;
        removeAll();
    }

    private void rebuild() {
        removeAll();
        if (!desiredVisible || preset == null || bounds == null) return;
        int stroke = Math.max(2, Ui.dp(context, 2));
        addBorder(bounds.left - stroke, bounds.top - stroke,
                bounds.width() + stroke * 2, stroke);
        addBorder(bounds.left - stroke, bounds.bottom,
                bounds.width() + stroke * 2, stroke);
        addBorder(bounds.left - stroke, bounds.top, stroke, bounds.height());
        addBorder(bounds.right, bounds.top, stroke, bounds.height());
        addToolbar();
    }

    private void addBorder(int x, int y, int width, int height) {
        View border = new View(context);
        border.setBackgroundColor(Ui.ACCENT);
        add(border, x, y, Math.max(1, width), Math.max(1, height), true);
    }

    private void addToolbar() {
        int height = Ui.dp(context, 44);
        Rect display = windows.getCurrentWindowMetrics().getBounds();
        int y = bounds.top >= height + Ui.dp(context, 4)
                ? bounds.top - height - Ui.dp(context, 3)
                : Math.min(display.bottom - height, bounds.bottom + Ui.dp(context, 3));
        LinearLayout bar = new LinearLayout(context);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(context, 12), 0, Ui.dp(context, 4), 0);
        bar.setBackground(Ui.stroked(Ui.CARD, 9, Ui.ACCENT, 1, context));

        TextView title = Ui.heading(context, preset.label, 14);
        title.setSingleLine(true);
        title.setOnTouchListener(this::onDrag);
        bar.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        Button previous = compactButton("‹");
        previous.setContentDescription("Предыдущее приложение");
        previous.setOnClickListener(v -> listener.onSwitchRelative(-1));
        bar.addView(previous);
        Button next = compactButton("›");
        next.setContentDescription("Следующее приложение");
        next.setOnClickListener(v -> listener.onSwitchRelative(1));
        bar.addView(next);
        Button close = compactButton("×");
        close.setContentDescription("Закрыть окно");
        close.setOnClickListener(v -> listener.onCloseRequested());
        bar.addView(close);
        add(bar, bounds.left, y, bounds.width(), height, false);
    }

    private Button compactButton(String label) {
        Button button = Ui.button(context, label);
        button.setTextSize(22);
        button.setPadding(0, 0, 0, 0);
        button.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        button.setLayoutParams(new LinearLayout.LayoutParams(
                Ui.dp(context, 44), LinearLayout.LayoutParams.MATCH_PARENT));
        return button;
    }

    private boolean onDrag(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                dragStartX = event.getRawX();
                dragStartY = event.getRawY();
                dragStartBounds = bounds;
                return true;
            }
            case MotionEvent.ACTION_MOVE -> {
                if (dragStartBounds == null) return false;
                int dx = Math.round(event.getRawX() - dragStartX);
                int dy = Math.round(event.getRawY() - dragStartY);
                Rect display = windows.getCurrentWindowMetrics().getBounds();
                int left = Math.max(0, Math.min(dragStartBounds.left + dx,
                        display.width() - dragStartBounds.width()));
                int top = Math.max(0, Math.min(dragStartBounds.top + dy,
                        display.height() - dragStartBounds.height()));
                bounds = new WindowBounds(left, top,
                        left + dragStartBounds.width(), top + dragStartBounds.height());
                return true;
            }
            case MotionEvent.ACTION_UP -> {
                if (dragStartBounds != null && !dragStartBounds.equals(bounds)) {
                    // Rebuilding while ACTION_MOVE is being dispatched removes the title view
                    // that owns this gesture, so ACTION_UP may never arrive. Apply the chrome
                    // position only after the gesture has completed.
                    rebuild();
                    listener.onMoveRequested(bounds);
                }
                dragStartBounds = null;
                return true;
            }
            case MotionEvent.ACTION_CANCEL -> {
                if (dragStartBounds != null) bounds = dragStartBounds;
                dragStartBounds = null;
                return true;
            }
            default -> { return false; }
        }
    }

    private void add(View view, int x, int y, int width, int height, boolean notTouchable) {
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        if (notTouchable) flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                width, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                flags, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = x;
        params.y = y;
        // ActivityOptions bounds are physical screen coordinates. Do not let system-bar insets
        // shift the Atlas frame away from the foreign task it describes.
        params.setFitInsetsTypes(0);
        try {
            windows.addView(view, params);
            attached.add(view);
        } catch (SecurityException | WindowManager.BadTokenException error) {
            AppLog.warn("Cannot attach Atlas chrome", error);
        }
    }

    private void removeAll() {
        for (View view : attached) {
            try { windows.removeViewImmediate(view); } catch (IllegalArgumentException ignored) {}
        }
        attached.clear();
    }
}
