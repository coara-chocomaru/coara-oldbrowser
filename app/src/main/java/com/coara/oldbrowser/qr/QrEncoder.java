package com.coara.oldbrowser.qr;

import java.nio.charset.StandardCharsets;

public final class QrEncoder {
    private static final String ALPHANUMERIC = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:";
    private static final int MODE_NUMERIC = 1;
    private static final int MODE_ALPHANUMERIC = 2;
    private static final int MODE_BYTE = 4;
    private static final int ECI_UTF8 = 26;
    private static final int MAX_VERSION = 40;

    private QrEncoder() {
    }

    public static QrCode encode(String text) throws QrException {
        if (text == null || text.isEmpty()) {
            throw new QrException("QRコードにする文字列が空です");
        }
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        int mode = selectMode(text);
        boolean eci = false;
        if (mode == MODE_BYTE) {
            for (byte b : bytes) {
                if (b < 0) {
                    eci = true;
                    break;
                }
            }
        }
        int payloadBits = payloadBits(mode, text, bytes);
        int version = 0;
        for (int v = 1; v <= MAX_VERSION; v++) {
            int needed = (eci ? 12 : 0) + 4 + QrSpec.countBits(mode, v) + payloadBits;
            if (needed <= QrSpec.dataCodewords(QrSpec.LEVEL_L, v) * 8 && fitsCount(mode, v, text, bytes)) {
                version = v;
                break;
            }
        }
        if (version == 0) {
            throw new QrException("内容がQRコードの最大容量(" + maxBytes() + " bytes)を超えています");
        }
        int capacityBits = QrSpec.dataCodewords(QrSpec.LEVEL_L, version) * 8;
        BitBuffer bits = new BitBuffer(capacityBits + 16);
        if (eci) {
            bits.append(7, 4);
            bits.append(ECI_UTF8, 8);
        }
        bits.append(mode, 4);
        int count = mode == MODE_BYTE ? bytes.length : text.length();
        bits.append(count, QrSpec.countBits(mode, version));
        appendPayload(bits, mode, text, bytes);
        bits.append(0, Math.min(4, capacityBits - bits.length()));
        bits.append(0, (8 - bits.length() % 8) % 8);
        for (int pad = 0xEC; bits.length() < capacityBits; pad ^= 0xEC ^ 0x11) {
            bits.append(pad, 8);
        }
        int[] data = new int[capacityBits / 8];
        for (int i = 0; i < bits.length(); i++) {
            if (bits.get(i)) {
                data[i >>> 3] |= 1 << (7 - (i & 7));
            }
        }
        int[] codewords = addEccAndInterleave(data, version);
        return build(version, codewords);
    }

    private static int maxBytes() {
        return QrSpec.dataCodewords(QrSpec.LEVEL_L, MAX_VERSION) - 3;
    }

    private static boolean fitsCount(int mode, int version, String text, byte[] bytes) {
        int count = mode == MODE_BYTE ? bytes.length : text.length();
        return count < (1 << QrSpec.countBits(mode, version));
    }

