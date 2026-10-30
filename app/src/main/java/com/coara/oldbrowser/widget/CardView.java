package com.coara.oldbrowser.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

import com.coara.oldbrowser.R;

public class CardView extends FrameLayout {
    private final GradientDrawable shape = new GradientDrawable();
    private float radius;
    private int strokeWidth;
    private ColorStateList strokeColor = ColorStateList.valueOf(0);
    private ColorStateList fill = ColorStateList.valueOf(0xFFFFFFFF);

    public CardView(Context context) {
        this(context, null);
    }

    public CardView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        float density = context.getResources().getDisplayMetrics().density;
        radius = 4f * density;
        float elevation = 1f * density;
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CardView, defStyleAttr, 0);
            try {
                ColorStateList c = a.getColorStateList(R.styleable.CardView_cardBackgroundColor);
                if (c != null) fill = c;
                radius = a.getDimension(R.styleable.CardView_cardCornerRadius, radius);
                elevation = a.getDimension(R.styleable.CardView_cardElevation, elevation);
                ColorStateList s = a.getColorStateList(R.styleable.CardView_strokeColor);
                if (s != null) strokeColor = s;
                strokeWidth = a.getDimensionPixelSize(R.styleable.CardView_strokeWidth, 0);
            } finally {
                a.recycle();
            }
        }
        super.setBackground(shape);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                if (fill.getDefaultColor() >>> 24 == 0) {
                    outline.setAlpha(0f);
                }
            }
        });
        setClipToOutline(true);
        super.setElevation(elevation);
        refresh();
    }

    private void refresh() {
        shape.setColor(fill);
        shape.setCornerRadius(radius);
        shape.setStroke(strokeWidth, strokeColor);
        invalidateOutline();
    }

    public void setCardBackgroundColor(int color) {
        fill = ColorStateList.valueOf(color);
        refresh();
    }

    public void setCardBackgroundColor(ColorStateList color) {
        fill = color == null ? ColorStateList.valueOf(0) : color;
        refresh();
    }

    public ColorStateList getCardBackgroundColor() {
        return fill;
    }

    public void setRadius(float value) {
        radius = value;
        refresh();
    }

    public float getRadius() {
        return radius;
    }

    public void setCardElevation(float value) {
        setElevation(value);
    }

    public float getCardElevation() {
        return getElevation();
    }

    public void setStrokeWidth(int width) {
        strokeWidth = Math.max(0, width);
        refresh();
    }

    public int getStrokeWidth() {
        return strokeWidth;
    }

    public void setStrokeColor(int color) {
        strokeColor = ColorStateList.valueOf(color);
        refresh();
    }

    public void setStrokeColor(ColorStateList color) {
        strokeColor = color == null ? ColorStateList.valueOf(0) : color;
        refresh();
    }

    public ColorStateList getStrokeColorStateList() {
        return strokeColor;
    }

    public int getStrokeColor() {
        return strokeColor.getDefaultColor();
    }
}
