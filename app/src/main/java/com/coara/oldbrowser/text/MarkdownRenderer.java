package com.coara.oldbrowser.text;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.BulletSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.QuoteSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;

public final class MarkdownRenderer {
    private static final float[] HEADING_SIZES = {1.6f, 1.4f, 1.25f, 1.12f, 1.05f, 1.0f};
    private static final int CODE_BACKGROUND = 0x1F808080;
    private static final int RULE_COLOR = 0x66808080;

    private MarkdownRenderer() {
    }

    public static CharSequence render(String markdown) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        if (markdown == null || markdown.isEmpty()) {
            return out;
        }
        String[] lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        boolean fenced = false;
        boolean pendingBlank = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (trimmed.startsWith("```")) {
                fenced = !fenced;
                pendingBlank = true;
                continue;
            }
            if (fenced) {
                int start = out.length();
                out.append(line).append('\n');
                out.setSpan(new TypefaceSpan("monospace"), start, out.length() - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new BackgroundColorSpan(CODE_BACKGROUND), start, out.length() - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                continue;
            }
            if (trimmed.isEmpty()) {
                pendingBlank = true;
                continue;
            }
            if (pendingBlank && out.length() > 0 && out.charAt(out.length() - 1) == '\n') {
                out.append('\n');
            } else if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') {
                out.append('\n');
            }
            pendingBlank = false;