    private static int selectMode(String text) {
        boolean numeric = true;
        boolean alnum = true;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < '0' || c > '9') {
                numeric = false;
            }
            if (ALPHANUMERIC.indexOf(c) < 0) {
                alnum = false;
            }
            if (!numeric && !alnum) {
                return MODE_BYTE;
            }
        }
        return numeric ? MODE_NUMERIC : MODE_ALPHANUMERIC;
    }

    private static int payloadBits(int mode, String text, byte[] bytes) {
        int n = text.length();
        switch (mode) {
            case MODE_NUMERIC:
                return n / 3 * 10 + (n % 3 == 0 ? 0 : n % 3 * 3 + 1);
            case MODE_ALPHANUMERIC:
                return n / 2 * 11 + (n % 2) * 6;
            default:
                return bytes.length * 8;
        }
    }

    private static void appendPayload(BitBuffer bits, int mode, String text, byte[] bytes) {
        if (mode == MODE_NUMERIC) {
            for (int i = 0; i < text.length(); ) {
                int take = Math.min(3, text.length() - i);
                bits.append(Integer.parseInt(text.substring(i, i + take)), take * 3 + 1);
                i += take;
            }
        } else if (mode == MODE_ALPHANUMERIC) {
            int i = 0;
            for (; i + 1 < text.length(); i += 2) {
                bits.append(ALPHANUMERIC.indexOf(text.charAt(i)) * 45 + ALPHANUMERIC.indexOf(text.charAt(i + 1)), 11);
            }
            if (i < text.length()) {
                bits.append(ALPHANUMERIC.indexOf(text.charAt(i)), 6);
            }
        } else {
            for (byte b : bytes) {
                bits.append(b & 0xFF, 8);
            }
        }
    }

    private static int[] addEccAndInterleave(int[] data, int version) {
        int blocks = QrSpec.numBlocks(QrSpec.LEVEL_L, version);
        int eccLen = QrSpec.eccPerBlock(QrSpec.LEVEL_L, version);
        int raw = QrSpec.totalCodewords(version);
        int shortBlocks = blocks - raw % blocks;
        int shortLen = raw / blocks;
        int[] generator = QrReedSolomon.generator(eccLen);
        int[][] dataBlocks = new int[blocks][];
        int[][] eccBlocks = new int[blocks][];
        int offset = 0;
        for (int i = 0; i < blocks; i++) {
            int dataLen = shortLen - eccLen + (i < shortBlocks ? 0 : 1);
            dataBlocks[i] = new int[dataLen];
            System.arraycopy(data, offset, dataBlocks[i], 0, dataLen);
            eccBlocks[i] = QrReedSolomon.remainder(data, offset, dataLen, generator);
            offset += dataLen;
        }
        int[] result = new int[raw];
        int k = 0;
        for (int i = 0; i < shortLen - eccLen + 1; i++) {
            for (int j = 0; j < blocks; j++) {
                if (i < dataBlocks[j].length) {
                    result[k++] = dataBlocks[j][i];
                }
            }
        }
        for (int i = 0; i < eccLen; i++) {
            for (int j = 0; j < blocks; j++) {
                result[k++] = eccBlocks[j][i];
            }
        }
        return result;
    }

    private static QrCode build(int version, int[] codewords) {
        int size = QrSpec.size(version);
        boolean[] modules = new boolean[size * size];
        boolean[] function = QrSpec.functionMask(version);
        drawFunctionPatterns(modules, size, version);
        placeCodewords(modules, function, size, codewords);
        int bestMask = 0;
        int bestPenalty = Integer.MAX_VALUE;
        for (int mask = 0; mask < 8; mask++) {
            applyMask(modules, function, size, mask);
            drawFormat(modules, size, mask);
            int penalty = penalty(modules, size);
            if (penalty < bestPenalty) {
                bestPenalty = penalty;
                bestMask = mask;
            }
            applyMask(modules, function, size, mask);
        }
        applyMask(modules, function, size, bestMask);
        drawFormat(modules, size, bestMask);
        return new QrCode(size, modules);
    }

    private static void set(boolean[] modules, int size, int x, int y, boolean value) {
        modules[y * size + x] = value;
    }

    private static void drawFunctionPatterns(boolean[] modules, int size, int version) {
        for (int i = 0; i < size; i++) {
            set(modules, size, 6, i, i % 2 == 0);
            set(modules, size, i, 6, i % 2 == 0);
        }
        drawFinder(modules, size, 3, 3);
        drawFinder(modules, size, size - 4, 3);
        drawFinder(modules, size, 3, size - 4);
        int[] align = QrSpec.alignmentPositions(version);
        int n = align.length;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if ((i == 0 && j == 0) || (i == 0 && j == n - 1) || (i == n - 1 && j == 0)) {
                    continue;
                }
                drawAlignment(modules, size, align[i], align[j]);
            }
        }
        drawFormat(modules, size, 0);
        if (version >= 7) {
            int bits = QrSpec.versionBits(version);
            for (int i = 0; i < 18; i++) {
                boolean bit = ((bits >>> i) & 1) != 0;
                int a = size - 11 + i % 3;
                int b = i / 3;
                set(modules, size, a, b, bit);
                set(modules, size, b, a, bit);
            }
        }
    }

    private static void drawFinder(boolean[] modules, int size, int cx, int cy) {
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                int dist = Math.max(Math.abs(dx), Math.abs(dy));
                int x = cx + dx;
                int y = cy + dy;
                if (x >= 0 && x < size && y >= 0 && y < size) {
                    set(modules, size, x, y, dist != 2 && dist != 4);
                }
            }
        }
    }

    private static void drawAlignment(boolean[] modules, int size, int cx, int cy) {
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                set(modules, size, cx + dx, cy + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
            }
        }
    }

    private static void drawFormat(boolean[] modules, int size, int mask) {
        int bits = QrSpec.formatBits(QrSpec.LEVEL_L, mask);
        for (int i = 0; i <= 5; i++) {
            set(modules, size, 8, i, ((bits >>> i) & 1) != 0);
        }
        set(modules, size, 8, 7, ((bits >>> 6) & 1) != 0);
        set(modules, size, 8, 8, ((bits >>> 7) & 1) != 0);
        set(modules, size, 7, 8, ((bits >>> 8) & 1) != 0);
        for (int i = 9; i < 15; i++) {
            set(modules, size, 14 - i, 8, ((bits >>> i) & 1) != 0);
        }
        for (int i = 0; i < 8; i++) {
            set(modules, size, size - 1 - i, 8, ((bits >>> i) & 1) != 0);
        }
        for (int i = 8; i < 15; i++) {
            set(modules, size, 8, size - 15 + i, ((bits >>> i) & 1) != 0);
        }
        set(modules, size, 8, size - 8, true);
    }

    private static void placeCodewords(boolean[] modules, boolean[] function, int size, int[] codewords) {
        int bit = 0;
        int total = codewords.length * 8;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) {
                right = 5;
            }
            for (int vert = 0; vert < size; vert++) {
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    boolean upward = ((right + 1) & 2) == 0;
                    int y = upward ? size - 1 - vert : vert;
                    if (!function[y * size + x] && bit < total) {
                        modules[y * size + x] = ((codewords[bit >>> 3] >>> (7 - (bit & 7))) & 1) != 0;
                        bit++;
                    }
                }
            }
        }
    }

    private static void applyMask(boolean[] modules, boolean[] function, int size, int mask) {
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (!function[y * size + x] && QrSpec.maskBit(mask, x, y)) {
                    modules[y * size + x] ^= true;
                }
            }
        }
    }

    private static int penalty(boolean[] modules, int size) {
        int result = 0;
        for (int pass = 0; pass < 2; pass++) {
            for (int a = 0; a < size; a++) {
                int run = 0;
                boolean last = false;
                int window = 0;
                for (int b = 0; b < size; b++) {
                    boolean color = pass == 0 ? modules[a * size + b] : modules[b * size + a];
                    if (b > 0 && color == last) {
                        run++;
                        if (run == 5) {
                            result += 3;
                        } else if (run > 5) {
                            result++;
                        }
                    } else {
                        run = 1;
                        last = color;
                    }
                    window = ((window << 1) & 0x7FF) | (color ? 1 : 0);
                    if (b >= 10 && (window == 0x05D || window == 0x5D0)) {
                        result += 40;
                    }
                }
            }
        }
        for (int y = 0; y < size - 1; y++) {
            for (int x = 0; x < size - 1; x++) {
                boolean c = modules[y * size + x];
                if (c == modules[y * size + x + 1] && c == modules[(y + 1) * size + x] && c == modules[(y + 1) * size + x + 1]) {
                    result += 3;
                }
            }
        }
        int dark = 0;
        for (boolean m : modules) {
            if (m) {
                dark++;
            }
        }
        int total = size * size;
        int k = (Math.abs(dark * 20 - total * 10) + total - 1) / total - 1;
        return result + k * 10;
    }

    private static final class BitBuffer {
        private boolean[] bits;
        private int length;

        BitBuffer(int capacity) {
            bits = new boolean[Math.max(capacity, 16)];
        }

        void append(int value, int count) {
            for (int i = count - 1; i >= 0; i--) {
                if (length == bits.length) {
                    boolean[] grown = new boolean[bits.length * 2];
                    System.arraycopy(bits, 0, grown, 0, length);
                    bits = grown;
                }
                bits[length++] = ((value >>> i) & 1) != 0;
            }
        }

        int length() {
            return length;
        }

        boolean get(int index) {
            return bits[index];
        }
    }
}
