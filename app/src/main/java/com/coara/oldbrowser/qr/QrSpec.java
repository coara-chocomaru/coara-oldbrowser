package com.coara.oldbrowser.qr;

final class QrSpec {
    static final int LEVEL_M = 0;
    static final int LEVEL_L = 1;
    static final int LEVEL_H = 2;
    static final int LEVEL_Q = 3;

    private static final int[][] ECC_PER_BLOCK = {
            {10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28},
            {7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30},
            {17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30, 24, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30},
            {13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30}
    };

    private static final int[][] NUM_BLOCKS = {
            {1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49},
            {1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 6, 7, 8, 8, 9, 9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25},
            {1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81},
            {1, 1, 2, 2, 4, 4, 6, 6, 8, 8, 8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68}
    };

    private static final int[] FORMAT_LEVEL_BITS = {0, 1, 2, 3};

    private QrSpec() {
    }

    static int size(int version) {
        return version * 4 + 17;
    }

    static int eccPerBlock(int level, int version) {
        return ECC_PER_BLOCK[level][version - 1];
    }

    static int numBlocks(int level, int version) {
        return NUM_BLOCKS[level][version - 1];
    }

    static int rawDataModules(int version) {
        int size = size(version);
        int result = size * size;
        result -= 8 * 8 * 3;
        result -= 15 * 2 + 1;
        result -= (size - 16) * 2;
        if (version >= 2) {
            int numAlign = version / 7 + 2;
            result -= (numAlign - 1) * (numAlign - 1) * 25;
            result -= (numAlign - 2) * 2 * 20;
            if (version >= 7) {
                result -= 6 * 3 * 2;
            }
        }
        return result;
    }

    static int totalCodewords(int version) {
        return rawDataModules(version) / 8;
    }

    static int dataCodewords(int level, int version) {
        return totalCodewords(version) - eccPerBlock(level, version) * numBlocks(level, version);
    }

    static int[] alignmentPositions(int version) {
        if (version == 1) {
            return new int[0];
        }
        int numAlign = version / 7 + 2;
        int step = version == 32 ? 26 : (version * 4 + numAlign * 2 + 1) / (numAlign * 2 - 2) * 2;
        int[] result = new int[numAlign];
        result[0] = 6;
        for (int i = numAlign - 1, pos = size(version) - 7; i >= 1; i--, pos -= step) {
            result[i] = pos;
        }
        return result;
    }

    static boolean[] functionMask(int version) {
        int size = size(version);
        boolean[] mask = new boolean[size * size];
        fillRect(mask, size, 0, 0, 9, 9);
        fillRect(mask, size, size - 8, 0, 8, 9);
        fillRect(mask, size, 0, size - 8, 9, 8);
        for (int i = 0; i < size; i++) {
            mask[6 * size + i] = true;
            mask[i * size + 6] = true;
        }
        int[] align = alignmentPositions(version);
        int n = align.length;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if ((i == 0 && j == 0) || (i == 0 && j == n - 1) || (i == n - 1 && j == 0)) {
                    continue;
                }
                fillRect(mask, size, align[i] - 2, align[j] - 2, 5, 5);
            }
        }
        if (version >= 7) {
            fillRect(mask, size, size - 11, 0, 3, 6);
            fillRect(mask, size, 0, size - 11, 6, 3);
        }
        return mask;
    }

    private static void fillRect(boolean[] mask, int size, int x, int y, int w, int h) {
        for (int dy = 0; dy < h; dy++) {
            for (int dx = 0; dx < w; dx++) {
                mask[(y + dy) * size + x + dx] = true;
            }
        }
    }

    static boolean maskBit(int mask, int x, int y) {
        switch (mask) {
            case 0:
                return (x + y) % 2 == 0;
            case 1:
                return y % 2 == 0;
            case 2:
                return x % 3 == 0;
            case 3:
                return (x + y) % 3 == 0;
            case 4:
                return (x / 3 + y / 2) % 2 == 0;
            case 5:
                return x * y % 2 + x * y % 3 == 0;
            case 6:
                return (x * y % 2 + x * y % 3) % 2 == 0;
            default:
                return ((x + y) % 2 + x * y % 3) % 2 == 0;
        }
    }

    static int formatBits(int level, int mask) {
        int data = FORMAT_LEVEL_BITS[level] << 3 | mask;
        int rem = data;
        for (int i = 0; i < 10; i++) {
            rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
        }
        return (data << 10 | rem) ^ 0x5412;
    }

    static int versionBits(int version) {
        int rem = version;
        for (int i = 0; i < 12; i++) {
            rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
        }
        return version << 12 | rem;
    }

    static int countBits(int mode, int version) {
        int tier = version <= 9 ? 0 : version <= 26 ? 1 : 2;
        switch (mode) {
            case 1:
                return new int[]{10, 12, 14}[tier];
            case 2:
                return new int[]{9, 11, 13}[tier];
            case 4:
                return new int[]{8, 16, 16}[tier];
            default:
                return new int[]{8, 10, 12}[tier];
        }
    }
}
