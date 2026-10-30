package com.coara.oldbrowser.qr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

final class QrDetector {
    static final class Point {
        final double x;
        final double y;

        Point(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    static final class Finder {
        double x;
        double y;
        double size;
        int count;

        Finder(double x, double y, double size) {
            this.x = x;
            this.y = y;
            this.size = size;
            this.count = 1;
        }

        boolean near(double px, double py, double psize) {
            if (Math.abs(px - x) > size || Math.abs(py - y) > size) {
                return false;
            }
            double diff = Math.abs(psize - size);
            return diff <= 1.0 || diff <= size;
        }
    }

    static final class Candidate {
        final Finder topLeft;
        final Finder topRight;
        final Finder bottomLeft;
        final double score;

        Candidate(Finder topLeft, Finder topRight, Finder bottomLeft, double score) {
            this.topLeft = topLeft;
            this.topRight = topRight;
            this.bottomLeft = bottomLeft;
            this.score = score;
        }
    }

    private final BitGrid grid;
    private final int width;
    private final int height;

    QrDetector(BitGrid grid) {
        this.grid = grid;
        this.width = grid.width;
        this.height = grid.height;
    }

    List<Candidate> candidates() {
        List<Finder> finders = findFinders();
        List<Finder> usable = new ArrayList<>();
        for (Finder f : finders) {
            if (f.count >= 2) {
                usable.add(f);
            }
        }
        if (usable.size() < 3) {
            usable = finders;
        }
        Collections.sort(usable, new Comparator<Finder>() {
            @Override
            public int compare(Finder a, Finder b) {
                return b.count - a.count;
            }
        });
        if (usable.size() > 12) {
            usable = new ArrayList<>(usable.subList(0, 12));
        }
        List<Candidate> result = new ArrayList<>();
        int n = usable.size();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                for (int k = j + 1; k < n; k++) {
                    Candidate c = evaluate(usable.get(i), usable.get(j), usable.get(k));
                    if (c != null) {
                        result.add(c);
                    }
                }
            }
        }
        Collections.sort(result, new Comparator<Candidate>() {
            @Override
            public int compare(Candidate a, Candidate b) {
                return Double.compare(a.score, b.score);
            }
        });
        return result;
    }

    private static double dist2(Finder a, Finder b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        return dx * dx + dy * dy;
    }

    private Candidate evaluate(Finder a, Finder b, Finder c) {
        double ab = dist2(a, b);
        double bc = dist2(b, c);
        double ac = dist2(a, c);
        Finder corner;
        Finder p1;
        Finder p2;
        double hyp;
        double s1;
        double s2;
        if (bc >= ab && bc >= ac) {
            corner = a;
            p1 = b;
            p2 = c;
            hyp = bc;
            s1 = ab;
            s2 = ac;
        } else if (ac >= ab && ac >= bc) {
            corner = b;
            p1 = a;
            p2 = c;
            hyp = ac;
            s1 = ab;
            s2 = bc;
        } else {
            corner = c;
            p1 = a;
            p2 = b;
            hyp = ab;
            s1 = ac;
            s2 = bc;
        }
        double minSize = Math.min(a.size, Math.min(b.size, c.size));
        double maxSize = Math.max(a.size, Math.max(b.size, c.size));
        if (maxSize > minSize * 1.7) {
            return null;
        }
        double side1 = Math.sqrt(s1);
        double side2 = Math.sqrt(s2);
        double meanSize = (a.size + b.size + c.size) / 3.0;
        if (side1 < meanSize * 9 || side2 < meanSize * 9) {
            return null;
        }
        double ratio = Math.min(side1, side2) / Math.max(side1, side2);
        if (ratio < 0.6) {
            return null;
        }
        double angleError = Math.abs(s1 + s2 - hyp) / (s1 + s2);
        if (angleError > 0.35) {
            return null;
        }
        double cross = (p1.x - corner.x) * (p2.y - corner.y) - (p1.y - corner.y) * (p2.x - corner.x);
        Finder topRight = cross > 0 ? p1 : p2;
        Finder bottomLeft = cross > 0 ? p2 : p1;
        double score = angleError * 4 + (1 - ratio) * 2 + (maxSize - minSize) / meanSize
                - 0.05 * (a.count + b.count + c.count);
        return new Candidate(corner, topRight, bottomLeft, score);
    }

