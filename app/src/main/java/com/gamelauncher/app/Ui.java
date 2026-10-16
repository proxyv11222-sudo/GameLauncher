package com.gamelauncher.app;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Cores, formas e animações usadas pelo app inteiro. */
final class Ui {
    private Ui() {}

    static final int BG1 = 0xFF0A0E1A;
    static final int BG2 = 0xFF171E40;
    static final int CARD = 0xFF151B30;
    static final int FIELD = 0xFF1D2542;
    static final int STROKE = 0xFF2A3355;
    static final int TXT = 0xFFF2F4FA;
    static final int SUB = 0xFF8D96B5;
    static final int ACC1 = 0xFF7C5CFF;
    static final int ACC2 = 0xFF2EA8FF;
    static final int GREEN = 0xFF47E08F;
    static final int YELLOW = 0xFFFFC857;
    static final int RED = 0xFFFF5C6C;
    static final int GOLD = 0xFFFFC94D;

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    // ------------------------------------------------------------ formas
    static GradientDrawable box(Context c, int fill, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    static GradientDrawable card(Context c) {
        GradientDrawable g = box(c, CARD, 22);
        g.setStroke(dp(c, 1), STROKE);
        return g;
    }

    static GradientDrawable gradient(Context c, int a, int b, GradientDrawable.Orientation o, float radiusDp) {
        GradientDrawable g = new GradientDrawable(o, new int[]{a, b});
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    static GradientDrawable oval(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    static GradientDrawable ovalGradient(int a, int b) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{a, b});
        g.setShape(GradientDrawable.OVAL);
        return g;
    }

    // ------------------------------------------------------------ views
    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static LinearLayout.LayoutParams lp(Context c, int w, int h, float topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.topMargin = dp(c, topDp);
        return p;
    }

    // ------------------------------------------------------------ animações
    /** Efeito de "apertar" ao tocar. */
    static void pressable(View v) {
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f).setDuration(220)
                            .setInterpolator(new OvershootInterpolator()).start();
                    break;
                default:
                    break;
            }
            return false;  // o clique continua funcionando
        });
    }

    /** Entrada suave: aparece e sobe. */
    static void enter(View v, long delayMs) {
        v.setAlpha(0f);
        v.setTranslationY(v.getResources().getDisplayMetrics().density * 28f);
        v.animate().alpha(1f).translationY(0f)
                .setStartDelay(delayMs).setDuration(480)
                .setInterpolator(new DecelerateInterpolator(1.6f)).start();
    }

    /** Balança de lado a lado (senha errada). */
    static void shake(View v) {
        ObjectAnimator a = ObjectAnimator.ofFloat(v, View.TRANSLATION_X, 0f, -18f, 18f, -14f, 14f, -8f, 8f, 0f);
        a.setDuration(420);
        a.start();
    }

    /** Pulsa devagar (bolinha de status). */
    static ObjectAnimator pulse(View v) {
        ObjectAnimator a = ObjectAnimator.ofPropertyValuesHolder(v,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.45f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.45f),
                PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0.55f));
        a.setDuration(900);
        a.setRepeatCount(ValueAnimator.INFINITE);
        a.setRepeatMode(ValueAnimator.REVERSE);
        a.start();
        return a;
    }
}
