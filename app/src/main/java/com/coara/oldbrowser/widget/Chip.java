package com.coara.oldbrowser.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.Checkable;
import android.widget.TextView;

import com.coara.oldbrowser.R;

public class Chip extends TextView implements Checkable {
    public interface OnCheckedChangeListener {
        void onCheckedChanged(Chip chip, boolean checked);
    }

    private static final int[] CHECKED_STATE = {android.R.attr.state_checked};

    private static final ColorStateList DEFAULT_FILL = new ColorStateList(
            new int[][]{CHECKED_STATE, new int[]{}}, new int[]{0x334285F4, 0x00000000});
    private static final ColorStateList DEFAULT_STROKE = new ColorStateList(
            new int[][]{CHECKED_STATE, new int[]{}}, new int[]{0xFF4285F4, 0x33000000});

    private ColorStateList chipBackground;
    private ColorStateList chipStroke;
    private int chipStrokeWidth = -1;
    private boolean customTextColor;
    private boolean checked;
    private boolean broadcasting;
    private boolean checkable = true;
    private OnCheckedChangeListener listener;
    private OnCheckedChangeListener groupListener;

    public Chip(Context context) {
        this(context, null);
    }

    public Chip(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.chipStyle);
    }

    public Chip(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        float density = context.getResources().getDisplayMetrics().density;
        setGravity(Gravity.CENTER);
        setClickable(true);
        setFocusable(true);
        if (attrs != null) {
            int count = attrs.getAttributeCount();
            for (int i = 0; i < count; i++) {
                String name = attrs.getAttributeName(i);
                if ("checked".equals(name)) {
                    checked = attrs.getAttributeBooleanValue(i, false);
                } else if ("checkable".equals(name)) {
                    checkable = attrs.getAttributeBooleanValue(i, true);
                }
            }
        }
        int h = Math.round(12f * density);
        int v = Math.round(6f * density);
        setPadding(h, v, h, v);
        setMinHeight(Math.round(32f * density));
        buildBackground(density);
        updateTextColor();
    }

    private void buildBackground(float density) {
        float r = 16f * density;
        GradientDrawable face = new GradientDrawable();
        face.setCornerRadius(r);
        face.setColor(chipBackground != null ? chipBackground : DEFAULT_FILL);
        int width = chipStrokeWidth >= 0 ? chipStrokeWidth : Math.round(density);
        face.setStroke(width, chipStroke != null ? chipStroke : DEFAULT_STROKE);
        GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadius(r);
        mask.setColor(0xFFFFFFFF);
        setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000), face, mask));
    }

    public void setChipBackgroundColor(ColorStateList color) {
        chipBackground = color;
        buildBackground(getResources().getDisplayMetrics().density);
    }

    public void setChipStrokeColor(ColorStateList color) {
        chipStroke = color;
        buildBackground(getResources().getDisplayMetrics().density);
    }

    public void setChipStrokeWidth(int px) {
        chipStrokeWidth = px;
        buildBackground(getResources().getDisplayMetrics().density);
    }

    @Override
    public void setTextColor(int color) {
        customTextColor = true;
        super.setTextColor(color);
    }

    @Override
    public void setTextColor(ColorStateList colors) {
        customTextColor = true;
        super.setTextColor(colors);
    }

    private void updateTextColor() {
        if (customTextColor) return;
        super.setTextColor(checked ? 0xFF1A56C4 : 0xDE000000);
    }

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        int[] state = super.onCreateDrawableState(extraSpace + 1);
        if (checked) mergeDrawableStates(state, CHECKED_STATE);
        return state;
    }

    @Override
    public boolean performClick() {
        if (checkable) toggle();
        return super.performClick();
    }

    public void setCheckable(boolean value) {
        checkable = value;
    }

    public boolean isCheckable() {
        return checkable;
    }

    @Override
    public void setChecked(boolean value) {
        if (checked == value) return;
        checked = value;
        refreshDrawableState();
        updateTextColor();
        if (broadcasting) return;
        broadcasting = true;
        if (listener != null) listener.onCheckedChanged(this, checked);
        if (groupListener != null) groupListener.onCheckedChanged(this, checked);
        broadcasting = false;
    }

    @Override
    public boolean isChecked() {
        return checked;
    }

    @Override
    public void toggle() {
        if (checked && getParent() instanceof ChipGroup && !((ChipGroup) getParent()).canUncheck()) return;
        setChecked(!checked);
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener l) {
        listener = l;
    }

    void setGroupListener(OnCheckedChangeListener l) {
        groupListener = l;
    }

    @Override
    public void setOnClickListener(View.OnClickListener l) {
        super.setOnClickListener(l);
    }
}
