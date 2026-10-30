package com.coara.oldbrowser.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;

public class SwipeRefreshLayout extends ViewGroup {
    public interface OnRefreshListener {
        void onRefresh();
    }

    public interface OnChildScrollUpCallback {
        boolean canChildScrollUp(SwipeRefreshLayout parent, View child);
    }

    private static final float DRAG_RATE = 0.5f;
    private static final int INVALID = -1;

    private final Indicator indicator;
    private final int indicatorSize;
    private final int triggerDistance;
    private final int holdOffset;
    private final int touchSlop;
    private View target;
    private OnRefreshListener listener;
    private OnChildScrollUpCallback scrollUpCallback;
    private boolean refreshing;
    private boolean dragging;
    private float initialY;
    private float lastY;
    private int activePointer = INVALID;
    private float offset;
    private ValueAnimator settle;

    public SwipeRefreshLayout(Context context) {
        this(context, null);
    }

    public SwipeRefreshLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        float density = context.getResources().getDisplayMetrics().density;
        indicatorSize = Math.round(40f * density);
        triggerDistance = Math.round(64f * density);
        holdOffset = Math.round(52f * density);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        indicator = new Indicator(context, density);
        indicator.setVisibility(GONE);
        super.addView(indicator, new LayoutParams(indicatorSize, indicatorSize));
        setWillNotDraw(false);
    }

    public void setOnRefreshListener(OnRefreshListener l) {
        listener = l;
    }

    public void setOnChildScrollUpCallback(OnChildScrollUpCallback c) {
        scrollUpCallback = c;
    }

    public void setColorSchemeColors(int... colors) {
        if (colors != null && colors.length > 0) indicator.arcColor = colors[0];
        indicator.invalidate();
    }

    public void setColorSchemeResources(int... ids) {
        if (ids == null || ids.length == 0) return;
        indicator.arcColor = getContext().getColor(ids[0]);
        indicator.invalidate();
    }

    public void setProgressBackgroundColorSchemeColor(int color) {
        indicator.fillColor = color;
        indicator.invalidate();
    }

    public void setProgressBackgroundColorSchemeResource(int id) {
        setProgressBackgroundColorSchemeColor(getContext().getColor(id));
    }

    public boolean isRefreshing() {
        return refreshing;
    }

    public void setRefreshing(boolean value) {
        if (refreshing == value) return;
        refreshing = value;
        if (value) {
            indicator.setVisibility(VISIBLE);
            indicator.startSpin();
            animateTo(holdOffset, null);
        } else {
            animateTo(0, new Runnable() {
                @Override
                public void run() {
                    indicator.stopSpin();
                    indicator.setVisibility(GONE);
                }
            });
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (!enabled) {
            dragging = false;
            if (!refreshing) {
                offset = 0f;
                indicator.setVisibility(GONE);
                applyOffset();
            }
        }
    }

    private void ensureTarget() {
        if (target != null && target.getParent() == this) return;
        target = null;
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            if (c != indicator) {
                target = c;
                break;
            }
        }
    }

    private boolean canChildScrollUp() {
        ensureTarget();
        if (target == null) return true;
        if (scrollUpCallback != null) return scrollUpCallback.canChildScrollUp(this, target);
        return target.canScrollVertically(-1);
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallow) {
        super.requestDisallowInterceptTouchEvent(disallow);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!isEnabled() || refreshing || canChildScrollUp()) {
            if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) activePointer = INVALID;
            return false;
        }
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                activePointer = ev.getPointerId(0);
                initialY = ev.getY();
                lastY = initialY;
                dragging = false;
                break;
            case MotionEvent.ACTION_MOVE: {
                if (activePointer == INVALID) return false;
                int idx = ev.findPointerIndex(activePointer);
                if (idx < 0) return false;
                float y = ev.getY(idx);
                if (y - initialY > touchSlop && !dragging) {
                    dragging = true;
                    lastY = initialY + touchSlop;
                    indicator.setVisibility(VISIBLE);
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                activePointer = INVALID;
                break;
            case MotionEvent.ACTION_POINTER_UP: {
                int index = ev.getActionIndex();
                if (ev.getPointerId(index) == activePointer) {
                    int ni = index == 0 ? 1 : 0;
                    activePointer = ev.getPointerId(ni);
                    initialY = ev.getY(ni);
                    lastY = initialY;
                }
                break;
            }
            default:
                break;
        }
        return dragging;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!isEnabled() || refreshing) return false;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                activePointer = ev.getPointerId(0);
                dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE: {
                int idx = ev.findPointerIndex(activePointer);
                if (idx < 0) return false;
                float y = ev.getY(idx);
                float dy = (y - lastY) * DRAG_RATE;
                lastY = y;
                if (dragging) {
                    offset = Math.max(0f, Math.min(offset + dy, triggerDistance * 2f));
                    indicator.setProgress(Math.min(1f, offset / triggerDistance));
                    applyOffset();
                    if (offset <= 0f && dy < 0f) {
                        dragging = false;
                        return false;
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_POINTER_DOWN:
                activePointer = ev.getPointerId(ev.getActionIndex());
                lastY = ev.getY(ev.getActionIndex());
                return true;
            case MotionEvent.ACTION_POINTER_UP: {
                int index = ev.getActionIndex();
                if (ev.getPointerId(index) == activePointer) {
                    int ni = index == 0 ? 1 : 0;
                    activePointer = ev.getPointerId(ni);
                    lastY = ev.getY(ni);
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                boolean fire = dragging && offset >= triggerDistance && ev.getActionMasked() == MotionEvent.ACTION_UP;
                dragging = false;
                activePointer = INVALID;
                if (fire) {
                    refreshing = true;
                    indicator.startSpin();
                    animateTo(holdOffset, null);
                    if (listener != null) listener.onRefresh();
                } else {
                    animateTo(0, new Runnable() {
                        @Override
                        public void run() {
                            if (!refreshing) indicator.setVisibility(GONE);
                        }
                    });
                }
                return true;
            }
            default:
                return true;
        }
    }

    private void animateTo(float to, final Runnable end) {
        if (settle != null) settle.cancel();
        if (to > 0f && indicator.getVisibility() != VISIBLE) indicator.setVisibility(VISIBLE);
        settle = ValueAnimator.ofFloat(offset, to);
        settle.setDuration(200L);
        settle.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                offset = (Float) animation.getAnimatedValue();
                applyOffset();
            }
        });
        if (end != null) {
            settle.addListener(new android.animation.AnimatorListenerAdapter() {
                private boolean cancelled;

                @Override
                public void onAnimationCancel(android.animation.Animator animation) {
                    cancelled = true;
                }

                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    if (!cancelled) end.run();
                }
            });
        }
        settle.start();
    }

    private void applyOffset() {
        indicator.setTranslationY(offset);
        ensureTarget();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, heightSpec);
        ensureTarget();
        if (target != null) {
            target.measure(
                    MeasureSpec.makeMeasureSpec(getMeasuredWidth() - getPaddingLeft() - getPaddingRight(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(getMeasuredHeight() - getPaddingTop() - getPaddingBottom(), MeasureSpec.EXACTLY));
        }
        indicator.measure(MeasureSpec.makeMeasureSpec(indicatorSize, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(indicatorSize, MeasureSpec.EXACTLY));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        ensureTarget();
        int w = r - l;
        if (target != null) {
            target.layout(getPaddingLeft(), getPaddingTop(), w - getPaddingRight(), b - t - getPaddingBottom());
        }
        int left = (w - indicatorSize) / 2;
        indicator.layout(left, -indicatorSize, left + indicatorSize, 0);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (settle != null) settle.cancel();
        indicator.stopSpin();
    }

    private static final class Indicator extends View {
        int arcColor = 0xFF4285F4;
        int fillColor = 0xFFFFFFFF;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bounds = new RectF();
        private float progress;
        private float rotation;
        private boolean spinning;
        private ValueAnimator spin;

        Indicator(Context context, float density) {
            super(context);
            arc.setStyle(Paint.Style.STROKE);
            arc.setStrokeCap(Paint.Cap.ROUND);
            arc.setStrokeWidth(3f * density);
            setElevation(4f * density);
            setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });
        }

        void setProgress(float value) {
            progress = value;
            invalidate();
        }

        void startSpin() {
            if (spinning) return;
            spinning = true;
            spin = ValueAnimator.ofFloat(0f, 360f);
            spin.setDuration(900L);
            spin.setRepeatCount(ValueAnimator.INFINITE);
            spin.setInterpolator(new LinearInterpolator());
            spin.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    rotation = (Float) animation.getAnimatedValue();
                    invalidate();
                }
            });
            spin.start();
        }

        void stopSpin() {
            spinning = false;
            if (spin != null) {
                spin.cancel();
                spin = null;
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            fill.setColor(fillColor);
            canvas.drawCircle(w / 2f, h / 2f, Math.min(w, h) / 2f, fill);
            arc.setColor(arcColor);
            float pad = w * 0.28f;
            bounds.set(pad, pad, w - pad, h - pad);
            if (spinning) {
                canvas.drawArc(bounds, rotation, 270f, false, arc);
            } else {
                canvas.drawArc(bounds, -90f + progress * 120f, Math.max(10f, progress * 300f), false, arc);
            }
        }
    }
}