    private boolean pixel(int x, int y) {
        return grid.bits[y * width + x];
    }

    private List<Finder> findFinders() {
        List<Finder> found = new ArrayList<>();
        int skip = Math.max(2, (3 * height) / (4 * 97));
        int[] counts = new int[5];
        for (int y = skip - 1; y < height; y += skip) {
            counts[0] = counts[1] = counts[2] = counts[3] = counts[4] = 0;
            int state = 0;
            for (int x = 0; x < width; x++) {
                if (pixel(x, y)) {
                    if ((state & 1) == 1) {
                        state++;
                    }
                    counts[state]++;
                } else if ((state & 1) == 0) {
                    if (state == 4) {
                        if (looksLikeFinder(counts) && handle(found, counts, y, x)) {
                            counts[0] = counts[1] = counts[2] = counts[3] = counts[4] = 0;
                            state = 0;
                        } else {
                            shift(counts);
                            state = 3;
                        }
                    } else {
                        counts[++state]++;
                    }
                } else {
                    counts[state]++;
                }
            }
            if (state == 4 && looksLikeFinder(counts)) {
                handle(found, counts, y, width);
            }
        }
        return found;
    }

    private static void shift(int[] c) {
        c[0] = c[2];
        c[1] = c[3];
        c[2] = c[4];
        c[3] = 1;
        c[4] = 0;
    }

    private static boolean looksLikeFinder(int[] c) {
        int total = 0;
        for (int v : c) {
            if (v == 0) {
                return false;
            }
            total += v;
        }
        if (total < 7) {
            return false;
        }
        double module = total / 7.0;
        double variance = module / 2.0;
        return Math.abs(module - c[0]) < variance
                && Math.abs(module - c[1]) < variance
                && Math.abs(3.0 * module - c[2]) < 3 * variance
                && Math.abs(module - c[3]) < variance
                && Math.abs(module - c[4]) < variance;
    }

    private static double centerFromEnd(int[] c, int end) {
        return (end - c[4] - c[3]) - c[2] / 2.0;
    }

    private boolean handle(List<Finder> found, int[] counts, int row, int end) {
        int total = counts[0] + counts[1] + counts[2] + counts[3] + counts[4];
        double centerX = centerFromEnd(counts, end);
        double centerY = crossVertical(row, (int) centerX, counts[2], total);
        if (Double.isNaN(centerY)) {
            return false;
        }
        centerX = crossHorizontal((int) centerX, (int) centerY, counts[2], total);
        if (Double.isNaN(centerX)) {
            return false;
        }
        double size = total / 7.0;
        for (Finder f : found) {
            if (f.near(centerX, centerY, size)) {
                int n = f.count;
                f.x = (n * f.x + centerX) / (n + 1);
                f.y = (n * f.y + centerY) / (n + 1);
                f.size = (n * f.size + size) / (n + 1);
                f.count = n + 1;
                return true;
            }
        }
        found.add(new Finder(centerX, centerY, size));
        return true;
    }

    private double crossVertical(int startY, int x, int maxCount, int originalTotal) {
        if (x < 0 || x >= width) {
            return Double.NaN;
        }
        int[] c = new int[5];
        int y = startY;
        while (y >= 0 && pixel(x, y)) {
            c[2]++;
            y--;
        }
        if (y < 0) {
            return Double.NaN;
        }
        while (y >= 0 && !pixel(x, y) && c[1] <= maxCount) {
            c[1]++;
            y--;
        }
        if (y < 0 || c[1] > maxCount) {
            return Double.NaN;
        }
        while (y >= 0 && pixel(x, y) && c[0] <= maxCount) {
            c[0]++;
            y--;
        }
        if (c[0] > maxCount) {
            return Double.NaN;
        }
        y = startY + 1;
        while (y < height && pixel(x, y)) {
            c[2]++;
            y++;
        }
        if (y == height) {
            return Double.NaN;
        }
        while (y < height && !pixel(x, y) && c[3] < maxCount) {
            c[3]++;
            y++;
        }
        if (y == height || c[3] >= maxCount) {
            return Double.NaN;
        }
        while (y < height && pixel(x, y) && c[4] < maxCount) {
            c[4]++;
            y++;
        }
        if (c[4] >= maxCount) {
            return Double.NaN;
        }
        int total = c[0] + c[1] + c[2] + c[3] + c[4];
        if (5 * Math.abs(total - originalTotal) >= 2 * originalTotal) {
            return Double.NaN;
        }
        return looksLikeFinder(c) ? centerFromEnd(c, y) : Double.NaN;
    }

