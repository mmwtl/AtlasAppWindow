package com.mmwtl.atlasappwindow;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
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

    private static final int CORNER_RADIUS_DP = 18;
    private static final int FLOATING_GAP_DP = 8;
    private static final int CONTROL_SIZE_DP = 44;
    private static final int HEADER_COLOR = 0xff4a4d50;

    private final Context context;
    private final WindowManager windows;
    private final Listener listener;
    private final List<View> attached = new ArrayList<>();
    private Preset preset;
    private WindowBounds bounds;
    private ChromeStyle style = new ChromeStyle(true, true, true,
            ChromeStyle.DEFAULT_HEADER_HEIGHT_DP);
    private boolean desiredVisible;
    private float dragStartX;
    private float dragStartY;
    private WindowBounds dragStartBounds;

    ChromeController(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
        windows = context.getSystemService(WindowManager.class);
    }

    void setWindow(Preset preset, WindowBounds bounds, ChromeStyle style) {
        this.preset = preset;
        this.bounds = bounds;
        this.style = style;
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
        addRoundedMask();
        if (style.headerVisible) {
            addHeader();
        } else if (style.controlsVisible) {
            addFloatingControls();
        }
    }

    private void addRoundedMask() {
        RoundedMaskView mask = new RoundedMaskView(context, !style.headerVisible, true);
        add(mask, bounds.left, bounds.top, bounds.width(), bounds.height(), true);
    }

    private void addHeader() {
        int height = Ui.dp(context, style.headerHeightDp);
        int y = bounds.top >= height ? bounds.top - height : bounds.top;
        LinearLayout bar = new LinearLayout(context);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(context, 14), 0, Ui.dp(context, 4), 0);
        bar.setBackground(Ui.topRounded(HEADER_COLOR, CORNER_RADIUS_DP, context));
        bar.setElevation(Ui.dp(context, 5));

        TextView dragArea = Ui.heading(context, style.titleVisible ? preset.label : "", 15);
        dragArea.setSingleLine(true);
        dragArea.setOnTouchListener(this::onDrag);
        dragArea.setContentDescription(style.titleVisible
                ? preset.label + ". Перетащите для перемещения окна"
                : "Перетащите для перемещения окна");
        bar.addView(dragArea, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        if (style.controlsVisible) {
            addControls(bar, Math.min(Ui.dp(context, CONTROL_SIZE_DP), bounds.width() / 3));
        }
        add(bar, bounds.left, y, bounds.width(), height, false);
    }

    private void addFloatingControls() {
        int height = Ui.dp(context, CONTROL_SIZE_DP);
        int width = Math.min(height * 3, bounds.width());
        int controlWidth = Math.max(1, width / 3);
        int gap = Ui.dp(context, FLOATING_GAP_DP);
        Rect display = windows.getCurrentWindowMetrics().getBounds();
        int x = Math.max(0, Math.min(bounds.right - width, display.right - width));
        int y = bounds.top >= height + gap ? bounds.top - height - gap : bounds.top + gap;

        LinearLayout controls = new LinearLayout(context);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(0, 0, 0, 0);
        controls.setBackground(Ui.background(HEADER_COLOR, 14, context));
        controls.setElevation(Ui.dp(context, 6));
        addControls(controls, controlWidth);
        add(controls, x, y, width, height, false);
    }

    private void addControls(LinearLayout parent, int controlWidth) {
        Button previous = compactButton("‹", "Предыдущее приложение", controlWidth);
        previous.setOnClickListener(v -> listener.onSwitchRelative(-1));
        parent.addView(previous);
        Button next = compactButton("›", "Следующее приложение", controlWidth);
        next.setOnClickListener(v -> listener.onSwitchRelative(1));
        parent.addView(next);
        Button close = compactButton("×", "Закрыть окно", controlWidth);
        close.setOnClickListener(v -> listener.onCloseRequested());
        parent.addView(close);
    }

    private Button compactButton(String label, String description, int width) {
        Button button = Ui.button(context, label);
        button.setTextSize(22);
        button.setPadding(0, 0, 0, 0);
        button.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        button.setContentDescription(description);
        button.setLayoutParams(new LinearLayout.LayoutParams(
                width, LinearLayout.LayoutParams.MATCH_PARENT));
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

    private static final class RoundedMaskView extends View {
        private final boolean roundTop;
        private final boolean roundBottom;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path inner = new Path();

        RoundedMaskView(Context context, boolean roundTop, boolean roundBottom) {
            super(context);
            this.roundTop = roundTop;
            this.roundBottom = roundBottom;
            paint.setColor(Ui.BACKGROUND);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float radius = Ui.dp(getContext(), CORNER_RADIUS_DP);
            float top = roundTop ? radius : 0f;
            float bottom = roundBottom ? radius : 0f;
            inner.reset();
            inner.addRoundRect(0f, 0f, getWidth(), getHeight(), new float[] {
                    top, top, top, top, bottom, bottom, bottom, bottom
            }, Path.Direction.CW);
            int save = canvas.save();
            canvas.clipOutPath(inner);
            canvas.drawColor(Ui.BACKGROUND);
            canvas.restoreToCount(save);
        }
    }
}
