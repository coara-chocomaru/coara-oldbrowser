package com.coara.oldbrowser.webview;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import java.lang.ref.WeakReference;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class CookieGate {
    public static final String BRIDGE_NAME = "CoaraCookieGate";
    public static final String KIND_COOKIE = "c";
    public static final String KIND_STORAGE_ACCESS = "s:";
    private static final String TOKEN_MARK = "@@COARA_FRAME_TOKEN@@";
    private static final int MAX_TOKENS = 1024;
    private static final int MAX_PENDING_REPLIES = 32;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Map<String, FrameInfo> FRAMES = new LinkedHashMap<String, FrameInfo>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, FrameInfo> eldest) {
            return size() > MAX_TOKENS;
        }
    };

    public interface Reply {
        void send(String message);
    }

    public interface Listener {
        void onRequest(WebView view, String frameOrigin, String message, Reply reply);
    }

    private static final class FrameInfo {
        final WeakReference<WebView> view;
        final String origin;
        final Map<String, String> replies = new LinkedHashMap<>();

        FrameInfo(WebView view, String origin) {
            this.view = new WeakReference<>(view);
            this.origin = origin;
        }
    }

    private static final String SCRIPT = "(function(){try{"
            + "if(window.top===window)return;"
            + "var o=window.CoaraCookieGate,T='" + TOKEN_MARK + "';"
            + "if(!o||typeof o.post!=='function'||typeof o.poll!=='function')return;"
            + "var P=o.post,Q=o.poll,ST=window.setTimeout;"
            + "var sent=0,seq=0,cnt=0,seen=Object.create(null),seenCount=0;"
            + "var send=function(k){if(sent>=2)return;sent++;try{P.call(o,T,k);}catch(e){}};"
            + "var D=Object.getOwnPropertyDescriptor(Document.prototype,'cookie');"
            + "if(D&&D.configurable&&D.get&&D.set){"
            + "Object.defineProperty(Document.prototype,'cookie',{configurable:true,enumerable:D.enumerable,"
            + "get:D.get,"
            + "set:function(v){"
            + "D.set.call(this,v);"
            + "if(sent>=2)return;"
            + "try{"
            + "var s=String(v),n=s.split(';')[0],q=n.indexOf('='),nm=(q<0?n:n.slice(0,q)).trim();"
            + "if(!nm||seen[nm])return;"
            + "if(/max-age\\s*=\\s*(0|-)|expires\\s*=[^;]*(1970|1969)/i.test(s))return;"
            + "if(/;\\s*secure/i.test(s)&&location.protocol!=='https:')return;"
            + "var dm=/;\\s*domain\\s*=\\s*\\.?([^;\\s]+)/i.exec(s);"
            + "if(dm){var h=String(location.hostname).toLowerCase(),dd=dm[1].toLowerCase();"
            + "if(h!==dd&&h.slice(-dd.length-1)!=='.'+dd)return;}"
            + "if(++cnt>256){sent=2;return;}"
            + "if(seenCount<256){seen[nm]=1;seenCount++;}"
            + "var c=D.get.call(this)||'';"
            + "if((';'+c.replace(/;\\s*/g,';')).indexOf(';'+nm+'=')<0)send('c');"
            + "}catch(e){}"
            + "}});"
            + "}"
            + "var X=Document.prototype;"
            + "if(typeof X.requestStorageAccess==='function'){"
            + "X.requestStorageAccess=function(){"
            + "return new Promise(function(res,rej){"
            + "var id=String(++seq);"
            + "var deny=function(){rej(new DOMException('Storage access denied','NotAllowedError'));};"
            + "try{P.call(o,T,'s:'+id);}catch(e){deny();return;}"
            + "var tries=0,tick=function(){"
            + "var r='';try{r=String(Q.call(o,T,id));}catch(e){deny();return;}"
            + "if(r==='1'){res();return;}"
            + "if(r==='0'){deny();return;}"
            + "if(++tries>300){deny();return;}"
            + "ST(tick,200);};"
            + "ST(tick,100);"
            + "});"
            + "};"
            + "}"
            + "}catch(e){}})();";

    private CookieGate() {
    }

    private static String originOf(Uri uri) {
        StringBuilder sb = new StringBuilder();
        sb.append(uri.getScheme()).append("://").append(uri.getHost());
        if (uri.getPort() >= 0) {
            sb.append(':').append(uri.getPort());
        }
        return sb.toString();
    }

    private static String issue(WebView view, Uri documentUri) {
        byte[] raw = new byte[16];
        RANDOM.nextBytes(raw);
        StringBuilder sb = new StringBuilder(32);
        for (byte b : raw) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        String token = sb.toString();
        synchronized (FRAMES) {
            FRAMES.put(token, new FrameInfo(view, originOf(documentUri)));
        }
        return token;
    }

    private static final class Bridge {
        private final WeakReference<WebView> viewRef;
        private final Listener listener;

        Bridge(WebView view, Listener listener) {
            this.viewRef = new WeakReference<>(view);
            this.listener = listener;
        }

        @JavascriptInterface
        public void post(final String token, final String data) {
            if (token == null || data == null || data.length() > 24) {
                return;
            }
            if (!KIND_COOKIE.equals(data) && !data.startsWith(KIND_STORAGE_ACCESS)) {
                return;
            }
            final WebView view = viewRef.get();
            final FrameInfo info;
            synchronized (FRAMES) {
                info = FRAMES.get(token);
            }
            if (view == null || info == null || info.view.get() != view) {
                return;
            }
            MAIN.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        listener.onRequest(view, info.origin, data, new Reply() {
                            @Override
                            public void send(String message) {
                                int colon = message == null ? -1 : message.indexOf(':');
                                if (colon < 1) {
                                    return;
                                }
                                synchronized (info.replies) {
                                    if (info.replies.size() >= MAX_PENDING_REPLIES) {
                                        return;
                                    }
                                    info.replies.put(message.substring(0, colon), message.substring(colon + 1));
                                }
                            }
                        });
                    } catch (Throwable ignored) {
                    }
                }
            });
        }

        @JavascriptInterface
        public String poll(String token, String id) {
            if (token == null || id == null || id.length() > 12) {
                return "";
            }
            FrameInfo info;
            synchronized (FRAMES) {
                info = FRAMES.get(token);
            }
            if (info == null || info.view.get() != viewRef.get()) {
                return "";
            }
            synchronized (info.replies) {
                String r = info.replies.remove(id);
                return r == null ? "" : r;
            }
        }
    }

    public static boolean attach(WebView view, final Listener listener) {
        if (view == null || listener == null) {
            return false;
        }
        try {
            view.addJavascriptInterface(new Bridge(view, listener), BRIDGE_NAME);
            DocumentScripts.add(view, new DocumentScripts.Source() {
                @Override
                public String build(WebView v, Uri documentUri, boolean mainFrame) {
                    if (mainFrame || documentUri.getHost() == null) {
                        return null;
                    }
                    return SCRIPT.replace(TOKEN_MARK, issue(v, documentUri));
                }
            }, null, true);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void reply(Reply reply, String storageMessage, boolean allow) {
        if (reply == null || storageMessage == null || !storageMessage.startsWith(KIND_STORAGE_ACCESS)) {
            return;
        }
        String id = storageMessage.substring(KIND_STORAGE_ACCESS.length());
        if (id.isEmpty() || id.length() > 12) {
            return;
        }
        try {
            reply.send(id + ":" + (allow ? "1" : "0"));
        } catch (Throwable ignored) {
        }
    }

    public static String siteOf(String host) {
        if (host == null) {
            return "";
        }
        String h = host.toLowerCase(Locale.US);
        while (h.endsWith(".")) {
            h = h.substring(0, h.length() - 1);
        }
        if (h.isEmpty() || h.indexOf(':') >= 0 || h.indexOf('.') < 0) {
            return h;
        }
        String[] labels = h.split("\\.");
        int n = labels.length;
        if (n <= 2) {
            return h;
        }
        boolean numeric = true;
        for (String l : labels) {
            if (l.isEmpty() || !Character.isDigit(l.charAt(0))) {
                numeric = false;
                break;
            }
        }
        if (numeric) {
            return h;
        }
        String last = labels[n - 1];
        String second = labels[n - 2];
        boolean compound = last.length() == 2 && (second.equals("co") || second.equals("com") || second.equals("ne")
                || second.equals("or") || second.equals("go") || second.equals("ac") || second.equals("ad")
                || second.equals("ed") || second.equals("gr") || second.equals("lg") || second.equals("net")
                || second.equals("org") || second.equals("gov") || second.equals("edu"));
        int keep = compound ? 3 : 2;
        StringBuilder sb = new StringBuilder();
        for (int i = n - keep; i < n; i++) {
            if (sb.length() > 0) {
                sb.append('.');
            }
            sb.append(labels[i]);
        }
        return sb.toString();
    }
}
