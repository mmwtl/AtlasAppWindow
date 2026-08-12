package com.mmwtl.atlasappwindow;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    static final int BACKGROUND = Color.rgb(29, 34, 40);
    static final int CARD = Color.rgb(38, 38, 38);
    static final int NESTED = Color.rgb(51, 51, 51);
    static final int PRIMARY = Color.rgb(245, 245, 245);
    static final int SECONDARY = Color.rgb(212, 212, 212);
    static final int ACCENT = Color.rgb(120, 147, 160);
    static final int OUTLINE = Color.rgb(115, 115, 115);
    static final int ERROR = Color.rgb(217, 130, 130);

    private Ui() {}

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable background(int color, float radiusDp, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    static GradientDrawable stroked(int color, float radiusDp, int strokeColor,
            float strokeDp, Context context) {
        GradientDrawable drawable = background(color, radiusDp, context);
        drawable.setStroke(dp(context, strokeDp), strokeColor);
        return drawable;
    }

    static GradientDrawable topRounded(int color, float radiusDp, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        float radius = dp(context, radiusDp);
        drawable.setCornerRadii(new float[] {
                radius, radius, radius, radius, 0f, 0f, 0f, 0f
        });
        return drawable;
    }

    static TextView text(Context context, String value, float sizeSp, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        return view;
    }

    static TextView heading(Context context, String value, float sizeSp) {
        TextView view = text(context, value, sizeSp, PRIMARY);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    static Button button(Context context, String value) {
        Button button = new Button(context);
        button.setText(value);
        button.setTextColor(PRIMARY);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(context, 16), dp(context, 10),
                dp(context, 16), dp(context, 10));
        button.setBackground(background(NESTED, 8, context));
        return button;
    }

    static Button outlinedButton(Context context, String value) {
        Button button = button(context, value);
        button.setBackground(stroked(Color.TRANSPARENT, 8, OUTLINE, 1, context));
        return button;
    }

    static Button primaryButton(Context context, String value) {
        Button button = button(context, value);
        button.setTextColor(Color.rgb(7, 16, 20));
        button.setBackground(background(ACCENT, 8, context));
        return button;
    }

    static LinearLayout card(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(context, 20), dp(context, 18),
                dp(context, 20), dp(context, 18));
        card.setBackground(background(CARD, 8, context));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(context, 12);
        card.setLayoutParams(params);
        return card;
    }

    static LinearLayout.LayoutParams fullWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    static void topMargin(View view, Context context, int dp) {
        ViewGroup.LayoutParams raw = view.getLayoutParams();
        LinearLayout.LayoutParams params = raw instanceof LinearLayout.LayoutParams
                ? (LinearLayout.LayoutParams) raw : fullWrap();
        params.topMargin = Ui.dp(context, dp);
        view.setLayoutParams(params);
    }

    static void applySystemBarInsets(View view) {
        if (Build.VERSION.SDK_INT < 35) return;
        int left = view.getPaddingLeft();
        int top = view.getPaddingTop();
        int right = view.getPaddingRight();
        int bottom = view.getPaddingBottom();
        view.setOnApplyWindowInsetsListener((target, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            target.setPadding(left + bars.left, top + bars.top,
                    right + bars.right, bottom + bars.bottom);
            return insets;
        });
        view.requestApplyInsets();
    }
}
