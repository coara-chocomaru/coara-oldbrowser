package com.coara.oldbrowser.qr;

final class QrReedSolomon {
    private static final int[] EXP = new int[512];
    private static final int[] LOG = new int[256];

    static {
        int x = 1;
        for (int i = 0; i < 255; i++) {
            EXP[i] = x;
            LOG[x] = i;
            x <<= 1;
            if (x >= 256) {
                x ^= 0x11D;
            }
        }
        for (int i = 255; i < 512; i++) {
            EXP[i] = EXP[i - 255];
        }
    }

    private QrReedSolomon() {
    }

    static int multiply(int a, int b) {
        if (a == 0 || b == 0) {
            return 0;
        }
        return EXP[LOG[a] + LOG[b]];
    }

    private static int divide(int a, int b) {
        if (a == 0) {
            return 0;
        }
        return EXP[LOG[a] + 255 - LOG[b]];
    }

    static int[] generator(int degree) {
        int[] result = new int[degree];
        result[degree - 1] = 1;
        int root = 1;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < degree; j++) {
                result[j] = multiply(result[j], root);
                if (j + 1 < degree) {
                    result[j] ^= result[j + 1];
                }
            }
            root = multiply(root, 2);
        }
        return result;
    }

    static int[] remainder(int[] data, int offset, int length, int[] generator) {
        int degree = generator.length;
        int[] result = new int[degree];
        for (int i = 0; i < length; i++) {
            int factor = data[offset + i] ^ result[0];
            System.arraycopy(result, 1, result, 0, degree - 1);
            result[degree - 1] = 0;
            for (int j = 0; j < degree; j++) {
                result[j] ^= multiply(generator[j], factor);
            }
        }
        return result;
    }

    static boolean correct(int[] received, int eccLength) {
        int n = received.length;
        int[] syndromes = new int[eccLength];
        boolean clean = true;
        for (int i = 0; i < eccLength; i++) {
            int s = 0;
            int alpha = EXP[i];
            for (int j = 0; j < n; j++) {
                s = multiply(s, alpha) ^ received[j];
            }
            syndromes[i] = s;
            if (s != 0) {
                clean = false;
            }
        }
        if (clean) {
            return true;
        }
        int[] sigma = new int[eccLength + 1];
        int[] previous = new int[eccLength + 1];
        sigma[0] = 1;
        previous[0] = 1;
        int degree = 0;
        int shift = 1;
        int lastDiscrepancy = 1;
        for (int step = 0; step < eccLength; step++) {
            int discrepancy = syndromes[step];
            for (int i = 1; i <= degree; i++) {
                discrepancy ^= multiply(sigma[i], syndromes[step - i]);
            }
            if (discrepancy == 0) {
                shift++;
                continue;
            }
            int[] snapshot = sigma.clone();
            int scale = divide(discrepancy, lastDiscrepancy);
            for (int i = 0; i + shift <= eccLength; i++) {
                sigma[i + shift] ^= multiply(scale, previous[i]);
            }
            if (2 * degree <= step) {
                degree = step + 1 - degree;
                previous = snapshot;
                lastDiscrepancy = discrepancy;
                shift = 1;
            } else {
                shift++;
            }
        }
        if (degree == 0 || degree * 2 > eccLength) {
            return false;
        }
        int[] positions = new int[degree];
        int found = 0;
        for (int i = 0; i < n; i++) {
            int xInverse = EXP[(255 - ((n - 1 - i) % 255)) % 255];
            int value = 0;
            int power = 1;
            for (int k = 0; k <= degree; k++) {
                value ^= multiply(sigma[k], power);
                power = multiply(power, xInverse);
            }
            if (value == 0) {
                if (found >= degree) {
                    return false;
                }
                positions[found++] = i;
            }
        }
        if (found != degree) {
            return false;
        }
        int[] omega = new int[degree];
        for (int i = 0; i < degree; i++) {
            int value = 0;
            for (int j = 0; j <= i; j++) {
                value ^= multiply(sigma[j], syndromes[i - j]);
            }
            omega[i] = value;
        }
        for (int k = 0; k < degree; k++) {
            int location = n - 1 - positions[k];
            int xInverse = EXP[(255 - location % 255) % 255];
            int numerator = 0;
            int power = 1;
            for (int i = 0; i < degree; i++) {
                numerator ^= multiply(omega[i], power);
                power = multiply(power, xInverse);
            }
            int denominator = 0;
            for (int i = 1; i <= degree; i += 2) {
                int term = sigma[i];
                int p = 1;
                for (int j = 0; j < i - 1; j++) {
                    p = multiply(p, xInverse);
                }
                denominator ^= multiply(term, p);
            }
            if (denominator == 0) {
                return false;
            }
            int magnitude = multiply(EXP[location % 255], divide(numerator, denominator));
            received[positions[k]] ^= magnitude;
        }
        for (int i = 0; i < eccLength; i++) {
            int s = 0;
            int alpha = EXP[i];
            for (int j = 0; j < n; j++) {
                s = multiply(s, alpha) ^ received[j];
            }
            if (s != 0) {
                return false;
            }
        }
        return true;
    }
}
