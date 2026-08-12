package com.mmwtl.atlasappwindow;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** A non-interactive, proportional preview of the HOME screen and freeform task bounds. */
final class WindowPreviewView extends View {
    private static final float OUTER_RADIUS_DP = 12f;
    private static final float INNER_RADIUS_DP = 7f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF displayRect = new RectF();
    private final RectF windowRect = new RectF();

    private int displayWidth = 1440;
    private int displayHeight = 1920;
    private int left;
    private int top;
    private int right;
    private int bottom;

    WindowPreviewView(Context context) {
        this(context, null);
    }

    WindowPreviewView(Context context, AttributeSet attributes) {
        super(context, attributes);
        setMinimumHeight(Ui.dp(context, 250));
        setContentDescription("Предпросмотр границ свободного окна");
    }

    void setGeometry(int width, int height, int left, int top, int right, int bottom) {
        displayWidth = Math.max(1, width);
        displayHeight = Math.max(1, height);
        this.left = clamp(left, 0, displayWidth);
        this.top = clamp(top, 0, displayHeight);
        this.right = clamp(right, 0, displayWidth - this.left);
        this.bottom = clamp(bottom, 0, displayHeight - this.top);
        invalidate();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int availableWidth = Math.max(Ui.dp(getContext(), 180), MeasureSpec.getSize(widthMeasureSpec));
        float aspect = displayWidth / (float) displayHeight;
        int desiredHeight = Math.round(availableWidth / Math.max(0.35f, aspect));
        desiredHeight = clamp(desiredHeight, Ui.dp(getContext(), 230), Ui.dp(getContext(), 390));
        int measuredHeight = resolveSize(desiredHeight, heightMeasureSpec);
        setMeasuredDimension(resolveSize(availableWidth, widthMeasureSpec), measuredHeight);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float pad = Ui.dp(getContext(), 10);
        float labelSpace = Ui.dp(getContext(), 24);
        float availableWidth = Math.max(1f, getWidth() - pad * 2f);
        float availableHeight = Math.max(1f, getHeight() - pad * 2f - labelSpace);
        float scale = Math.min(availableWidth / displayWidth, availableHeight / displayHeight);
        float screenWidth = displayWidth * scale;
        float screenHeight = displayHeight * scale;
        float originX = (getWidth() - screenWidth) / 2f;
        float originY = pad;

        displayRect.set(originX, originY, originX + screenWidth, originY + screenHeight);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Ui.BACKGROUND);
        canvas.drawRoundRect(displayRect, Ui.dp(getContext(), OUTER_RADIUS_DP),
                Ui.dp(getContext(), OUTER_RADIUS_DP), paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Ui.dp(getContext(), 1));
        paint.setColor(Ui.SECONDARY);
        canvas.drawRoundRect(displayRect, Ui.dp(getContext(), OUTER_RADIUS_DP),
                Ui.dp(getContext(), OUTER_RADIUS_DP), paint);

        float windowLeft = originX + left * scale;
        float windowTop = originY + top * scale;
        float windowRight = originX + (displayWidth - right) * scale;
        float windowBottom = originY + (displayHeight - bottom) * scale;
        windowRect.set(windowLeft, windowTop,
                Math.max(windowLeft, windowRight), Math.max(windowTop, windowBottom));
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Ui.NESTED);
        canvas.drawRoundRect(windowRect, Ui.dp(getContext(), INNER_RADIUS_DP),
                Ui.dp(getContext(), INNER_RADIUS_DP), paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Ui.dp(getContext(), 2));
        paint.setColor(Ui.ACCENT);
        canvas.drawRoundRect(windowRect, Ui.dp(getContext(), INNER_RADIUS_DP),
                Ui.dp(getContext(), INNER_RADIUS_DP), paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(Ui.PRIMARY);
        paint.setTextSize(Ui.dp(getContext(), 12));
        if (windowRect.width() > Ui.dp(getContext(), 72)
                && windowRect.height() > Ui.dp(getContext(), 34)) {
            Paint.FontMetrics metrics = paint.getFontMetrics();
            float baseline = windowRect.centerY() - (metrics.ascent + metrics.descent) / 2f;
            canvas.drawText("FREEFORM APP", windowRect.centerX(), baseline, paint);
        }

        paint.setColor(Ui.SECONDARY);
        paint.setTextSize(Ui.dp(getContext(), 11));
        Paint.FontMetrics metrics = paint.getFontMetrics();
        float labelY = displayRect.bottom + labelSpace / 2f
                - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(displayWidth + " × " + displayHeight + " px · снаружи доступен HOME",
                getWidth() / 2f, labelY, paint);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }
}
