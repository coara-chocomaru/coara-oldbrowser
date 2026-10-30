package com.coara.oldbrowser.widget;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class Snackbar {
    public static final int LENGTH_SHORT = 1500;
    public static final int LENGTH_LONG = 2750;
    public static final int LENGTH_INDEFINITE = -2;

    private final ViewGroup host;
    private final LinearLayout bar;
    private final TextView message;
    private final TextView action;
    private final int duration;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable dismissRunnable = new Runnable() {
        @Override
        public void run() {
            dismiss();
        }
    };
    private boolean shown;

    private Snackbar(ViewGroup host, CharSequence text, int duration) {
        this.host = host;
        this.duration = duration;
        Context context = host.getContext();
        float density = context.getResources().getDisplayMetrics().density;
        bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF323232);
        bg.setCornerRadius(4f * density);
        bar.setBackground(bg);
        bar.setElevation(6f * density);
        int pad = Math.round(16f * density);
        bar.setPadding(pad, Math.round(6f * density), pad, Math.round(6f * density));
        message = new TextView(context);
        message.setText(text);
        message.setTextColor(0xFFFFFFFF);
        message.setTextSize(14f);
        message.setMaxLines(3);
        bar.addView(message, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        action = new TextView(context);
        action.setTextColor(0xFF8AB4F8);
        action.setTextSize(14f);
        action.setAllCaps(true);
        action.setGravity(Gravity.CENTER);
        action.setPadding(pad, Math.round(10f * density), 0, Math.round(10f * density));
        action.setVisibility(View.GONE);
        bar.addView(action, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    public static Snackbar make(View view, CharSequence text, int duration) {
        ViewGroup root = null;
        View v = view;
        while (v != null) {
            if (v instanceof FrameLayout && v.getId() == android.R.id.content) {
                root = (ViewGroup) v;
                break;
            }
            if (v instanceof FrameLayout && root == null) root = (ViewGroup) v;
            ViewGroup.LayoutParams p = v.getLayoutParams();
            Object parent = v.getParent();
            v = parent instanceof View ? (View) parent : null;
            if (p == null && v == null) break;
        }
        if (root == null) throw new IllegalArgumentException("No suitable parent found");
        return new Snackbar(root, text, duration);
    }

    public Snackbar setAction(CharSequence label, final View.OnClickListener listener) {
        action.setText(label);
        action.setVisibility(View.VISIBLE);
        action.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onClick(v);
                dismiss();
            }
        });
        return this;
    }

    public void show() {
        if (shown) return;
        shown = true;
        float density = host.getResources().getDisplayMetrics().density;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        int m = Math.round(8f * density);
        lp.setMargins(m, m, m, m + Math.round(8f * density));
        host.addView(bar, lp);
        bar.setAlpha(0f);
        bar.setTranslationY(48f * density);
        bar.animate().alpha(1f).translationY(0f).setDuration(180L).start();
        if (duration != LENGTH_INDEFINITE) handler.postDelayed(dismissRunnable, duration);
    }

    public void dismiss() {
        if (!shown) return;
        shown = false;
        handler.removeCallbacks(dismissRunnable);
        final float density = host.getResources().getDisplayMetrics().density;
        bar.animate().alpha(0f).translationY(48f * density).setDuration(150L).withEndAction(new Runnable() {
            @Override
            public void run() {
                host.removeView(bar);
            }
        }).start();
    }
}
