package com.coara.oldbrowser.qr;

final class QrNative {
    private static final boolean AVAILABLE = load();

    private QrNative() {
    }

    private static boolean load() {
        try {
            System.loadLibrary("coarafast");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean available() {
        return AVAILABLE;
    }

    static BitGrid binarize(byte[] luma, int width, int height) {
        if (!AVAILABLE || !valid(luma, width, height)) {
            return null;
        }
        try {
            BitGrid grid = new BitGrid(width, height);
            return nativeBinarize(luma, width, height, grid.bits) ? grid : null;
        } catch (Throwable t) {
            return null;
        }
    }

    static BitGrid global(byte[] luma, int width, int height) {
        if (!AVAILABLE || !valid(luma, width, height)) {
            return null;
        }
        try {
            BitGrid grid = new BitGrid(width, height);
            return nativeGlobal(luma, width, height, grid.bits) ? grid : null;
        } catch (Throwable t) {
            return null;
        }
    }

    static byte[] smooth(byte[] luma, int width, int height) {
        if (!AVAILABLE || !valid(luma, width, height)) {
            return null;
        }
        try {
            byte[] out = new byte[width * height];
            return nativeSmooth(luma, width, height, out) ? out : null;
        } catch (Throwable t) {
            return null;
        }
    }

    static byte[] invert(byte[] luma, int width, int height) {
        if (!AVAILABLE || !valid(luma, width, height)) {
            return null;
        }
        try {
            byte[] out = new byte[width * height];
            return nativeInvert(luma, width, height, out) ? out : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean valid(byte[] luma, int width, int height) {
        return luma != null && width > 0 && height > 0
            && (long) width * height <= 0x3FFFFFFFL && luma.length >= width * height;
    }

    private static native boolean nativeBinarize(byte[] luma, int width, int height, boolean[] out);

    private static native boolean nativeGlobal(byte[] luma, int width, int height, boolean[] out);

    private static native boolean nativeSmooth(byte[] luma, int width, int height, byte[] out);

    private static native boolean nativeInvert(byte[] luma, int width, int height, byte[] out);
}
