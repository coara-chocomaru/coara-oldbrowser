package com.coara.oldbrowser.qr;

final class BitGrid {
    final int width;
    final int height;
    final boolean[] bits;

    BitGrid(int width, int height) {
        this.width = width;
        this.height = height;
        this.bits = new boolean[width * height];
    }

    boolean get(int x, int y) {
        return bits[y * width + x];
    }

    void set(int x, int y, boolean value) {
        bits[y * width + x] = value;
    }
}
