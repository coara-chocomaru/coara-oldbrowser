package com.coara.oldbrowser.widget;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.coara.oldbrowser.R;

public class BottomSheetDialog extends Dialog {
    private final FrameLayout sheet;
    private int maxWidth;

    public BottomSheetDialog(Context context) {
        super(context, R.style.Theme_CoaraBrowser_BottomSheet);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        sheet = new SheetFrame(context);
        super.setContentView(sheet, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setCanceledOnTouchOutside(true);
    }

    @Override
    public void setContentView(View view) {
        sheet.removeAllViews();
        sheet.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
    }

    @Override
    public void setContentView(int layoutResID) {
        setContentView(getLayoutInflater().inflate(layoutResID, sheet, false));
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        setContentView(view);
    }

    public View getSheetContent() {
        return sheet.getChildCount() > 0 ? sheet.getChildAt(0) : null;
    }

    public void setMaxWidth(int px) {
        maxWidth = px;
        sheet.requestLayout();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Window w = getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.setGravity(Gravity.BOTTOM);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.4f);
            w.setWindowAnimations(R.style.Animation_CoaraBrowser_BottomSheet);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    private final class SheetFrame extends FrameLayout {
        private final int slop;
        private float downY;
        private boolean dragging;

        SheetFrame(Context context) {
            super(context);
            slop = ViewConfiguration.get(context).getScaledTouchSlop();
        }

        private View content() {
            return getChildCount() > 0 ? getChildAt(0) : null;
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            super.onMeasure(widthSpec, heightSpec);
            View c = content();
            if (maxWidth > 0 && c != null && c.getMeasuredWidth() > maxWidth) {
                c.measure(MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.AT_MOST));
            }
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            View c = content();
            if (c == null) return false;
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downY = ev.getRawY();
                    dragging = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!dragging && ev.getRawY() - downY > slop * 2 && !canScrollUp(c)) {
                        dragging = true;
                        return true;
                    }
                    break;
                default:
                    break;
            }
            return false;
        }

        private boolean canScrollUp(View v) {
            if (v.canScrollVertically(-1)) return true;
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) {
                    if (g.getChildAt(i) instanceof ViewGroup && canScrollUp(g.getChildAt(i))) return true;
                    if (g.getChildAt(i).canScrollVertically(-1)) return true;
                }
            }
            return false;
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            View c = content();
            if (c == null) return false;
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (dragging) {
                        c.setTranslationY(Math.max(0f, ev.getRawY() - downY));
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (dragging) {
                        dragging = false;
                        if (c.getTranslationY() > c.getHeight() / 3f && ev.getActionMasked() == MotionEvent.ACTION_UP) {
                            dismiss();
                        } else {
                            c.animate().translationY(0f).setDuration(150L).start();
                        }
                    } else if (ev.getActionMasked() == MotionEvent.ACTION_UP && ev.getY() < c.getTop()) {
                        cancel();
                    }
                    return true;
                default:
                    return true;
            }
        }
    }
}
