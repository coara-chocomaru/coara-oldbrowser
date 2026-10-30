package com.coara.oldbrowser.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

public class ItemTouchHelper {
    public static final int LEFT = 4;
    public static final int RIGHT = 8;
    public static final int ACTION_STATE_IDLE = 0;
    public static final int ACTION_STATE_SWIPE = 1;

    public abstract static class Callback {
        public int getSwipeDirs(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            return LEFT | RIGHT;
        }

        public abstract boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder,
                                       RecyclerView.ViewHolder target);

        public abstract void onSwiped(RecyclerView.ViewHolder viewHolder, int direction);

        public void onChildDraw(Canvas c, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder,
                                float dX, float dY, int actionState, boolean isCurrentlyActive) {
            viewHolder.itemView.setTranslationX(dX);
        }

        public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            viewHolder.itemView.setTranslationX(0f);
            viewHolder.itemView.setTranslationY(0f);
        }
    }

    public abstract static class SimpleCallback extends Callback {
        private final int swipeDirs;

        public SimpleCallback(int dragDirs, int swipeDirs) {
            this.swipeDirs = swipeDirs;
        }

        @Override
        public int getSwipeDirs(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            return swipeDirs;
        }
    }

    private final Callback callback;
    private RecyclerView list;
    private int slop;
    private float minFlingVelocity;
    private float downX;
    private float downY;
    private RecyclerView.ViewHolder candidate;
    private RecyclerView.ViewHolder active;
    private float dX;
    private boolean tracking;
    private VelocityTracker velocity;
    private ValueAnimator settle;

    public ItemTouchHelper(Callback callback) {
        this.callback = callback;
    }

    public void attachToRecyclerView(RecyclerView recyclerView) {
        if (list == recyclerView) {
            return;
        }
        if (list != null) {
            list.setSwipeHelper(null);
        }
        list = recyclerView;
        if (list != null) {
            ViewConfiguration vc = ViewConfiguration.get(list.getContext());
            slop = vc.getScaledTouchSlop();
            minFlingVelocity = vc.getScaledMinimumFlingVelocity() * 4f;
            list.setSwipeHelper(this);
        }
    }

    private RecyclerView.ViewHolder holderAt(float x, float y) {
        for (int i = list.getChildCount() - 1; i >= 0; i--) {
            View row = list.getChildAt(i);
            if (y < row.getTop() || y > row.getBottom() || x < row.getLeft() || x > row.getRight()) {
                continue;
            }
            Object tag = row.getTag(com.coara.oldbrowser.R.id.recycler_holder);
            if (tag instanceof RecyclerView.ViewHolder) {
                return (RecyclerView.ViewHolder) tag;
            }
            if (row instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) row;
                float lx = x - row.getLeft();
                float ly = y - row.getTop();
                for (int j = group.getChildCount() - 1; j >= 0; j--) {
                    View cell = group.getChildAt(j);
                    if (lx >= cell.getLeft() + cell.getTranslationX() && lx <= cell.getRight() + cell.getTranslationX()
                            && ly >= cell.getTop() && ly <= cell.getBottom()) {
                        Object cellTag = cell.getTag(com.coara.oldbrowser.R.id.recycler_holder);
                        if (cellTag instanceof RecyclerView.ViewHolder) {
                            return (RecyclerView.ViewHolder) cellTag;
                        }
                    }
                }
            }
            return null;
        }
        return null;
    }

    boolean isActive() {
        return tracking;
    }

    boolean onIntercept(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (settle != null && settle.isRunning()) {
                    settle.end();
                }
                downX = ev.getX();
                downY = ev.getY();
                candidate = holderAt(downX, downY);
                tracking = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (candidate != null && !tracking) {
                    float mx = ev.getX() - downX;
                    float my = ev.getY() - downY;
                    if (Math.abs(mx) > slop && Math.abs(mx) > Math.abs(my) * 1.5f) {
                        int dirs = callback.getSwipeDirs(list, candidate);
                        int dir = mx > 0 ? RIGHT : LEFT;
                        if ((dirs & dir) != 0) {
                            begin(ev);
                            return true;
                        }
                        candidate = null;
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                candidate = null;
                break;
            default:
                break;
        }
        return false;
    }

    private void begin(MotionEvent ev) {
        tracking = true;
        active = candidate;
        dX = 0f;
        if (velocity != null) {
            velocity.recycle();
        }
        velocity = VelocityTracker.obtain();
        velocity.addMovement(ev);
        list.getParent().requestDisallowInterceptTouchEvent(true);
    }

    boolean onTouch(MotionEvent ev) {
        if (!tracking || active == null) {
            return false;
        }
        velocity.addMovement(ev);
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                dX = ev.getX() - downX;
                list.invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                velocity.computeCurrentVelocity(1000);
                float vx = velocity.getXVelocity();
                velocity.recycle();
                velocity = null;
                tracking = false;
                int dirs = callback.getSwipeDirs(list, active);
                int dir = dX > 0 ? RIGHT : LEFT;
                float width = Math.max(1, active.itemView.getWidth());
                boolean fling = Math.abs(vx) > minFlingVelocity && (vx > 0) == (dX > 0) && Math.abs(dX) > slop * 2;
                boolean commit = ev.getActionMasked() == MotionEvent.ACTION_UP && (dirs & dir) != 0
                        && (Math.abs(dX) > width * 0.5f || fling);
                finish(commit, dir, width);
                return true;
            default:
                return true;
        }
    }

    private void finish(final boolean commit, final int dir, float width) {
        final RecyclerView.ViewHolder holder = active;
        final float from = dX;
        final float to = commit ? (dir == RIGHT ? width : -width) : 0f;
        settle = ValueAnimator.ofFloat(from, to);
        settle.setDuration(commit ? 140L : 160L);
        settle.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                dX = (Float) animation.getAnimatedValue();
                list.invalidate();
            }
        });
        settle.addListener(new AnimatorListenerAdapter() {
            private boolean done;

            private void complete() {
                if (done) {
                    return;
                }
                done = true;
                active = null;
                dX = 0f;
                if (commit) {
                    callback.onSwiped(holder, dir);
                }
                callback.clearView(list, holder);
                list.invalidate();
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                complete();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                complete();
            }
        });
        settle.start();
    }

    void drawActive(Canvas canvas) {
        if (active == null) {
            return;
        }
        View item = active.itemView;
        View parent = item.getParent() instanceof View ? (View) item.getParent() : null;
        int save = canvas.save();
        if (parent != null && parent != list) {
            canvas.translate(parent.getLeft(), parent.getTop());
        }
        callback.onChildDraw(canvas, list, active, dX, 0f, ACTION_STATE_SWIPE, tracking);
        canvas.restoreToCount(save);
    }
}