    private double crossHorizontal(int startX, int y, int maxCount, int originalTotal) {
        if (y < 0 || y >= height) {
            return Double.NaN;
        }
        int[] c = new int[5];
        int x = startX;
        while (x >= 0 && pixel(x, y)) {
            c[2]++;
            x--;
        }
        if (x < 0) {
            return Double.NaN;
        }
        while (x >= 0 && !pixel(x, y) && c[1] <= maxCount) {
            c[1]++;
            x--;
        }
        if (x < 0 || c[1] > maxCount) {
            return Double.NaN;
        }
        while (x >= 0 && pixel(x, y) && c[0] <= maxCount) {
            c[0]++;
            x--;
        }
        if (c[0] > maxCount) {
            return Double.NaN;
        }
        x = startX + 1;
        while (x < width && pixel(x, y)) {
            c[2]++;
            x++;
        }
        if (x == width) {
            return Double.NaN;
        }
        while (x < width && !pixel(x, y) && c[3] < maxCount) {
            c[3]++;
            x++;
        }
        if (x == width || c[3] >= maxCount) {
            return Double.NaN;
        }
        while (x < width && pixel(x, y) && c[4] < maxCount) {
            c[4]++;
            x++;
        }
        if (c[4] >= maxCount) {
            return Double.NaN;
        }
        int total = c[0] + c[1] + c[2] + c[3] + c[4];
        if (5 * Math.abs(total - originalTotal) >= originalTotal) {
            return Double.NaN;
        }
        return looksLikeFinder(c) ? centerFromEnd(c, x) : Double.NaN;
    }

    double estimateModuleSize(Candidate c) {
        double dx = c.topRight.x - c.topLeft.x;
        double dy = c.topRight.y - c.topLeft.y;
        double length = Math.sqrt(dx * dx + dy * dy);
        double cos = Math.abs(dx) / length;
        double sin = Math.abs(dy) / length;
        double scale = Math.max(cos, sin);
        double size = (c.topLeft.size + c.topRight.size + c.bottomLeft.size) / 3.0;
        return size * scale;
    }

    int[] dimensionGuesses(Candidate c, double moduleSize) {
        double d1 = Math.sqrt(dist2(c.topLeft, c.topRight));
        double d2 = Math.sqrt(dist2(c.topLeft, c.bottomLeft));
        double raw = ((d1 + d2) / 2.0) / moduleSize + 7.0;
        int base = (int) Math.round((raw - 17.0) / 4.0) * 4 + 17;
        List<Integer> list = new ArrayList<>();
        int[] offsets = {0, 4, -4, 8, -8};
        for (int off : offsets) {
            int d = base + off;
            if (d >= 21 && d <= 177) {
                list.add(d);
            }
        }
        int[] result = new int[list.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = list.get(i);
        }
        return result;
    }

