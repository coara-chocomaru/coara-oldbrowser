package com.coara.oldbrowser.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageButton;

import com.coara.oldbrowser.R;

public class FloatingActionButton extends ImageButton {
    private ColorStateList tint;

    public FloatingActionButton(Context context) {
        this(context, null);
    }

    public FloatingActionButton(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FloatingActionButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        float density = context.getResources().getDisplayMetrics().density;
        int mini = 0;
        int color = 0xFF4285F4;
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.FloatingActionButton, defStyleAttr, 0);
        try {
            mini = a.getInt(R.styleable.FloatingActionButton_fabSize, 0);
            tint = a.getColorStateList(R.styleable.FloatingActionButton_backgroundTint);
        } finally {
            a.recycle();
        }
        if (tint == null) {
            TypedArray p = context.obtainStyledAttributes(new int[]{android.R.attr.colorPrimary});
            try {
                color = p.getColor(0, color);
            } finally {
                p.recycle();
            }
            tint = ColorStateList.valueOf(color);
        }
        final int size = Math.round((mini == 1 ? 40f : 56f) * density);
        setMinimumWidth(size);
        setMinimumHeight(size);
        setScaleType(ScaleType.CENTER);
        setElevation(6f * density);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        setClipToOutline(false);
        rebuild();
    }

    private void rebuild() {
        GradientDrawable fill = new GradientDrawable();
        fill.setShape(GradientDrawable.OVAL);
        fill.setColor(tint);
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(0xFFFFFFFF);
        super.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), fill, mask));
    }

    @Override
    public void setBackgroundTintList(ColorStateList list) {
        tint = list == null ? ColorStateList.valueOf(0) : list;
        rebuild();
    }

    @Override
    protected void onMeasure(int w, int h) {
        super.onMeasure(w, h);
        int size = Math.max(getMeasuredWidth(), getMeasuredHeight());
        size = Math.max(size, Math.max(getMinimumWidth(), getMinimumHeight()));
        setMeasuredDimension(size, size);
    }
}
