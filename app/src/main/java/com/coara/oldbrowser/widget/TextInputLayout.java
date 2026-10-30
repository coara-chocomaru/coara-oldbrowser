package com.coara.oldbrowser.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.coara.oldbrowser.R;

public class TextInputLayout extends LinearLayout {
    private final TextView label;
    private final LinearLayout box;
    private final GradientDrawable boxShape = new GradientDrawable();
    private ImageView startIcon;
    private ImageButton clearButton;
    private EditText editText;
    private CharSequence hint;
    private boolean hintEnabled = true;
    private boolean clearMode;
    private int boxColor = 0xFFF1F3F4;
    private int strokeColor;
    private int strokeWidth;
    private float[] radii = new float[8];

    public TextInputLayout(Context context) {
        this(context, null);
    }

    public TextInputLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TextInputLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        super.setOrientation(VERTICAL);
        float density = context.getResources().getDisplayMetrics().density;
        float r = 4f * density;
        float tl = r;
        float tr = r;
        float bl = r;
        float br = r;
        Drawable icon = null;
        ColorStateList iconTint = null;
        if (attrs != null) {
            int count = attrs.getAttributeCount();
            for (int i = 0; i < count; i++) {
                if ("hint".equals(attrs.getAttributeName(i))) {
                    int res = attrs.getAttributeResourceValue(i, 0);
                    hint = res != 0 ? context.getString(res) : attrs.getAttributeValue(i);
                }
            }
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.TextInputLayout, defStyleAttr, 0);
            try {
                boxColor = a.getColor(R.styleable.TextInputLayout_boxBackgroundColor, boxColor);
                tl = a.getDimension(R.styleable.TextInputLayout_boxCornerRadiusTopStart, tl);
                tr = a.getDimension(R.styleable.TextInputLayout_boxCornerRadiusTopEnd, tr);
                bl = a.getDimension(R.styleable.TextInputLayout_boxCornerRadiusBottomStart, bl);
                br = a.getDimension(R.styleable.TextInputLayout_boxCornerRadiusBottomEnd, br);
                icon = a.getDrawable(R.styleable.TextInputLayout_startIconDrawable);
                iconTint = a.getColorStateList(R.styleable.TextInputLayout_startIconTint);
                clearMode = a.getInt(R.styleable.TextInputLayout_endIconMode, 0) == 1;
                hintEnabled = a.getBoolean(R.styleable.TextInputLayout_hintEnabled, true);
            } finally {
                a.recycle();
            }
        }
        radii = new float[]{tl, tl, tr, tr, br, br, bl, bl};
        label = new TextView(context);
        label.setTextSize(12f);
        label.setTextColor(0xFF5F6368);
        label.setPadding(Math.round(4f * density), 0, Math.round(4f * density), Math.round(4f * density));
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        box = new LinearLayout(context);
        box.setOrientation(HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Math.round(12f * density);
        box.setPadding(pad, 0, pad, 0);
        applyBox();
        box.setBackground(boxShape);
        if (icon != null) {
            startIcon = new ImageView(context);
            Drawable d = icon.mutate();
            if (iconTint != null) d.setTintList(iconTint);
            startIcon.setImageDrawable(d);
            int s = Math.round(24f * density);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(s, s);
            lp.setMarginEnd(Math.round(8f * density));
            box.addView(startIcon, lp);
        }
        if (hintEnabled && !TextUtils.isEmpty(hint)) {
            label.setText(hint);
            super.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        super.addView(box, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void applyBox() {
        boxShape.setColor(boxColor);
        boxShape.setCornerRadii(radii);
        boxShape.setStroke(strokeWidth, strokeColor);
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        if (child instanceof EditText && editText == null) {
            editText = (EditText) child;
            editText.setBackground(null);
            if (hintEnabled && !TextUtils.isEmpty(hint) && TextUtils.isEmpty(editText.getHint())) {
                editText.setHint(hint);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            box.addView(editText, lp);
            if (clearMode) attachClear();
            return;
        }
        super.addView(child, index, params);
    }

    private void attachClear() {
        float density = getResources().getDisplayMetrics().density;
        clearButton = new ImageButton(getContext());
        clearButton.setImageResource(R.drawable.ic_close);
        clearButton.setBackground(null);
        clearButton.setContentDescription("clear");
        clearButton.setColorFilter(0xFF5F6368);
        int s = Math.round(32f * density);
        box.addView(clearButton, new LinearLayout.LayoutParams(s, s));
        clearButton.setVisibility(editText.length() > 0 ? VISIBLE : GONE);
        clearButton.setOnClickListener(v -> editText.setText(""));
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                clearButton.setVisibility(s.length() > 0 ? VISIBLE : GONE);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    public EditText getEditText() {
        return editText;
    }

    public void setBoxBackgroundColor(int color) {
        boxColor = color;
        applyBox();
    }

    public void setBoxStrokeColor(int color) {
        strokeColor = color;
        if (strokeWidth == 0) strokeWidth = Math.max(1, Math.round(getResources().getDisplayMetrics().density));
        applyBox();
    }

    public void setHint(CharSequence value) {
        hint = value;
        label.setText(value);
        if (editText != null) editText.setHint(value);
    }

    public CharSequence getHint() {
        return hint;
    }

    public void setError(CharSequence error) {
        if (error == null) {
            label.setTextColor(0xFF5F6368);
            label.setText(hint);
        } else {
            label.setTextColor(0xFFD93025);
            label.setText(error);
            if (label.getParent() == null) {
                super.addView(label, 0, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
        }
    }
}