            int level = headingLevel(trimmed);
            if (level > 0) {
                int start = out.length();
                String content = trimmed.substring(level).trim();
                appendInline(out, content);
                int end = out.length();
                out.setSpan(new RelativeSizeSpan(HEADING_SIZES[level - 1]), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.append('\n');
                continue;
            }
            if (isRule(trimmed)) {
                int start = out.length();
                out.append("────────────────────").append('\n');
                out.setSpan(new ForegroundColorSpan(RULE_COLOR), start, out.length() - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                continue;
            }
            if (trimmed.startsWith(">")) {
                int start = out.length();
                appendInline(out, trimmed.substring(1).trim());
                out.append('\n');
                out.setSpan(new QuoteSpan(0xFF9E9E9E), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                continue;
            }
            int bulletOffset = bulletContentStart(line);
            if (bulletOffset >= 0) {
                int indent = 0;
                while (indent < line.length() && line.charAt(indent) == ' ') {
                    indent++;
                }
                int start = out.length();
                appendInline(out, line.substring(bulletOffset).trim());
                out.append('\n');
                out.setSpan(new BulletSpan(24 + (indent / 2) * 24), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                continue;
            }
            int numberEnd = numberedContentStart(trimmed);
            if (numberEnd >= 0) {
                int start = out.length();
                out.append(trimmed, 0, numberEnd).append(' ');
                appendInline(out, trimmed.substring(numberEnd).trim().substring(0));
                out.append('\n');
                out.setSpan(new android.text.style.LeadingMarginSpan.Standard(24), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                continue;
            }
            appendInline(out, trimmed);
            out.append('\n');
        }
        int length = out.length();
        while (length > 0 && out.charAt(length - 1) == '\n') {
            length--;
        }
        if (length < out.length()) {
            out.delete(length, out.length());
        }
        return out;
    }

    private static int headingLevel(String trimmed) {
        int n = 0;
        while (n < trimmed.length() && trimmed.charAt(n) == '#') {
            n++;
        }
        if (n >= 1 && n <= 6 && n < trimmed.length() && trimmed.charAt(n) == ' ') {
            return n;
        }
        return 0;
    }

    private static boolean isRule(String trimmed) {
        if (trimmed.length() < 3) {
            return false;
        }
        char c = trimmed.charAt(0);
        if (c != '-' && c != '*' && c != '_') {
            return false;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char d = trimmed.charAt(i);
            if (d != c && d != ' ') {
                return false;
            }
        }
        return true;
    }

    private static int bulletContentStart(String line) {
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ') {
            i++;
        }
        if (i + 1 < line.length() && (line.charAt(i) == '*' || line.charAt(i) == '-' || line.charAt(i) == '+') && line.charAt(i + 1) == ' ') {
            return i + 2;
        }
        return -1;
    }

    private static int numberedContentStart(String trimmed) {
        int i = 0;
        while (i < trimmed.length() && Character.isDigit(trimmed.charAt(i))) {
            i++;
        }
        if (i > 0 && i + 1 < trimmed.length() && (trimmed.charAt(i) == '.' || trimmed.charAt(i) == ')') && trimmed.charAt(i + 1) == ' ') {
            return i + 1;
        }
        return -1;
    }

    private static void appendInline(SpannableStringBuilder out, String text) {
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < n && "\\`*_{}[]()#+-.!<>~|".indexOf(text.charAt(i + 1)) >= 0) {
                out.append(text.charAt(i + 1));
                i += 2;
                continue;
            }
            if (c == '`') {
                int end = text.indexOf('`', i + 1);
                if (end > i) {
                    int start = out.length();
                    out.append(text, i + 1, end);
                    out.setSpan(new TypefaceSpan("monospace"), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new BackgroundColorSpan(CODE_BACKGROUND), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = end + 1;
                    continue;
                }
            }
            if ((c == '*' || c == '_') && i + 1 < n && text.charAt(i + 1) == c) {
                int end = text.indexOf(String.valueOf(c) + c, i + 2);
                if (end > i + 2) {
                    int start = out.length();
                    appendInline(out, text.substring(i + 2, end));
                    out.setSpan(new StyleSpan(Typeface.BOLD), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = end + 2;
                    continue;
                }
            }
            if ((c == '*' || c == '_') && i + 1 < n && !Character.isWhitespace(text.charAt(i + 1))
                    && (c == '*' || i == 0 || !Character.isLetterOrDigit(text.charAt(i - 1)))) {
                int end = findClosing(text, c, i + 1);
                if (end > i + 1) {
                    int start = out.length();
                    appendInline(out, text.substring(i + 1, end));
                    out.setSpan(new StyleSpan(Typeface.ITALIC), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = end + 1;
                    continue;
                }
            }
            if (c == '~' && i + 1 < n && text.charAt(i + 1) == '~') {
                int end = text.indexOf("~~", i + 2);
                if (end > i + 2) {
                    int start = out.length();
                    appendInline(out, text.substring(i + 2, end));
                    out.setSpan(new android.text.style.StrikethroughSpan(), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = end + 2;
                    continue;
                }
            }
            if (c == '[') {
                int close = text.indexOf(']', i + 1);
                if (close > i && close + 1 < n && text.charAt(close + 1) == '(') {
                    int paren = text.indexOf(')', close + 2);
                    if (paren > close) {
                        String label = text.substring(i + 1, close);
                        String url = text.substring(close + 2, paren).trim();
                        int space = url.indexOf(' ');
                        if (space > 0) {
                            url = url.substring(0, space);
                        }
                        int start = out.length();
                        appendInline(out, label);
                        if (!url.isEmpty() && isSafeLink(url)) {
                            out.setSpan(new URLSpan(url), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                        i = paren + 1;
                        continue;
                    }
                }
            }
            if (c == '<') {
                int close = text.indexOf('>', i + 1);
                if (close > i) {
                    String inner = text.substring(i + 1, close);
                    if ((inner.startsWith("http://") || inner.startsWith("https://")) && inner.indexOf(' ') < 0) {
                        int start = out.length();
                        out.append(inner);
                        out.setSpan(new URLSpan(inner), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        i = close + 1;
                        continue;
                    }
                }
            }
            if (c == 'h' && (text.startsWith("http://", i) || text.startsWith("https://", i))) {
                int end = i;
                while (end < n && !Character.isWhitespace(text.charAt(end)) && text.charAt(end) != ')' && text.charAt(end) != '>') {
                    end++;
                }
                while (end > i && ".,;:!?".indexOf(text.charAt(end - 1)) >= 0) {
                    end--;
                }
                String url = text.substring(i, end);
                int start = out.length();
                out.append(url);
                out.setSpan(new URLSpan(url), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                i = end;
                continue;
            }
            out.append(c);
            i++;
        }
    }

    private static int findClosing(String text, char marker, int from) {
        for (int i = from; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\') {
                i++;
                continue;
            }
            if (c == marker && (i + 1 >= text.length() || text.charAt(i + 1) != marker) && !Character.isWhitespace(text.charAt(i - 1))) {
                if (marker == '_' && i + 1 < text.length() && Character.isLetterOrDigit(text.charAt(i + 1))) {
                    continue;
                }
                return i;
            }
        }
        return -1;
    }

    private static boolean isSafeLink(String url) {
        String lower = url.toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("mailto:");
    }
}
