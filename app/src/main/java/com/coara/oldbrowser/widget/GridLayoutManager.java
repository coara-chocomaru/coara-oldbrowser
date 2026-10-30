package com.coara.oldbrowser.widget;

import android.content.Context;

public class GridLayoutManager extends RecyclerView.LayoutManager {
    public abstract static class SpanSizeLookup {
        public abstract int getSpanSize(int position);
    }

    private final int spanCount;
    private SpanSizeLookup lookup;

    public GridLayoutManager(Context context, int spanCount) {
        this.spanCount = Math.max(1, spanCount);
    }

    public int getSpanCount() {
        return spanCount;
    }

    public void setSpanSizeLookup(SpanSizeLookup value) {
        lookup = value;
    }

    public SpanSizeLookup getSpanSizeLookup() {
        return lookup;
    }
}
