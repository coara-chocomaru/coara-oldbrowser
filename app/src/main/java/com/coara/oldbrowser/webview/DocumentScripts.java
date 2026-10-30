package com.coara.oldbrowser.webview;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public final class DocumentScripts {
    public interface Source {
        String build(WebView view, Uri documentUri, boolean mainFrame);
    }

    public static final class Handle {
        private final WeakReference<WebView> viewRef;
        private final Entry entry;

        Handle(WebView view, Entry entry) {
            this.viewRef = new WeakReference<>(view);
            this.entry = entry;
        }

        public void remove() {
            WebView view = viewRef.get();
            if (view == null) {
                return;
            }
            State state = stateOf(view, false);
            if (state != null) {
                state.entries.remove(entry);
            }
        }
    }

    private static final class Entry {
        final String id;
        final String fixed;
        final Source dynamic;
        final String[] rules;
        final boolean subFramesOnly;
        final boolean fallback;

        Entry(String id, String fixed, Source dynamic, String[] rules, boolean subFramesOnly, boolean fallback) {
            this.id = id;
            this.fixed = fixed;
            this.dynamic = dynamic;
            this.rules = rules;
            this.subFramesOnly = subFramesOnly;
            this.fallback = fallback;
        }

        String key() {
            return "coara.ds." + id;
        }

        boolean matches(Uri uri) {
            if (rules == null || rules.length == 0) {
                return true;
            }
            for (String rule : rules) {
                if (matchRule(rule, uri)) {
                    return true;
                }
            }
            return false;
        }

        String render(WebView view, Uri uri, boolean main) {
            String src = dynamic != null ? dynamic.build(view, uri, main) : fixed;
            if (src == null || src.isEmpty()) {
                return null;
            }
            if (fallback) {
                return "(function(){try{var k=Symbol.for(" + JSONObject.quote(key())
                        + ");if(window[k])return;Object.defineProperty(window,k,{value:1});}catch(e){return;}\ntry{\n"
                        + src + "\n}catch(e){}})();";
            }
            return "try{\n" + src + "\n}catch(e){}";
        }
    }

    private static final class State {
        final CopyOnWriteArrayList<Entry> entries = new CopyOnWriteArrayList<>();
        volatile String topUrl = "";
        volatile String userAgent;

        boolean hasStrict() {
            for (Entry entry : entries) {
                if (!entry.fallback) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final Map<WebView, State> STATES = Collections.synchronizedMap(new WeakHashMap<WebView, State>());
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final Map<String, Long> BYPASS = new LinkedHashMap<String, Long>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
            return size() > 128;
        }
    };
    private static final long BYPASS_MS = 300000L;
    private static final int MAX_PREFIX = 65536;
    private static final int NEED_MORE = -1;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private DocumentScripts() {
    }

    private static State stateOf(WebView view, boolean create) {
        synchronized (STATES) {
            State state = STATES.get(view);
            if (state == null && create) {
                state = new State();
                STATES.put(view, state);
            }
            return state;
        }
    }

    public static boolean isSupported() {
        return true;
    }

    public static Handle add(WebView view, String source, String[] originRules, boolean subFramesOnly, boolean fallback) {
        if (view == null || source == null || source.isEmpty()) {
            return null;
        }
        Entry entry = new Entry("e" + COUNTER.incrementAndGet(), source, null, originRules, subFramesOnly, fallback);
        stateOf(view, true).entries.add(entry);
        return new Handle(view, entry);
    }

    public static Handle add(WebView view, Source source, String[] originRules, boolean subFramesOnly) {
        if (view == null || source == null) {
            return null;
        }
        Entry entry = new Entry("e" + COUNTER.incrementAndGet(), null, source, originRules, subFramesOnly, false);
        stateOf(view, true).entries.add(entry);
        return new Handle(view, entry);
    }

    public static Handle add(WebView view, String source, java.util.Collection<String> originRules, boolean fallback) {
        String[] rules = originRules == null || originRules.contains("*") ? null : originRules.toArray(new String[0]);
        return add(view, source, rules, false, fallback);
    }

    private static boolean matchRule(String rule, Uri uri) {
        if (rule == null) {
            return false;
        }
        if (rule.equals("*")) {
            return true;
        }
        int sep = rule.indexOf("://");
        if (sep <= 0) {
            return false;
        }
        String scheme = rule.substring(0, sep);
        String hostPort = rule.substring(sep + 3);
        if (!scheme.equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        String host = hostPort;
        int port = -1;
        int colon = hostPort.lastIndexOf(':');
        if (colon > 0 && hostPort.indexOf(']') < colon) {
            try {
                port = Integer.parseInt(hostPort.substring(colon + 1));
                host = hostPort.substring(0, colon);
            } catch (NumberFormatException e) {
                return false;
            }
        }
        if (port >= 0 && uri.getPort() != port) {
            int implicit = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
            if (!(uri.getPort() < 0 && port == implicit)) {
                return false;
            }
        }
        String target = uri.getHost();
        if (target == null) {
            return false;
        }
        target = target.toLowerCase(Locale.US);
        host = host.toLowerCase(Locale.US);
        if (host.startsWith("*.")) {
            String suffix = host.substring(1);
            return target.endsWith(suffix) && target.length() > suffix.length();
        }
        return host.equals(target);
    }

    private static boolean bypassed(String key) {
        synchronized (BYPASS) {
            Long until = BYPASS.get(key);
            if (until == null) {
                return false;
            }
            if (until < System.currentTimeMillis()) {
                BYPASS.remove(key);
                return false;
            }
            return true;
        }
    }

    private static void markBypass(String key) {
        synchronized (BYPASS) {
            BYPASS.put(key, System.currentTimeMillis() + BYPASS_MS);
        }
    }

    private static String hostKey(Uri uri) {
        return (uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.US)) + ":" + uri.getPort();
    }

    private static String header(Map<String, String> headers, String name) {
        if (headers == null) {
            return null;
        }
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) {
                return e.getValue();
            }
        }
        return null;
    }

    public static WebResourceResponse intercept(WebView view, WebResourceRequest request) {
        if (view == null || request == null || request.getUrl() == null) {
            return null;
        }
        State state = stateOf(view, false);
        if (state == null || !state.hasStrict()) {
            return null;
        }
        Uri uri = request.getUrl();
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            return null;
        }
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return null;
        }
        boolean main = request.isForMainFrame();
        Map<String, String> reqHeaders = request.getRequestHeaders();
        if (!main) {
            String accept = header(reqHeaders, "Accept");
            if (accept == null || !accept.contains("text/html") || !accept.contains("application/xhtml+xml")) {
                return null;
            }
        }
        if (main) {
            state.topUrl = uri.toString();
        }
        if (bypassed(hostKey(uri))) {
            return null;
        }
        List<Entry> applicable = new ArrayList<>(state.entries.size());
        for (Entry entry : state.entries) {
            if (entry.fallback || (entry.subFramesOnly && main)) {
                continue;
            }
            if (entry.matches(uri)) {
                applicable.add(entry);
            }
        }
        if (applicable.isEmpty()) {
            return null;
        }
        try {
            return fetch(view, state, request, uri, main, applicable);
        } catch (Throwable t) {
            markBypass(hostKey(uri));
            return null;
        }
    }

    private static boolean cookiesAllowed(WebView view, State state, Uri uri, boolean main) {
        CookieManager cm = CookieManager.getInstance();
        if (!cm.acceptCookie()) {
            return false;
        }
        if (main) {
            return true;
        }
        String top = state.topUrl;
        String topHost = top == null || top.isEmpty() ? null : Uri.parse(top).getHost();
        String host = uri.getHost();
        if (topHost != null && host != null && CookieGate.siteOf(topHost).equals(CookieGate.siteOf(host))) {
            return true;
        }
        try {
            return cm.acceptThirdPartyCookies(view);
        } catch (Throwable t) {
            return false;
        }
    }

    private static WebResourceResponse fetch(WebView view, State state, WebResourceRequest request, Uri uri,
                                             boolean main, List<Entry> applicable) throws Exception {
        String url = uri.toString();
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        boolean handedOver = false;
        try {
            conn.setInstanceFollowRedirects(false);
            conn.setUseCaches(false);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setRequestMethod("GET");
            Map<String, String> reqHeaders = request.getRequestHeaders();
            boolean hasCookie = false;
            boolean hasLang = false;
            if (reqHeaders != null) {
                for (Map.Entry<String, String> e : reqHeaders.entrySet()) {
                    String k = e.getKey();
                    if (k == null || e.getValue() == null) {
                        continue;
                    }
                    String lk = k.toLowerCase(Locale.US);
                    if (lk.equals("host") || lk.equals("connection") || lk.equals("accept-encoding")
                            || lk.equals("content-length") || lk.equals("transfer-encoding")) {
                        continue;
                    }
                    if (lk.equals("cookie")) {
                        hasCookie = true;
                    } else if (lk.equals("accept-language")) {
                        hasLang = true;
                    } else if (lk.equals("user-agent")) {
                        state.userAgent = e.getValue();
                    }
                    conn.setRequestProperty(k, e.getValue());
                }
            }
            if (header(reqHeaders, "User-Agent") == null) {
                String ua = state.userAgent != null ? state.userAgent : System.getProperty("http.agent");
                if (ua != null) {
                    conn.setRequestProperty("User-Agent", ua);
                }
            }
            if (!hasLang) {
                conn.setRequestProperty("Accept-Language", Locale.getDefault().toLanguageTag() + ",en;q=0.8");
            }
            boolean cookies = cookiesAllowed(view, state, uri, main);
            if (!hasCookie && cookies) {
                String cookie = CookieManager.getInstance().getCookie(url);
                if (cookie != null && !cookie.isEmpty()) {
                    conn.setRequestProperty("Cookie", cookie);
                }
            }
            int code = conn.getResponseCode();
            if (cookies) {
                applyCookies(conn, url);
            }
            if (code == 401 || code == 407 || code < 200 || code == 204 || code == 205 || code == 304
                    || (code >= 300 && code < 400 && !isRedirectable(code))) {
                markBypass(hostKey(uri));
                return null;
            }
            if (code >= 300 && code < 400) {
                String location = conn.getHeaderField("Location");
                if (location == null || location.isEmpty()) {
                    markBypass(hostKey(uri));
                    return null;
                }
                String target = new URL(new URL(url), location).toString();
                String lower = target.toLowerCase(Locale.US);
                if (lower.startsWith("javascript:") || lower.startsWith("data:") || lower.startsWith("blob:")) {
                    markBypass(hostKey(uri));
                    return null;
                }
                return redirectPage(target);
            }
            String contentType = conn.getContentType();
            String mime = "text/html";
            String charset = null;
            if (contentType != null) {
                String[] parts = contentType.split(";");
                if (parts.length > 0 && !parts[0].trim().isEmpty()) {
                    mime = parts[0].trim().toLowerCase(Locale.US);
                }
                for (int i = 1; i < parts.length; i++) {
                    String p = parts[i].trim();
                    if (p.regionMatches(true, 0, "charset=", 0, 8)) {
                        charset = p.substring(8).trim();
                        if (charset.length() > 1 && charset.startsWith("\"") && charset.endsWith("\"")) {
                            charset = charset.substring(1, charset.length() - 1);
                        }
                    }
                }
            }
            InputStream raw = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (raw == null) {
                markBypass(hostKey(uri));
                return null;
            }
            Map<String, String> out = new LinkedHashMap<>();
            String cspValue = null;
            for (Map.Entry<String, List<String>> e : conn.getHeaderFields().entrySet()) {
                String k = e.getKey();
                if (k == null || e.getValue() == null || e.getValue().isEmpty()) {
                    continue;
                }
                String lk = k.toLowerCase(Locale.US);
                if (lk.equals("set-cookie") || lk.equals("set-cookie2") || lk.equals("content-length")
                        || lk.equals("content-encoding") || lk.equals("transfer-encoding") || lk.equals("connection")
                        || lk.equals("keep-alive") || lk.equals("content-type")) {
                    continue;
                }
                StringBuilder sb = new StringBuilder();
                for (String v : e.getValue()) {
                    if (v == null) {
                        continue;
                    }
                    if (sb.length() > 0) {
                        sb.append(", ");
                    }
                    sb.append(v);
                }
                if (lk.equals("content-security-policy")) {
                    cspValue = sb.toString();
                    continue;
                }
                out.put(k, sb.toString());
            }
            String reason = conn.getResponseMessage();
            if (reason == null || reason.isEmpty()) {
                reason = code >= 400 ? "Error" : "OK";
            }
            boolean injectable = mime.equals("text/html") && asciiCompatible(charset);
            InputStream body = raw;
            if (injectable) {
                StringBuilder markup = new StringBuilder();
                List<String> hashes = new ArrayList<>();
                for (Entry entry : applicable) {
                    String text = entry.render(view, uri, main);
                    if (text == null) {
                        continue;
                    }
                    text = sanitize(text);
                    markup.append("<script>").append(text).append("</script>");
                    hashes.add(sha256(text));
                }
                if (markup.length() > 0) {
                    String adapted = cspValue == null ? null : adaptCsp(cspValue, hashes);
                    if (cspValue == null || adapted != null) {
                        if (adapted != null) {
                            out.put("Content-Security-Policy", adapted);
                        }
                        body = new InjectingStream(raw, conn, markup.toString().getBytes("US-ASCII"));
                        handedOver = true;
                    }
                }
            }
            if (!handedOver) {
                if (cspValue != null) {
                    out.put("Content-Security-Policy", cspValue);
                }
                body = new ClosingStream(raw, conn);
                handedOver = true;
            }
            return new WebResourceResponse(mime, charset, code, reason, out, body);
        } finally {
            if (!handedOver) {
                conn.disconnect();
            }
        }
    }

    private static boolean isRedirectable(int code) {
        return code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
    }

    private static void applyCookies(HttpURLConnection conn, String url) {
        CookieManager cm = CookieManager.getInstance();
        for (Map.Entry<String, List<String>> e : conn.getHeaderFields().entrySet()) {
            String k = e.getKey();
            if (k == null || !(k.equalsIgnoreCase("Set-Cookie") || k.equalsIgnoreCase("Set-Cookie2"))) {
                continue;
            }
            for (String v : e.getValue()) {
                if (v != null && !v.isEmpty()) {
                    cm.setCookie(url, v);
                }
            }
        }
    }

    private static WebResourceResponse redirectPage(String target) throws IOException {
        String html = "<!doctype html><meta charset=\"utf-8\"><meta name=\"referrer\" content=\"no-referrer-when-downgrade\">"
                + "<script>location.replace(" + sanitize(JSONObject.quote(target)) + ");</script>"
                + "<noscript><meta http-equiv=\"refresh\" content=\"0;url=" + escapeAttr(target) + "\"></noscript>";
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Cache-Control", "no-store");
        return new WebResourceResponse("text/html", "utf-8", 200, "OK", headers,
                new java.io.ByteArrayInputStream(html.getBytes("UTF-8")));
    }

    private static String escapeAttr(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '&') sb.append("&amp;");
            else if (c == '"') sb.append("&quot;");
            else if (c == '<') sb.append("&lt;");
            else if (c == '>') sb.append("&gt;");
            else sb.append(c);
        }
        return sb.toString();
    }

    private static boolean asciiCompatible(String charset) {
        if (charset == null) {
            return true;
        }
        String c = charset.toLowerCase(Locale.US);
        return !(c.startsWith("utf-16") || c.startsWith("utf-32") || c.startsWith("ucs") || c.equals("unicode"));
    }

    private static String sanitize(String text) {
        StringBuilder sb = new StringBuilder(text.length() + 32);
        int n = text.length();
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            if (c > 126 || (c < 32 && c != '\n' && c != '\r' && c != '\t')) {
                sb.append("\\u");
                String h = Integer.toHexString(c);
                for (int p = h.length(); p < 4; p++) {
                    sb.append('0');
                }
                sb.append(h);
                continue;
            }
            if (c == '<' && i + 1 < n) {
                if (text.regionMatches(true, i, "</script", 0, 8)) {
                    sb.append("<\\/");
                    i++;
                    continue;
                }
                if (text.startsWith("<!--", i)) {
                    sb.append("<\\!--");
                    i += 3;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static String sha256(String text) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(text.getBytes("UTF-8"));
        return android.util.Base64.encodeToString(digest, android.util.Base64.NO_WRAP);
    }

    private static String adaptCsp(String value, List<String> hashes) {
        StringBuilder hashTokens = new StringBuilder();
        for (String h : hashes) {
            hashTokens.append(" 'sha256-").append(h).append("'");
        }
        String[] policies = value.split(",");
        StringBuilder result = new StringBuilder();
        for (int p = 0; p < policies.length; p++) {
            String policy = policies[p].trim();
            if (policy.isEmpty()) {
                continue;
            }
            String[] directives = policy.split(";");
            int target = -1;
            int rank = 4;
            for (int i = 0; i < directives.length; i++) {
                String d = directives[i].trim();
                if (d.isEmpty()) {
                    continue;
                }
                String[] tok = d.split("\\s+");
                String name = tok[0].toLowerCase(Locale.US);
                if (name.equals("sandbox")) {
                    boolean allowScripts = false;
                    for (int t = 1; t < tok.length; t++) {
                        if (tok[t].equalsIgnoreCase("allow-scripts")) {
                            allowScripts = true;
                        }
                    }
                    if (!allowScripts) {
                        return null;
                    }
                }
                int r = name.equals("script-src-elem") ? 1 : name.equals("script-src") ? 2 : name.equals("default-src") ? 3 : 4;
                if (r < rank) {
                    rank = r;
                    target = i;
                }
            }
            StringBuilder rebuilt = new StringBuilder();
            for (int i = 0; i < directives.length; i++) {
                String d = directives[i].trim();
                if (d.isEmpty()) {
                    continue;
                }
                if (i == target) {
                    String lower = d.toLowerCase(Locale.US);
                    boolean none = lower.matches(".*\\s'none'(\\s.*)?$");
                    if (none) {
                        return null;
                    }
                    boolean strictNonce = lower.contains("'nonce-") || lower.contains("'sha256-")
                            || lower.contains("'sha384-") || lower.contains("'sha512-");
                    boolean unsafeInline = lower.contains("'unsafe-inline'");
                    if (!unsafeInline || strictNonce) {
                        d = d + hashTokens;
                    }
                }
                if (rebuilt.length() > 0) {
                    rebuilt.append("; ");
                }
                rebuilt.append(d);
            }
            if (result.length() > 0) {
                result.append(", ");
            }
            result.append(rebuilt);
        }
        return result.toString();
    }

    private static boolean isWs(byte b) {
        return b == ' ' || b == '\n' || b == '\r' || b == '\t' || b == '\f';
    }

    private static boolean startsWithIgnoreCase(byte[] b, int off, int n, String s) {
        if (off + s.length() > n) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            int c = b[off + i];
            if (c >= 'A' && c <= 'Z') {
                c += 32;
            }
            if (c != s.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static int indexOf(byte[] b, int from, int n, String s) {
        int len = s.length();
        for (int i = from; i + len <= n; i++) {
            int j = 0;
            while (j < len && b[i + j] == s.charAt(j)) {
                j++;
            }
            if (j == len) {
                return i;
            }
        }
        return -1;
    }

    private static int tagEnd(byte[] b, int from, int n) {
        int quote = 0;
        for (int i = from; i < n; i++) {
            byte c = b[i];
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '>') {
                return i;
            }
        }
        return -1;
    }

    static int findInsertion(byte[] b, int n, boolean complete) {
        int i = 0;
        if (n >= 3 && b[0] == (byte) 0xEF && b[1] == (byte) 0xBB && b[2] == (byte) 0xBF) {
            i = 3;
        }
        int safe = i;
        while (true) {
            while (i < n && isWs(b[i])) {
                i++;
            }
            if (i >= n) {
                return complete ? safe : NEED_MORE;
            }
            if (b[i] != '<') {
                return safe;
            }
            if (i + 1 >= n) {
                return complete ? safe : NEED_MORE;
            }
            if (i + 3 < n && b[i + 1] == '!' && b[i + 2] == '-' && b[i + 3] == '-') {
                int e = indexOf(b, i + 4, n, "-->");
                if (e < 0) {
                    return complete ? safe : NEED_MORE;
                }
                i = e + 3;
                continue;
            }
            if (b[i + 1] == '!') {
                if (i + 9 > n && !complete) {
                    return NEED_MORE;
                }
                if (startsWithIgnoreCase(b, i, n, "<!doctype")) {
                    int e = tagEnd(b, i + 2, n);
                    if (e < 0) {
                        return complete ? safe : NEED_MORE;
                    }
                    i = e + 1;
                    safe = i;
                    continue;
                }
                return safe;
            }
            if (b[i + 1] == '?') {
                int e = tagEnd(b, i + 2, n);
                if (e < 0) {
                    return complete ? safe : NEED_MORE;
                }
                i = e + 1;
                continue;
            }
            int c1 = b[i + 1] | 0x20;
            if (c1 < 'a' || c1 > 'z') {
                return safe;
            }
            int j = i + 1;
            while (j < n && (((b[j] | 0x20) >= 'a' && (b[j] | 0x20) <= 'z') || (b[j] >= '0' && b[j] <= '9') || b[j] == '-')) {
                j++;
            }
            if (j >= n) {
                return complete ? safe : NEED_MORE;
            }
            int e = tagEnd(b, j, n);
            if (e < 0) {
                return complete ? safe : NEED_MORE;
            }
            if (startsWithIgnoreCase(b, i, n, "<html") && j - i == 5) {
                safe = e + 1;
                i = e + 1;
                continue;
            }
            if (startsWithIgnoreCase(b, i, n, "<head") && j - i == 5) {
                return e + 1;
            }
            return safe;
        }
    }

    private static final class ClosingStream extends InputStream {
        private final InputStream in;
        private final HttpURLConnection conn;

        ClosingStream(InputStream in, HttpURLConnection conn) {
            this.in = in;
            this.conn = conn;
        }

        @Override
        public int read() throws IOException {
            return in.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return in.read(b, off, len);
        }

        @Override
        public int available() throws IOException {
            return in.available();
        }

        @Override
        public void close() throws IOException {
            try {
                in.close();
            } finally {
                conn.disconnect();
            }
        }
    }

    private static final class InjectingStream extends InputStream {
        private final InputStream in;
        private final HttpURLConnection conn;
        private final byte[] inject;
        private byte[] head;
        private int headPos;
        private int headLen;
        private boolean ready;

        InjectingStream(InputStream in, HttpURLConnection conn, byte[] inject) {
            this.in = in;
            this.conn = conn;
            this.inject = inject;
        }

        private void prepare() throws IOException {
            if (ready) {
                return;
            }
            ready = true;
            byte[] buf = new byte[8192];
            int n = 0;
            int at;
            while (true) {
                if (n == buf.length) {
                    byte[] grown = new byte[Math.min(MAX_PREFIX, buf.length * 2)];
                    System.arraycopy(buf, 0, grown, 0, n);
                    buf = grown;
                }
                int r = in.read(buf, n, buf.length - n);
                boolean eof = r < 0;
                if (r > 0) {
                    n += r;
                }
                boolean complete = eof || n >= MAX_PREFIX;
                at = findInsertion(buf, n, complete);
                if (at >= 0) {
                    break;
                }
                if (complete) {
                    at = 0;
                    break;
                }
            }
            if (n >= 2 && ((buf[0] == (byte) 0xFE && buf[1] == (byte) 0xFF) || (buf[0] == (byte) 0xFF && buf[1] == (byte) 0xFE))) {
                head = buf;
                headLen = n;
                return;
            }
            byte[] merged = new byte[n + inject.length];
            System.arraycopy(buf, 0, merged, 0, at);
            System.arraycopy(inject, 0, merged, at, inject.length);
            System.arraycopy(buf, at, merged, at + inject.length, n - at);
            head = merged;
            headLen = merged.length;
        }

        @Override
        public int read() throws IOException {
            prepare();
            if (headPos < headLen) {
                return head[headPos++] & 0xFF;
            }
            return in.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            prepare();
            if (headPos < headLen) {
                int c = Math.min(len, headLen - headPos);
                System.arraycopy(head, headPos, b, off, c);
                headPos += c;
                if (headPos >= headLen) {
                    head = null;
                }
                return c;
            }
            return in.read(b, off, len);
        }

        @Override
        public int available() throws IOException {
            return (headPos < headLen ? headLen - headPos : 0) + in.available();
        }

        @Override
        public void close() throws IOException {
            try {
                in.close();
            } finally {
                conn.disconnect();
            }
        }
    }

    public static void runFallback(final WebView view) {
        if (view == null) {
            return;
        }
        final State state = stateOf(view, false);
        if (state == null || state.entries.isEmpty()) {
            return;
        }
        String current = view.getUrl();
        if (current == null || !(current.startsWith("http://") || current.startsWith("https://"))) {
            return;
        }
        Uri uri = Uri.parse(current);
        final StringBuilder script = new StringBuilder();
        final StringBuilder keys = new StringBuilder();
        for (Entry entry : state.entries) {
            if (!entry.fallback || entry.subFramesOnly || !entry.matches(uri)) {
                continue;
            }
            String text = entry.render(view, uri, true);
            if (text == null) {
                continue;
            }
            script.append(text).append('\n');
            if (keys.length() > 0) {
                keys.append(',');
            }
            keys.append(JSONObject.quote(entry.key()));
        }
        if (script.length() == 0) {
            return;
        }
        final String probe = "(function(){try{var s=Symbol.for;return [" + keys + "].every(function(k){return !!window[s(k)];});}catch(e){return false;}})()";
        final String body = script.toString();
        view.evaluateJavascript(probe, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                if ("true".equals(value)) {
                    return;
                }
                try {
                    view.evaluateJavascript(body, null);
                } catch (Throwable ignored) {
                }
            }
        });
    }
}
