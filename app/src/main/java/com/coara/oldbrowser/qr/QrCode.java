package com.coara.oldbrowser.qr;

public final class QrCode {
    private final int size;
    private final boolean[] modules;

    QrCode(int size, boolean[] modules) {
        this.size = size;
        this.modules = modules;
    }

    public int size() {
        return size;
    }

    public boolean get(int x, int y) {
        return x >= 0 && y >= 0 && x < size && y < size && modules[y * size + x];
    }
}
