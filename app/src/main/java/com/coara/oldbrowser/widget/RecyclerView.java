package com.coara.oldbrowser.widget;

import android.content.Context;
import android.database.DataSetObservable;
import android.database.DataSetObserver;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RecyclerView extends ListView {
    public static final int NO_POSITION = -1;
    private static final int MAX_ROW_TYPES = 24;

    public abstract static class ViewHolder {
        public final View itemView;
        int position = NO_POSITION;
        int viewType;
        View root;

        public ViewHolder(View itemView) {
            if (itemView == null) throw new IllegalArgumentException("itemView may not be null");
            this.itemView = itemView;
        }

        public int getAdapterPosition() {
            return position;
        }

        public int getBindingAdapterPosition() {
            return position;
        }

        public int getLayoutPosition() {
            return position;
        }

        public int getItemViewType() {
            return viewType;
        }
    }

    public static class LayoutParams extends ViewGroup.MarginLayoutParams {
        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }
    }

    public abstract static class LayoutManager {
    }

    public abstract static class Adapter<VH extends ViewHolder> {
        private final DataSetObservable observable = new DataSetObservable();
        private boolean stableIds;

        public abstract VH onCreateViewHolder(ViewGroup parent, int viewType);

        public abstract void onBindViewHolder(VH holder, int position);

        public abstract int getItemCount();

        public int getItemViewType(int position) {
            return 0;
        }

        public long getItemId(int position) {
            return NO_ID;
        }

        public void setHasStableIds(boolean value) {
            stableIds = value;
        }

        public boolean hasStableIds() {
            return stableIds;
        }

        public void onViewRecycled(VH holder) {
        }

        public final void notifyDataSetChanged() {
            observable.notifyChanged();
        }

        public final void notifyItemChanged(int position) {
            observable.notifyChanged();
        }

        public final void notifyItemChanged(int position, Object payload) {
            observable.notifyChanged();
        }

        public final void notifyItemInserted(int position) {
            observable.notifyChanged();
        }

        public final void notifyItemRemoved(int position) {
            observable.notifyChanged();
        }

        public final void notifyItemMoved(int from, int to) {
            observable.notifyChanged();
        }

        public final void notifyItemRangeChanged(int start, int count) {
            observable.notifyChanged();
        }

        public final void notifyItemRangeInserted(int start, int count) {
            observable.notifyChanged();
        }

        public final void notifyItemRangeRemoved(int start, int count) {
            observable.notifyChanged();
        }

        final void registerObserver(DataSetObserver o) {
            observable.registerObserver(o);
        }

        final void unregisterObserver(DataSetObserver o) {
            observable.unregisterObserver(o);
        }
    }

    public static class OnScrollListener {
        public void onScrollStateChanged(RecyclerView view, int newState) {
        }

        public void onScrolled(RecyclerView view, int dx, int dy) {
        }
    }

    private Adapter<ViewHolder> adapter;
    private LayoutManager layoutManager;
    private Bridge bridge;
    private ItemTouchHelper swipeHelper;

    void setSwipeHelper(ItemTouchHelper helper) {
        swipeHelper = helper;
    }

    @Override
    public boolean onInterceptTouchEvent(android.view.MotionEvent ev) {
        if (swipeHelper != null && swipeHelper.onIntercept(ev)) {
            return true;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent ev) {
        if (swipeHelper != null && swipeHelper.isActive()) {
            return swipeHelper.onTouch(ev);
        }
        return super.onTouchEvent(ev);
    }

    @Override
    protected void dispatchDraw(android.graphics.Canvas canvas) {
        if (swipeHelper != null) {
            swipeHelper.drawActive(canvas);
        }
        super.dispatchDraw(canvas);
    }

    public RecyclerView(Context context) {
        this(context, null);
    }

    public RecyclerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RecyclerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setDivider(null);
        setDividerHeight(0);
        setSelector(new ColorDrawable(Color.TRANSPARENT));
        setCacheColorHint(Color.TRANSPARENT);
        setDrawSelectorOnTop(false);
        setVerticalScrollBarEnabled(true);
        setHeaderDividersEnabled(false);
        setFooterDividersEnabled(false);
        setItemsCanFocus(true);
    }

    public void setLayoutManager(LayoutManager manager) {
        layoutManager = manager;
        if (bridge != null) bridge.notifyDataSetChanged();
    }

    public LayoutManager getLayoutManager() {
        return layoutManager;
    }

    @SuppressWarnings("unchecked")
    public void setAdapter(Adapter<? extends ViewHolder> value) {
        if (bridge != null && adapter != null) {
            adapter.unregisterObserver(bridge.relay);
        }
        adapter = (Adapter<ViewHolder>) value;
        if (adapter == null) {
            bridge = null;
            super.setAdapter((android.widget.ListAdapter) null);
            return;
        }
        bridge = new Bridge();
        adapter.registerObserver(bridge.relay);
        super.setAdapter(bridge);
    }

    public Adapter<? extends ViewHolder> getRecyclerAdapter() {
        return adapter;
    }

    public void setItemViewCacheSize(int size) {
    }

    public void setHasFixedSize(boolean value) {
    }

    public void scrollToPosition(int position) {
        setSelection(Math.max(0, position));
    }

    public void addOnScrollListener(final OnScrollListener listener) {
        if (listener == null) return;
        setOnScrollListener(new AbsListView.OnScrollListener() {
            private int lastTop;

            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                listener.onScrollStateChanged(RecyclerView.this, scrollState);
            }

            @Override
            public void onScroll(AbsListView view, int first, int visible, int total) {
                View c = view.getChildAt(0);
                int top = c == null ? 0 : c.getTop();
                listener.onScrolled(RecyclerView.this, 0, lastTop - top);
                lastTop = top;
            }
        });
    }

    public int getChildAdapterPosition(View child) {
        Object tag = child == null ? null : child.getTag(com.coara.oldbrowser.R.id.recycler_holder);
        if (tag instanceof ViewHolder) return ((ViewHolder) tag).position;
        int index = indexOfChild(child);
        return index < 0 ? NO_POSITION : getFirstVisiblePosition() + index;
    }

    public ViewHolder findViewHolderForAdapterPosition(int position) {
        for (int i = 0; i < getChildCount(); i++) {
            Object tag = getChildAt(i).getTag(com.coara.oldbrowser.R.id.recycler_holder);
            if (tag instanceof ViewHolder && ((ViewHolder) tag).position == position) return (ViewHolder) tag;
        }
        return null;
    }

    private static final class RowHolder {
        final String signature;
        final List<ViewHolder> cells = new ArrayList<>();

        RowHolder(String signature) {
            this.signature = signature;
        }
    }

    private final class Bridge extends BaseAdapter {
        final DataSetObserver relay = new DataSetObserver() {
            @Override
            public void onChanged() {
                rows = null;
                Bridge.this.notifyDataSetChanged();
            }

            @Override
            public void onInvalidated() {
                rows = null;
                Bridge.this.notifyDataSetInvalidated();
            }
        };
        private int[][] rows;
        private final Map<String, Integer> rowTypeIds = new HashMap<>();
        private int gridSpan;
        private GridLayoutManager.SpanSizeLookup lookup;

        private boolean grid() {
            return layoutManager instanceof GridLayoutManager && ((GridLayoutManager) layoutManager).getSpanCount() > 1;
        }

        private void ensureRows() {
            if (!grid()) return;
            GridLayoutManager g = (GridLayoutManager) layoutManager;
            if (rows != null && gridSpan == g.getSpanCount() && lookup == g.getSpanSizeLookup()) return;
            gridSpan = g.getSpanCount();
            lookup = g.getSpanSizeLookup();
            int count = adapter == null ? 0 : adapter.getItemCount();
            List<int[]> out = new ArrayList<>();
            int start = 0;
            int used = 0;
            for (int i = 0; i < count; i++) {
                int span = lookup == null ? 1 : Math.max(1, Math.min(gridSpan, lookup.getSpanSize(i)));
                if (used + span > gridSpan) {
                    out.add(new int[]{start, i});
                    start = i;
                    used = 0;
                }
                used += span;
            }
            if (start < count) out.add(new int[]{start, count});
            rows = out.toArray(new int[0][]);
        }

        @Override
        public int getCount() {
            if (adapter == null) return 0;
            if (grid()) {
                ensureRows();
                return rows.length;
            }
            return adapter.getItemCount();
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            if (adapter == null || grid()) return position;
            return adapter.hasStableIds() ? adapter.getItemId(position) : position;
        }

        @Override
        public boolean hasStableIds() {
            return adapter != null && !grid() && adapter.hasStableIds();
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int position) {
            return false;
        }

        @Override
        public int getViewTypeCount() {
            return grid() ? MAX_ROW_TYPES : 32;
        }

        private String signature(int[] range) {
            StringBuilder sb = new StringBuilder();
            for (int i = range[0]; i < range[1]; i++) {
                sb.append(adapter.getItemViewType(i)).append(',');
            }
            return sb.toString();
        }

        @Override
        public int getItemViewType(int position) {
            if (adapter == null) return 0;
            if (grid()) {
                ensureRows();
                String sig = signature(rows[position]);
                Integer id = rowTypeIds.get(sig);
                if (id == null) {
                    id = rowTypeIds.size() < MAX_ROW_TYPES ? rowTypeIds.size() : MAX_ROW_TYPES - 1;
                    if (rowTypeIds.size() < MAX_ROW_TYPES) rowTypeIds.put(sig, id);
                }
                return id;
            }
            int t = adapter.getItemViewType(position);
            return Math.max(0, Math.min(31, t));
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (grid()) return rowView(position, convertView, parent);
            ViewHolder holder = convertView == null ? null : (ViewHolder) convertView.getTag(com.coara.oldbrowser.R.id.recycler_holder);
            int type = adapter.getItemViewType(position);
            if (holder == null || holder.viewType != type) {
                holder = adapter.onCreateViewHolder(parent, type);
                holder.viewType = type;
                holder.itemView.setTag(com.coara.oldbrowser.R.id.recycler_holder, holder);
                ViewGroup.LayoutParams lp = holder.itemView.getLayoutParams();
                boolean margins = lp instanceof ViewGroup.MarginLayoutParams
                        && (((ViewGroup.MarginLayoutParams) lp).leftMargin != 0 || ((ViewGroup.MarginLayoutParams) lp).topMargin != 0
                        || ((ViewGroup.MarginLayoutParams) lp).rightMargin != 0 || ((ViewGroup.MarginLayoutParams) lp).bottomMargin != 0);
                if (margins) {
                    ViewGroup.MarginLayoutParams m = (ViewGroup.MarginLayoutParams) lp;
                    FrameLayout wrapper = new FrameLayout(parent.getContext());
                    wrapper.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    FrameLayout.LayoutParams inner = new FrameLayout.LayoutParams(m.width, m.height);
                    inner.setMargins(m.leftMargin, m.topMargin, m.rightMargin, m.bottomMargin);
                    wrapper.addView(holder.itemView, inner);
                    wrapper.setTag(com.coara.oldbrowser.R.id.recycler_holder, holder);
                    holder.root = wrapper;
                } else {
                    if (!(lp instanceof AbsListView.LayoutParams)) {
                        holder.itemView.setLayoutParams(lp == null
                                ? new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                                : new AbsListView.LayoutParams(lp));
                    }
                    holder.root = holder.itemView;
                }
            }
            holder.position = position;
            adapter.onBindViewHolder(holder, position);
            return holder.root;
        }

        private View rowView(int position, View convertView, ViewGroup parent) {
            int[] range = rows[position];
            String sig = signature(range);
            RowHolder rh = convertView == null ? null : (RowHolder) convertView.getTag(com.coara.oldbrowser.R.id.recycler_holder);
            LinearLayout row;
            if (rh == null || !rh.signature.equals(sig)) {
                row = new LinearLayout(parent.getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                rh = new RowHolder(sig);
                for (int i = range[0]; i < range[1]; i++) {
                    int type = adapter.getItemViewType(i);
                    ViewHolder cell = adapter.onCreateViewHolder(row, type);
                    cell.viewType = type;
                    cell.itemView.setTag(com.coara.oldbrowser.R.id.recycler_holder, cell);
                    ViewGroup.LayoutParams old = cell.itemView.getLayoutParams();
                    int span = lookup == null ? 1 : Math.max(1, Math.min(gridSpan, lookup.getSpanSize(i)));
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                            old != null && old.height > 0 ? old.height : ViewGroup.LayoutParams.WRAP_CONTENT, span);
                    if (old instanceof ViewGroup.MarginLayoutParams) {
                        ViewGroup.MarginLayoutParams m = (ViewGroup.MarginLayoutParams) old;
                        lp.setMargins(m.leftMargin, m.topMargin, m.rightMargin, m.bottomMargin);
                    }
                    row.addView(cell.itemView, lp);
                    rh.cells.add(cell);
                }
                int used = 0;
                for (int i = range[0]; i < range[1]; i++) {
                    used += lookup == null ? 1 : Math.max(1, Math.min(gridSpan, lookup.getSpanSize(i)));
                }
                if (used < gridSpan) {
                    View filler = new View(row.getContext());
                    row.addView(filler, new LinearLayout.LayoutParams(0, 1, gridSpan - used));
                }
                row.setTag(com.coara.oldbrowser.R.id.recycler_holder, rh);
            } else {
                row = (LinearLayout) convertView;
            }
            for (int i = range[0]; i < range[1]; i++) {
                ViewHolder cell = rh.cells.get(i - range[0]);
                cell.position = i;
                adapter.onBindViewHolder(cell, i);
            }
            return row;
        }
    }
}
