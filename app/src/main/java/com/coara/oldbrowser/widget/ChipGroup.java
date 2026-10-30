package com.coara.oldbrowser.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import com.coara.oldbrowser.R;

public class ChipGroup extends ViewGroup {
    public interface OnCheckedChangeListener {
        void onCheckedChanged(ChipGroup group, int checkedId);
    }

    private int spacingH;
    private int spacingV;
    private boolean singleSelection;
    private boolean selectionRequired;
    private boolean singleLine;
    private int checkedId = View.NO_ID;
    private boolean updating;
    private OnCheckedChangeListener listener;
    private final Chip.OnCheckedChangeListener childListener = new Chip.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(Chip chip, boolean isChecked) {
            if (updating) return;
            if (singleSelection) {
                updating = true;
                if (isChecked) {
                    for (int i = 0; i < getChildCount(); i++) {
                        View c = getChildAt(i);
                        if (c instanceof Chip && c != chip && ((Chip) c).isChecked()) ((Chip) c).setChecked(false);
                    }
                    checkedId = chip.getId();
                } else if (checkedId == chip.getId()) {
                    checkedId = View.NO_ID;
                }
                updating = false;
            }
            if (listener != null) {
                listener.onCheckedChanged(ChipGroup.this, singleSelection ? checkedId : chip.getId());
            }
        }
    };

    public ChipGroup(Context context) {
        this(context, null);
    }

    public ChipGroup(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ChipGroup(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        float density = context.getResources().getDisplayMetrics().density;
        spacingH = Math.round(8f * density);
        spacingV = Math.round(8f * density);
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ChipGroup, defStyleAttr, 0);
            try {
                spacingH = a.getDimensionPixelSize(R.styleable.ChipGroup_chipSpacingHorizontal, spacingH);
                spacingV = a.getDimensionPixelSize(R.styleable.ChipGroup_chipSpacingVertical, spacingV);
                singleLine = a.getBoolean(R.styleable.ChipGroup_singleLine, false);
                singleSelection = a.getBoolean(R.styleable.ChipGroup_singleSelection, false);
                selectionRequired = a.getBoolean(R.styleable.ChipGroup_selectionRequired, false);
            } finally {
                a.recycle();
            }
        }
    }

    boolean canUncheck() {
        return !(singleSelection && selectionRequired);
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        if (child instanceof Chip) {
            Chip chip = (Chip) child;
            chip.setGroupListener(childListener);
            if (chip.isChecked()) {
                if (singleSelection && checkedId != View.NO_ID) {
                    chip.setGroupListener(null);
                    chip.setChecked(false);
                    chip.setGroupListener(childListener);
                } else if (singleSelection) {
                    checkedId = chip.getId();
                }
            }
        }
        super.addView(child, index, params);
    }

    public void setSingleSelection(boolean value) {
        singleSelection = value;
    }

    public void setSelectionRequired(boolean value) {
        selectionRequired = value;
    }

    public void setSingleLine(boolean value) {
        singleLine = value;
        requestLayout();
    }

    public void setChipSpacingHorizontal(int px) {
        spacingH = px;
        requestLayout();
    }

    public void setChipSpacingVertical(int px) {
        spacingV = px;
        requestLayout();
    }

    public int getCheckedChipId() {
        return singleSelection ? checkedId : View.NO_ID;
    }

    public void check(int id) {
        View v = findViewById(id);
        if (v instanceof Chip) ((Chip) v).setChecked(true);
    }

    public void clearCheck() {
        updating = true;
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            if (c instanceof Chip) ((Chip) c).setChecked(false);
        }
        checkedId = View.NO_ID;
        updating = false;
        if (listener != null) listener.onCheckedChanged(this, View.NO_ID);
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener l) {
        listener = l;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int maxWidth = MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED
                ? Integer.MAX_VALUE : MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight();
        int x = 0;
        int y = 0;
        int rowH = 0;
        int usedW = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            if (c.getVisibility() == GONE) continue;
            measureChild(c, widthSpec, heightSpec);
            int w = c.getMeasuredWidth();
            int h = c.getMeasuredHeight();
            if (!singleLine && x > 0 && x + w > maxWidth) {
                x = 0;
                y += rowH + spacingV;
                rowH = 0;
            }
            x += w + spacingH;
            usedW = Math.max(usedW, x - spacingH);
            rowH = Math.max(rowH, h);
        }
        int totalH = y + rowH + getPaddingTop() + getPaddingBottom();
        int totalW = usedW + getPaddingLeft() + getPaddingRight();
        setMeasuredDimension(resolveSize(totalW, widthSpec), resolveSize(totalH, heightSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int maxRight = r - l - getPaddingRight();
        int x = getPaddingLeft();
        int y = getPaddingTop();
        int rowH = 0;
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        int width = r - l;
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            if (c.getVisibility() == GONE) continue;
            int w = c.getMeasuredWidth();
            int h = c.getMeasuredHeight();
            if (!singleLine && x > getPaddingLeft() && x + w > maxRight) {
                x = getPaddingLeft();
                y += rowH + spacingV;
                rowH = 0;
            }
            int left = rtl ? width - x - w : x;
            c.layout(left, y, left + w, y + h);
            x += w + spacingH;
            rowH = Math.max(rowH, h);
        }
    }
}
