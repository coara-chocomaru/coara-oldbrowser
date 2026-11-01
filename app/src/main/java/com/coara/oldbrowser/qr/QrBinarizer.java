package com.coara.oldbrowser.qr;

final class QrBinarizer {
    private static final int BLOCK = 8;
    private static final int MIN_DYNAMIC_RANGE = 24;
    private static final int MIN_DIMENSION = BLOCK * 5;

    private QrBinarizer() {
    }

    static BitGrid binarize(byte[] luma, int width, int height) {
        BitGrid fast = QrNative.binarize(luma, width, height);
        if (fast != null) {
            return fast;
        }
        return binarizeJava(luma, width, height);
    }

    static BitGrid binarizeJava(byte[] luma, int width, int height) {
        if (width < MIN_DIMENSION || height < MIN_DIMENSION) {
            return globalJava(luma, width, height);
        }
        int subWidth = (width + BLOCK - 1) / BLOCK;
        int subHeight = (height + BLOCK - 1) / BLOCK;
        int[][] blackPoints = blackPoints(luma, width, height, subWidth, subHeight);
        BitGrid grid = new BitGrid(width, height);
        int maxX = width - BLOCK;
        int maxY = height - BLOCK;
        for (int y = 0; y < subHeight; y++) {
            int yOffset = Math.min(y * BLOCK, maxY);
            int top = Math.max(2, Math.min(y, subHeight - 3));
            for (int x = 0; x < subWidth; x++) {
                int xOffset = Math.min(x * BLOCK, maxX);
                int left = Math.max(2, Math.min(x, subWidth - 3));
                int sum = 0;
                for (int dy = -2; dy <= 2; dy++) {
                    int[] row = blackPoints[top + dy];
                    for (int dx = -2; dx <= 2; dx++) {
                        sum += row[left + dx];
                    }
                }
                int threshold = sum / 25;
                for (int yy = 0, offset = yOffset * width + xOffset; yy < BLOCK; yy++, offset += width) {
                    for (int xx = 0; xx < BLOCK; xx++) {
                        if ((luma[offset + xx] & 0xFF) <= threshold) {
                            grid.bits[offset + xx] = true;
                        }
                    }
                }
            }
        }
        return grid;
    }

    private static int[][] blackPoints(byte[] luma, int width, int height, int subWidth, int subHeight) {
        int maxX = width - BLOCK;
        int maxY = height - BLOCK;
        int[][] result = new int[subHeight][subWidth];
        for (int y = 0; y < subHeight; y++) {
            int yOffset = Math.min(y * BLOCK, maxY);
            for (int x = 0; x < subWidth; x++) {
                int xOffset = Math.min(x * BLOCK, maxX);
                int sum = 0;
                int min = 0xFF;
                int max = 0;
                for (int yy = 0, offset = yOffset * width + xOffset; yy < BLOCK; yy++, offset += width) {
                    for (int xx = 0; xx < BLOCK; xx++) {
                        int pixel = luma[offset + xx] & 0xFF;
                        sum += pixel;
                        if (pixel < min) {
                            min = pixel;
                        }
                        if (pixel > max) {
                            max = pixel;
                        }
                    }
                }
                int average;
                if (max - min > MIN_DYNAMIC_RANGE) {
                    average = sum >> 6;
                } else {
                    average = min / 2;
                    if (y > 0 && x > 0) {
                        int neighbor = (result[y - 1][x] + 2 * result[y][x - 1] + result[y - 1][x - 1]) >> 2;
                        if (min < neighbor) {
                            average = neighbor;
                        }
                    }
                }
                result[y][x] = average;
            }
        }
        return result;
    }

    static BitGrid global(byte[] luma, int width, int height) {
        BitGrid fast = QrNative.global(luma, width, height);
        if (fast != null) {
            return fast;
        }
        return globalJava(luma, width, height);
    }

    static BitGrid globalJava(byte[] luma, int width, int height) {
        int[] histogram = new int[256];
        int total = width * height;
        for (int i = 0; i < total; i++) {
            histogram[luma[i] & 0xFF]++;
        }
        long sumAll = 0;
        for (int i = 0; i < 256; i++) {
            sumAll += (long) i * histogram[i];
        }
        long sumBack = 0;
        int weightBack = 0;
        double best = -1;
        int threshold = 127;
        for (int t = 0; t < 256; t++) {
            weightBack += histogram[t];
            if (weightBack == 0) {
                continue;
            }
            int weightFore = total - weightBack;
            if (weightFore == 0) {
                break;
            }
            sumBack += (long) t * histogram[t];
            double meanBack = (double) sumBack / weightBack;
            double meanFore = (double) (sumAll - sumBack) / weightFore;
            double between = (double) weightBack * weightFore * (meanBack - meanFore) * (meanBack - meanFore);
            if (between > best) {
                best = between;
                threshold = t;
            }
        }
        BitGrid grid = new BitGrid(width, height);
        for (int i = 0; i < total; i++) {
            grid.bits[i] = (luma[i] & 0xFF) <= threshold;
        }
        return grid;
    }
}
