package com.coara.oldbrowser.util;

import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;
import android.webkit.MimeTypeMap;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class BrowserAssetLoader {
    private static final String HOST = "appassets.androidplatform.net";
    private static final String ASSETS_PREFIX = "/assets/";
    private static final String RES_PREFIX = "/res/";

    private final Context appContext;

    public BrowserAssetLoader(Context context) {
        appContext = context.getApplicationContext();
    }

    public WebResourceResponse intercept(WebResourceRequest request) {
        if (request == null || request.getUrl() == null) {
            return null;
        }
        return handle(request.getUrl());
    }

    public WebResourceResponse intercept(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        return handle(Uri.parse(url));
    }

    private WebResourceResponse handle(Uri uri) {
        if (!"https".equals(uri.getScheme()) || !HOST.equals(uri.getAuthority())) {
            return null;
        }
        String path = uri.getPath();
        if (path == null) {
            return null;
        }
        if (path.startsWith(ASSETS_PREFIX)) {
            String sub = path.substring(ASSETS_PREFIX.length());
            if (!isSafe(sub)) {
                return notFound();
            }
            try {
                InputStream in = appContext.getAssets().open(sub, android.content.res.AssetManager.ACCESS_STREAMING);
                return respond(sub, in);
            } catch (IOException e) {
                return notFound();
            }
        }
        if (path.startsWith(RES_PREFIX)) {
            String sub = path.substring(RES_PREFIX.length());
            if (!isSafe(sub)) {
                return notFound();
            }
            int slash = sub.indexOf('/');
            int dot = sub.lastIndexOf('.');
            if (slash <= 0 || dot <= slash + 1 || sub.indexOf('/', slash + 1) >= 0) {
                return notFound();
            }
            String type = sub.substring(0, slash);
            String name = sub.substring(slash + 1, dot);
            try {
                Resources res = appContext.getResources();
                int id = res.getIdentifier(name, type, appContext.getPackageName());
                if (id == 0) {
                    return notFound();
                }
                return respond(sub, res.openRawResource(id));
            } catch (Resources.NotFoundException e) {
                return notFound();
            }
        }
        return null;
    }

    private static boolean isSafe(String sub) {
        if (sub.isEmpty() || sub.indexOf('\\') >= 0 || sub.indexOf('\0') >= 0) {
            return false;
        }
        int start = 0;
        int len = sub.length();
        while (start <= len) {
            int end = sub.indexOf('/', start);
            if (end < 0) {
                end = len;
            }
            int segLen = end - start;
            if (segLen == 2 && sub.charAt(start) == '.' && sub.charAt(start + 1) == '.') {
                return false;
            }
            if (segLen == 1 && sub.charAt(start) == '.') {
                return false;
            }
            start = end + 1;
        }
        return true;
    }

    private static WebResourceResponse respond(String name, InputStream in) {
        String mime = mimeFor(name);
        String encoding = mime.startsWith("text/") || mime.endsWith("javascript") || mime.endsWith("json") || mime.endsWith("xml") || mime.endsWith("svg+xml") ? "utf-8" : null;
        WebResourceResponse response = new WebResourceResponse(mime, encoding, in);
        return response;
    }

    private static WebResourceResponse notFound() {
        Map<String, String> headers = new HashMap<>(2);
        headers.put("Cache-Control", "no-store");
        return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", headers, new java.io.ByteArrayInputStream(new byte[0]));
    }

    private static String mimeFor(String name) {
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
            switch (ext) {
                case "js":
                case "mjs":
                    return "text/javascript";
                case "css":
                    return "text/css";
                case "html":
                case "htm":
                    return "text/html";
                case "json":
                    return "application/json";
                case "svg":
                    return "image/svg+xml";
                case "wasm":
                    return "application/wasm";
                case "webp":
                    return "image/webp";
                case "woff2":
                    return "font/woff2";
                case "woff":
                    return "font/woff";
                case "txt":
                case "md":
                    return "text/plain";
                default:
                    break;
            }
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            if (mime != null) {
                return mime;
            }
        }
        return "application/octet-stream";
    }

    public static String normalizeLegacyAssetUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        if (url.startsWith(BrowserConstants.LEGACY_ASSET_PREFIX)) {
            return BrowserConstants.APP_ASSET_BASE_URL + url.substring(BrowserConstants.LEGACY_ASSET_PREFIX.length());
        }
        if (url.startsWith(BrowserConstants.LEGACY_RESOURCE_PREFIX)) {
            return "https://appassets.androidplatform.net/res/" + url.substring(BrowserConstants.LEGACY_RESOURCE_PREFIX.length());
        }
        return url;
    }

    public static boolean isAppAssetUrl(String url) {
        return url != null && url.startsWith(BrowserConstants.APP_ASSET_BASE_URL);
    }
}