    BitGrid sample(Candidate c, int dimension, int mode) {
        Finder tl = c.topLeft;
        Finder tr = c.topRight;
        Finder bl = c.bottomLeft;
        double brX = tr.x + bl.x - tl.x;
        double brY = tr.y + bl.y - tl.y;
        double moduleX;
        double moduleY;
        double sourceX;
        double sourceY;
        double destX;
        double destY;
        if (dimension >= 25 && mode < 2) {
            double correction = 1.0 - 3.0 / (dimension - 7);
            double estX = tl.x + correction * (brX - tl.x);
            double estY = tl.y + correction * (brY - tl.y);
            double module = (Math.sqrt(dist2(tl, tr)) + Math.sqrt(dist2(tl, bl))) / 2.0 / (dimension - 7);
            Point alignment = null;
            if (mode == 0) {
                for (int allowance = 4; allowance <= 16 && alignment == null; allowance <<= 1) {
                    alignment = findAlignment(estX, estY, module, allowance, (tr.x - tl.x) / (dimension - 7), (tr.y - tl.y) / (dimension - 7), (bl.x - tl.x) / (dimension - 7), (bl.y - tl.y) / (dimension - 7));
                }
            }
            if (alignment != null) {
                destX = alignment.x;
                destY = alignment.y;
            } else {
                destX = estX;
                destY = estY;
            }
            sourceX = dimension - 6.5;
            sourceY = dimension - 6.5;
        } else {
            destX = brX;
            destY = brY;
            sourceX = dimension - 3.5;
            sourceY = dimension - 3.5;
        }
        double[] h = homography(
                3.5, 3.5, tl.x, tl.y,
                dimension - 3.5, 3.5, tr.x, tr.y,
                sourceX, sourceY, destX, destY,
                3.5, dimension - 3.5, bl.x, bl.y);
        if (h == null) {
            return null;
        }
        BitGrid out = new BitGrid(dimension, dimension);
        double[] offsets = {0, 0, -0.22, 0, 0.22, 0, 0, -0.22, 0, 0.22};
        for (int my = 0; my < dimension; my++) {
            for (int mx = 0; mx < dimension; mx++) {
                int black = 0;
                for (int s = 0; s < 5; s++) {
                    double u = mx + 0.5 + offsets[s * 2];
                    double v = my + 0.5 + offsets[s * 2 + 1];
                    double w = h[6] * u + h[7] * v + 1.0;
                    double px = (h[0] * u + h[1] * v + h[2]) / w;
                    double py = (h[3] * u + h[4] * v + h[5]) / w;
                    int ix = (int) Math.floor(px);
                    int iy = (int) Math.floor(py);
                    if (ix < 0 || iy < 0 || ix >= width || iy >= height) {
                        return null;
                    }
                    if (pixel(ix, iy)) {
                        black++;
                    }
                }
                out.set(mx, my, black >= 3);
            }
        }
        return out;
    }

    private Point findAlignment(double estX, double estY, double module, int allowanceModules, double ux, double uy, double vx, double vy) {
        int allowance = (int) Math.ceil(allowanceModules * module);
        int left = Math.max(0, (int) Math.floor(estX - allowance));
        int right = Math.min(width - 1, (int) Math.ceil(estX + allowance));
        int top = Math.max(0, (int) Math.floor(estY - allowance));
        int bottom = Math.min(height - 1, (int) Math.ceil(estY + allowance));
        if (right - left < module * 3 || bottom - top < module * 3) {
            return null;
        }
        Point best = null;
        double bestDistance = Double.MAX_VALUE;
        int[] c = new int[3];
        for (int y = top; y <= bottom; y++) {
            c[0] = c[1] = c[2] = 0;
            int state = 0;
            for (int x = left; x <= right + 1; x++) {
                boolean black = x <= right && pixel(x, y);
                if (black) {
                    if (state == 1) {
                        c[1]++;
                    } else if (state == 2) {
                        Point p = confirmAlignment(c, x, y, module, ux, uy, vx, vy);
                        if (p != null) {
                            double dx = p.x - estX;
                            double dy = p.y - estY;
                            double d = dx * dx + dy * dy;
                            if (d < bestDistance) {
                                bestDistance = d;
                                best = p;
                            }
                        }
                        c[0] = c[2];
                        c[1] = 1;
                        c[2] = 0;
                        state = 1;
                    } else {
                        c[++state]++;
                    }
                } else {
                    if (state == 1) {
                        state++;
                    }
                    c[state]++;
                }
            }
            if (state == 2) {
                Point p = confirmAlignment(c, right + 1, y, module, ux, uy, vx, vy);
                if (p != null) {
                    double dx = p.x - estX;
                    double dy = p.y - estY;
                    double d = dx * dx + dy * dy;
                    if (d < bestDistance) {
                        bestDistance = d;
                        best = p;
                    }
                }
            }
        }
        return best;
    }

