package com.coara.oldbrowser.qr;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class QrDecoder {
    private static final String ALPHANUMERIC = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:";

    private QrDecoder() {
    }

    public static String decode(byte[] luma, int width, int height) {
        if (luma == null || width < 21 || height < 21 || luma.length < width * height) {
            return null;
        }
        String text = attempt(QrBinarizer.binarize(luma, width, height));
        if (text != null) {
            return text;
        }
        byte[] smooth = smooth(luma, width, height);
        text = attempt(QrBinarizer.binarize(smooth, width, height));
        if (text != null) {
            return text;
        }
        text = attempt(QrBinarizer.global(smooth, width, height));
        if (text != null) {
            return text;
        }
        byte[] inverted = QrNative.invert(luma, width, height);
        if (inverted == null) {
            inverted = new byte[width * height];
            for (int i = 0; i < inverted.length; i++) {
                inverted[i] = (byte) (255 - (luma[i] & 0xFF));
            }
        }
        text = attempt(QrBinarizer.binarize(inverted, width, height));
        if (text != null) {
            return text;
        }
        return attempt(QrBinarizer.binarize(smooth(inverted, width, height), width, height));
    }

    static byte[] smooth(byte[] luma, int width, int height) {
        byte[] fast = QrNative.smooth(luma, width, height);
        if (fast != null) {
            return fast;
        }
        return smoothJava(luma, width, height);
    }

    static byte[] smoothJava(byte[] luma, int width, int height) {
        byte[] tmp = new byte[width * height];
        byte[] out = new byte[width * height];
        for (int y = 0; y < height; y++) {
            int row = y * width;
            for (int x = 0; x < width; x++) {
                int l = x > 0 ? x - 1 : x;
                int r = x < width - 1 ? x + 1 : x;
                tmp[row + x] = (byte) (((luma[row + l] & 0xFF) + 2 * (luma[row + x] & 0xFF) + (luma[row + r] & 0xFF) + 2) >> 2);
            }
        }
        for (int y = 0; y < height; y++) {
            int up = (y > 0 ? y - 1 : y) * width;
            int mid = y * width;
            int down = (y < height - 1 ? y + 1 : y) * width;
            for (int x = 0; x < width; x++) {
                out[mid + x] = (byte) (((tmp[up + x] & 0xFF) + 2 * (tmp[mid + x] & 0xFF) + (tmp[down + x] & 0xFF) + 2) >> 2);
            }
        }
        return out;
    }

    private static String attempt(BitGrid binary) {
        QrDetector detector = new QrDetector(binary);
        List<QrDetector.Candidate> candidates = detector.candidates();
        int tried = 0;
        for (QrDetector.Candidate candidate : candidates) {
            if (tried++ >= 4) {
                break;
            }
            double module = detector.estimateModuleSize(candidate);
            if (module < 1.0) {
                continue;
            }
            for (int dimension : detector.dimensionGuesses(candidate, module)) {
                for (int mode = 0; mode < (dimension >= 25 ? 3 : 1); mode++) {
                    BitGrid sampled = detector.sample(candidate, dimension, mode);
                    if (sampled == null) {
                        continue;
                    }
                    String text = decodeGrid(sampled);
                    if (text == null) {
                        text = decodeGrid(transpose(sampled));
                    }
                    if (text != null) {
                        return text;
                    }
                }
            }
        }
        return null;
    }

    private static BitGrid transpose(BitGrid g) {
        BitGrid t = new BitGrid(g.height, g.width);
        for (int y = 0; y < g.height; y++) {
            for (int x = 0; x < g.width; x++) {
                t.set(y, x, g.get(x, y));
            }
        }
        return t;
    }

    private static int hamming(int a, int b) {
        return Integer.bitCount(a ^ b);
    }

    private static int[] bestFormat(int observed) {
        int bestDistance = 99;
        int bestLevel = -1;
        int bestMask = -1;
        for (int level = 0; level < 4; level++) {
            for (int mask = 0; mask < 8; mask++) {
                int d = hamming(observed, QrSpec.formatBits(level, mask));
                if (d < bestDistance) {
                    bestDistance = d;
                    bestLevel = level;
                    bestMask = mask;
                }
            }
        }
        return new int[]{bestDistance, bestLevel, bestMask};
    }

    private static String decodeGrid(BitGrid g) {
        int size = g.width;
        if (size < 21 || (size - 17) % 4 != 0) {
            return null;
        }
        int version = (size - 17) / 4;
        int first = 0;
        for (int i = 0; i <= 5; i++) {
            first |= (g.get(8, i) ? 1 : 0) << i;
        }
        first |= (g.get(8, 7) ? 1 : 0) << 6;
        first |= (g.get(8, 8) ? 1 : 0) << 7;
        first |= (g.get(7, 8) ? 1 : 0) << 8;
        for (int i = 9; i < 15; i++) {
            first |= (g.get(14 - i, 8) ? 1 : 0) << i;
        }
        int second = 0;
        for (int i = 0; i < 8; i++) {
            second |= (g.get(size - 1 - i, 8) ? 1 : 0) << i;
        }
        for (int i = 8; i < 15; i++) {
            second |= (g.get(8, size - 15 + i) ? 1 : 0) << i;
        }
        int[] a = bestFormat(first);
        int[] b = bestFormat(second);
        int[] format = a[0] <= b[0] ? a : b;
        if (format[0] > 3) {
            return null;
        }
        if (version >= 7) {
            int v1 = 0;
            int v2 = 0;
            for (int i = 0; i < 18; i++) {
                v1 |= (g.get(size - 11 + i % 3, i / 3) ? 1 : 0) << i;
                v2 |= (g.get(i / 3, size - 11 + i % 3) ? 1 : 0) << i;
            }
            if (hamming(v1, QrSpec.versionBits(version)) > 3 && hamming(v2, QrSpec.versionBits(version)) > 3) {
                return null;
            }
        }
        int level = format[1];
        int mask = format[2];
        boolean[] function = QrSpec.functionMask(version);
        int total = QrSpec.totalCodewords(version);
        int[] raw = new int[total];
        int bit = 0;
        int limit = total * 8;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) {
                right = 5;
            }
            for (int vert = 0; vert < size; vert++) {
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    boolean upward = ((right + 1) & 2) == 0;
                    int y = upward ? size - 1 - vert : vert;
                    if (!function[y * size + x] && bit < limit) {
                        boolean value = g.get(x, y) ^ QrSpec.maskBit(mask, x, y);
                        if (value) {
                            raw[bit >>> 3] |= 1 << (7 - (bit & 7));
                        }
                        bit++;
                    }
                }
            }
        }
        int blocks = QrSpec.numBlocks(level, version);
        int eccLen = QrSpec.eccPerBlock(level, version);
        int shortBlocks = blocks - total % blocks;
        int shortLen = total / blocks;
        int[][] blockData = new int[blocks][];
        for (int i = 0; i < blocks; i++) {
            blockData[i] = new int[shortLen + (i < shortBlocks ? 0 : 1)];
        }
        int k = 0;
        int dataRows = shortLen - eccLen + 1;
        for (int i = 0; i < dataRows; i++) {
            for (int j = 0; j < blocks; j++) {
                if (i < blockData[j].length - eccLen) {
                    blockData[j][i] = raw[k++];
                }
            }
        }
        for (int i = 0; i < eccLen; i++) {
            for (int j = 0; j < blocks; j++) {
                blockData[j][blockData[j].length - eccLen + i] = raw[k++];
            }
        }
        int dataTotal = QrSpec.dataCodewords(level, version);
        byte[] data = new byte[dataTotal];
        int w = 0;
        for (int i = 0; i < blocks; i++) {
            if (!QrReedSolomon.correct(blockData[i], eccLen)) {
                return null;
            }
            int dataLen = blockData[i].length - eccLen;
            for (int j = 0; j < dataLen; j++) {
                data[w++] = (byte) blockData[i][j];
            }
        }
        return parse(data, version);
    }

    private static final class Bits {
        private final byte[] data;
        private int position;

        Bits(byte[] data) {
            this.data = data;
        }

        int remaining() {
            return data.length * 8 - position;
        }

        int read(int count) {
            int value = 0;
            for (int i = 0; i < count; i++) {
                int b = (data[position >>> 3] >>> (7 - (position & 7))) & 1;
                value = (value << 1) | b;
                position++;
            }
            return value;
        }
    }

    private static String parse(byte[] data, int version) {
        Bits bits = new Bits(data);
        StringBuilder out = new StringBuilder();
        java.io.ByteArrayOutputStream pending = new java.io.ByteArrayOutputStream();
        String charset = null;
        boolean any = false;
        while (bits.remaining() >= 4) {
            int mode = bits.read(4);
            if (mode == 0) {
                break;
            }
            switch (mode) {
                case 1: {
                    flush(out, pending, charset);
                    int count = readCount(bits, mode, version);
                    if (count < 0) {
                        return null;
                    }
                    while (count >= 3) {
                        if (bits.remaining() < 10) {
                            return null;
                        }
                        int v = bits.read(10);
                        if (v >= 1000) {
                            return null;
                        }
                        out.append((char) ('0' + v / 100)).append((char) ('0' + v / 10 % 10)).append((char) ('0' + v % 10));
                        count -= 3;
                    }
                    if (count == 2) {
                        if (bits.remaining() < 7) {
                            return null;
                        }
                        int v = bits.read(7);
                        if (v >= 100) {
                            return null;
                        }
                        out.append((char) ('0' + v / 10)).append((char) ('0' + v % 10));
                    } else if (count == 1) {
                        if (bits.remaining() < 4) {
                            return null;
                        }
                        int v = bits.read(4);
                        if (v >= 10) {
                            return null;
                        }
                        out.append((char) ('0' + v));
                    }
                    any = true;
                    break;
                }
                case 2: {
                    flush(out, pending, charset);
                    int count = readCount(bits, mode, version);
                    if (count < 0) {
                        return null;
                    }
                    while (count >= 2) {
                        if (bits.remaining() < 11) {
                            return null;
                        }
                        int v = bits.read(11);
                        if (v >= 45 * 45) {
                            return null;
                        }
                        out.append(ALPHANUMERIC.charAt(v / 45)).append(ALPHANUMERIC.charAt(v % 45));
                        count -= 2;
                    }
                    if (count == 1) {
                        if (bits.remaining() < 6) {
                            return null;
                        }
                        int v = bits.read(6);
                        if (v >= 45) {
                            return null;
                        }
                        out.append(ALPHANUMERIC.charAt(v));
                    }
                    any = true;
                    break;
                }
                case 4: {
                    int count = readCount(bits, mode, version);
                    if (count < 0 || bits.remaining() < count * 8) {
                        return null;
                    }
                    for (int i = 0; i < count; i++) {
                        pending.write(bits.read(8));
                    }
                    any = true;
                    break;
                }
                case 8: {
                    flush(out, pending, charset);
                    int count = readCount(bits, mode, version);
                    if (count < 0 || bits.remaining() < count * 13) {
                        return null;
                    }
                    byte[] sjis = new byte[count * 2];
                    for (int i = 0; i < count; i++) {
                        int v = bits.read(13);
                        int assembled = (v / 0xC0) << 8 | (v % 0xC0);
                        assembled += assembled < 0x1F00 ? 0x8140 : 0xC140;
                        sjis[i * 2] = (byte) (assembled >> 8);
                        sjis[i * 2 + 1] = (byte) assembled;
                    }
                    try {
                        out.append(new String(sjis, "Shift_JIS"));
                    } catch (java.io.UnsupportedEncodingException e) {
                        return null;
                    }
                    any = true;
                    break;
                }
                case 7: {
                    if (bits.remaining() < 8) {
                        return null;
                    }
                    flush(out, pending, charset);
                    int first = bits.read(8);
                    int designator;
                    if ((first & 0x80) == 0) {
                        designator = first;
                    } else if ((first & 0xC0) == 0x80) {
                        if (bits.remaining() < 8) {
                            return null;
                        }
                        designator = ((first & 0x3F) << 8) | bits.read(8);
                    } else {
                        if (bits.remaining() < 16) {
                            return null;
                        }
                        designator = ((first & 0x1F) << 16) | bits.read(16);
                    }
                    charset = eciCharset(designator);
                    break;
                }
                case 3:
                    if (bits.remaining() < 16) {
                        return null;
                    }
                    bits.read(16);
                    break;
                case 5:
                    break;
                case 9:
                    if (bits.remaining() < 8) {
                        return null;
                    }
                    bits.read(8);
                    break;
                default:
                    return null;
            }
        }
        flush(out, pending, charset);
        return any ? out.toString() : null;
    }

    private static int readCount(Bits bits, int mode, int version) {
        int n = QrSpec.countBits(mode, version);
        if (bits.remaining() < n) {
            return -1;
        }
        return bits.read(n);
    }

    private static String eciCharset(int designator) {
        switch (designator) {
            case 1:
            case 3:
                return "ISO-8859-1";
            case 20:
                return "Shift_JIS";
            case 26:
                return "UTF-8";
            case 27:
            case 170:
                return "US-ASCII";
            case 28:
                return "Big5";
            case 29:
                return "GB2312";
            case 30:
                return "EUC-KR";
            case 4:
                return "ISO-8859-2";
            case 5:
                return "ISO-8859-3";
            case 6:
                return "ISO-8859-4";
            case 7:
                return "ISO-8859-5";
            case 8:
                return "ISO-8859-6";
            case 9:
                return "ISO-8859-7";
            case 10:
                return "ISO-8859-8";
            case 11:
                return "ISO-8859-9";
            case 13:
                return "ISO-8859-11";
            case 15:
                return "ISO-8859-13";
            case 17:
                return "ISO-8859-15";
            case 21:
                return "windows-1250";
            case 22:
                return "windows-1251";
            case 23:
                return "windows-1252";
            case 24:
                return "windows-1256";
            case 25:
                return "UTF-16BE";
            default:
                return null;
        }
    }

    private static void flush(StringBuilder out, java.io.ByteArrayOutputStream pending, String charset) {
        if (pending.size() == 0) {
            return;
        }
        byte[] bytes = pending.toByteArray();
        pending.reset();
        if (charset != null) {
            try {
                out.append(new String(bytes, charset));
                return;
            } catch (java.io.UnsupportedEncodingException ignored) {
            }
        }
        String decoded = strict(bytes, StandardCharsets.UTF_8);
        if (decoded == null) {
            try {
                decoded = strict(bytes, Charset.forName("Shift_JIS"));
            } catch (RuntimeException ignored) {
            }
        }
        if (decoded == null) {
            decoded = new String(bytes, StandardCharsets.ISO_8859_1);
        }
        out.append(decoded);
    }

    private static String strict(byte[] bytes, Charset charset) {
        try {
            return charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }
}
