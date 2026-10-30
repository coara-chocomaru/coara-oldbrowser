package com.coara.oldbrowser.webview;

public final class NativeMessageBridge {
    private NativeMessageBridge() {
    }

    public static boolean isValidTouchpadRequest(int kind, float a, float b) {
        if (kind < 1 || kind > 6 || Float.isNaN(a) || Float.isInfinite(a)
                || Float.isNaN(b) || Float.isInfinite(b)) {
            return false;
        }
        if (kind <= 4) {
            return a >= 0f && a <= 1f && b >= 0f && b <= 1f;
        }
        return a >= 0f && a <= 65535f && a == (int) a && b == 0f;
    }
}
