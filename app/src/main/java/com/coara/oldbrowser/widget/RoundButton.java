package com.coara.oldbrowser.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.AttributeSet;
import android.widget.Button;

import com.coara.oldbrowser.R;

public class RoundButton extends Button {
    private static final int[] INSET_ATTRS = {android.R.attr.insetLeft, android.R.attr.insetTop,
            android.R.attr.insetRight, android.R.attr.insetBottom};

    private float cornerRadius;
    private int strokeWidth;
    private ColorStateList strokeColor = ColorStateList.valueOf(0);
    private ColorStateList tint;
    private int insetLeft;
    private int insetTop;
    private int insetRight;
    private int insetBottom;
    private boolean building;

    public RoundButton(Context context) {
        this(context, null);
    }

    public RoundButton(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.roundButtonStyle);
    }

    public RoundButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        float density = context.getResources().getDisplayMetrics().density;
        cornerRadius = 4f * density;
        int defInset = Math.round(6f * density);
        insetLeft = 0;
        insetRight = 0;
        insetTop = defInset;
        insetBottom = defInset;
        tint = null;
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.RoundButton, defStyleAttr, 0);
        try {
            cornerRadius = a.getDimension(R.styleable.RoundButton_cornerRadius, cornerRadius);
            tint = a.getColorStateList(R.styleable.RoundButton_backgroundTint);
            ColorStateList s = a.getColorStateList(R.styleable.RoundButton_strokeColor);
            if (s != null) strokeColor = s;
            strokeWidth = a.getDimensionPixelSize(R.styleable.RoundButton_strokeWidth, 0);
        } finally {
            a.recycle();
        }
        TypedArray ia = context.obtainStyledAttributes(attrs, INSET_ATTRS, defStyleAttr, 0);
        try {
            insetLeft = ia.getDimensionPixelSize(0, insetLeft);
            insetTop = ia.getDimensionPixelSize(1, insetTop);
            insetRight = ia.getDimensionPixelSize(2, insetRight);
            insetBottom = ia.getDimensionPixelSize(3, insetBottom);
        } finally {
            ia.recycle();
        }
        if (tint == null) {
            TypedArray p = context.obtainStyledAttributes(new int[]{android.R.attr.colorPrimary});
            try {
                tint = ColorStateList.valueOf(p.getColor(0, 0xFF4285F4));
            } finally {
                p.recycle();
            }
        }
        rebuild();
    }

    private void rebuild() {
        if (building) return;
        building = true;
        try {
            GradientDrawable fill = new GradientDrawable();
            fill.setCornerRadius(cornerRadius);
            fill.setColor(tint);
            if (strokeWidth > 0) fill.setStroke(strokeWidth, strokeColor);
            GradientDrawable mask = new GradientDrawable();
            mask.setCornerRadius(cornerRadius);
            mask.setColor(Color.WHITE);
            int text = getCurrentTextColor();
            int ripple = (text & 0x00FFFFFF) | 0x33000000;
            Drawable content = new RippleDrawable(ColorStateList.valueOf(ripple), fill, mask);
            Drawable bg = (insetLeft | insetTop | insetRight | insetBottom) != 0
                    ? new InsetDrawable(content, insetLeft, insetTop, insetRight, insetBottom) : content;
            super.setBackground(bg);
        } finally {
            building = false;
        }
    }

    @Override
    public void setTextColor(int color) {
        super.setTextColor(color);
        rebuild();
    }

    @Override
    public void setTextColor(ColorStateList colors) {
        super.setTextColor(colors);
        rebuild();
    }

    @Override
    public void setBackgroundTintList(ColorStateList list) {
        tint = list == null ? ColorStateList.valueOf(0) : list;
        rebuild();
    }

    @Override
    public ColorStateList getBackgroundTintList() {
        return tint;
    }

    public void setCornerRadius(int px) {
        cornerRadius = px;
        rebuild();
    }

    public int getCornerRadius() {
        return Math.round(cornerRadius);
    }

    public void setStrokeWidth(int px) {
        strokeWidth = Math.max(0, px);
        rebuild();
    }

    public int getStrokeWidth() {
        return strokeWidth;
    }

    public void setStrokeColor(ColorStateList color) {
        strokeColor = color == null ? ColorStateList.valueOf(0) : color;
        rebuild();
    }

    public void setStrokeColor(int color) {
        setStrokeColor(ColorStateList.valueOf(color));
    }

    public ColorStateList getStrokeColor() {
        return strokeColor;
    }

    public void setInsetTop(int px) {
        insetTop = px;
        rebuild();
    }

    public int getInsetTop() {
        return insetTop;
    }

    public void setInsetBottom(int px) {
        insetBottom = px;
        rebuild();
    }

    public int getInsetBottom() {
        return insetBottom;
    }
}