    private Point confirmAlignment(int[] c, int end, int y, double module, double ux, double uy, double vx, double vy) {
        if (!looksLikeAlignment(c, module)) {
            return null;
        }
        double cx = (end - c[2]) - c[1] / 2.0;
        double cy = crossAlignmentVertical(y, (int) cx, module);
        if (Double.isNaN(cy)) {
            return null;
        }
        if (!ringMatches(cx, cy, ux, uy, vx, vy)) {
            return null;
        }
        return new Point(cx, cy);
    }

    private boolean ringMatches(double cx, double cy, double ux, double uy, double vx, double vy) {
        int errors = 0;
        for (int j = -2; j <= 2; j++) {
            for (int i = -2; i <= 2; i++) {
                int ring = Math.max(Math.abs(i), Math.abs(j));
                boolean expectBlack = ring != 1;
                int px = (int) Math.floor(cx + i * ux + j * vx);
                int py = (int) Math.floor(cy + i * uy + j * vy);
                if (px < 0 || py < 0 || px >= width || py >= height) {
                    return false;
                }
                if (pixel(px, py) != expectBlack) {
                    errors++;
                }
            }
        }
        return errors <= 3;
    }

    private static boolean looksLikeAlignment(int[] c, double module) {
        double max = module * 0.6 + 0.5;
        for (int v : c) {
            if (v == 0 || Math.abs(module - v) >= max) {
                return false;
            }
        }
        return true;
    }

    private double crossAlignmentVertical(int startY, int x, double module) {
        if (x < 0 || x >= width) {
            return Double.NaN;
        }
        int limit = (int) Math.ceil(module * 2);
        int[] c = new int[3];
        int y = startY;
        while (y >= 0 && pixel(x, y) && c[1] <= limit) {
            c[1]++;
            y--;
        }
        while (y >= 0 && !pixel(x, y) && c[0] <= limit) {
            c[0]++;
            y--;
        }
        y = startY + 1;
        while (y < height && pixel(x, y) && c[1] <= limit) {
            c[1]++;
            y++;
        }
        while (y < height && !pixel(x, y) && c[2] <= limit) {
            c[2]++;
            y++;
        }
        if (!looksLikeAlignment(c, module)) {
            return Double.NaN;
        }
        return (y - c[2]) - c[1] / 2.0;
    }

    private static double[] homography(double... p) {
        double[][] a = new double[8][9];
        for (int i = 0; i < 4; i++) {
            double x = p[i * 4];
            double y = p[i * 4 + 1];
            double u = p[i * 4 + 2];
            double v = p[i * 4 + 3];
            a[i * 2] = new double[]{x, y, 1, 0, 0, 0, -u * x, -u * y, u};
            a[i * 2 + 1] = new double[]{0, 0, 0, x, y, 1, -v * x, -v * y, v};
        }
        for (int col = 0; col < 8; col++) {
            int pivot = col;
            for (int r = col + 1; r < 8; r++) {
                if (Math.abs(a[r][col]) > Math.abs(a[pivot][col])) {
                    pivot = r;
                }
            }
            if (Math.abs(a[pivot][col]) < 1e-12) {
                return null;
            }
            double[] tmp = a[col];
            a[col] = a[pivot];
            a[pivot] = tmp;
            for (int r = 0; r < 8; r++) {
                if (r == col) {
                    continue;
                }
                double factor = a[r][col] / a[col][col];
                for (int k = col; k < 9; k++) {
                    a[r][k] -= factor * a[col][k];
                }
            }
        }
        double[] h = new double[8];
        for (int i = 0; i < 8; i++) {
            h[i] = a[i][8] / a[i][i];
        }
        return h;
    }
}
