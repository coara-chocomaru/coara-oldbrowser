package com.coara.oldbrowser.util;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.res.XmlResourceParser;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class SafeFileProvider extends ContentProvider {
    public static final String META_DATA_PATHS = "com.coara.oldbrowser.FILE_PROVIDER_PATHS";
    private static final String[] COLUMNS = {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
    private static final Map<String, Map<String, File>> CACHE = new HashMap<>();

    private Map<String, File> roots = new HashMap<>();

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public void attachInfo(Context context, ProviderInfo info) {
        super.attachInfo(context, info);
        if (info.exported) {
            throw new SecurityException("Provider must not be exported");
        }
        if (!info.grantUriPermissions) {
            throw new SecurityException("Provider must grant uri permissions");
        }
        roots = load(context, info.authority, info);
    }

    private static Map<String, File> load(Context context, String authority, ProviderInfo info) {
        synchronized (CACHE) {
            Map<String, File> cached = CACHE.get(authority);
            if (cached != null) {
                return cached;
            }
        }
        Map<String, File> map = new HashMap<>();
        try {
            ProviderInfo pi = info;
            if (pi == null || pi.metaData == null) {
                pi = context.getPackageManager().resolveContentProvider(authority, PackageManager.GET_META_DATA);
            }
            int res = pi == null || pi.metaData == null ? 0 : pi.metaData.getInt(META_DATA_PATHS);
            if (res == 0) {
                throw new IllegalArgumentException("Missing " + META_DATA_PATHS);
            }
            XmlResourceParser in = context.getResources().getXml(res);
            try {
                int type;
                while ((type = in.next()) != XmlPullParser.END_DOCUMENT) {
                    if (type != XmlPullParser.START_TAG) continue;
                    String tag = in.getName();
                    String name = in.getAttributeValue(null, "name");
                    String path = in.getAttributeValue(null, "path");
                    File root = null;
                    if ("root-path".equals(tag)) {
                        root = new File("/");
                    } else if ("files-path".equals(tag)) {
                        root = context.getFilesDir();
                    } else if ("cache-path".equals(tag)) {
                        root = context.getCacheDir();
                    } else if ("external-path".equals(tag)) {
                        root = Environment.getExternalStorageDirectory();
                    } else if ("external-files-path".equals(tag)) {
                        root = context.getExternalFilesDir(null);
                    } else if ("external-cache-path".equals(tag)) {
                        root = context.getExternalCacheDir();
                    } else if ("external-media-path".equals(tag)) {
                        File[] dirs = context.getExternalMediaDirs();
                        root = dirs.length > 0 ? dirs[0] : null;
                    }
                    if (root == null || TextUtils.isEmpty(name)) continue;
                    File target = path == null || path.isEmpty() || ".".equals(path) ? root : new File(root, path);
                    try {
                        map.put(name, target.getCanonicalFile());
                    } catch (IOException ignored) {
                    }
                }
            } finally {
                in.close();
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse " + META_DATA_PATHS, e);
        }
        synchronized (CACHE) {
            CACHE.put(authority, map);
        }
        return map;
    }

    public static Uri getUriForFile(Context context, String authority, File file) {
        Map<String, File> map = load(context, authority, null);
        String filePath;
        try {
            filePath = file.getCanonicalPath();
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to resolve canonical path for " + file);
        }
        Map.Entry<String, File> best = null;
        for (Map.Entry<String, File> entry : map.entrySet()) {
            String rootPath = entry.getValue().getPath();
            boolean inside = filePath.equals(rootPath)
                    || filePath.startsWith(rootPath.endsWith("/") ? rootPath : rootPath + "/");
            if (inside && (best == null || rootPath.length() > best.getValue().getPath().length())) {
                best = entry;
            }
        }
        if (best == null) {
            throw new IllegalArgumentException("Failed to find configured root that contains " + filePath);
        }
        String rootPath = best.getValue().getPath();
        String rel = rootPath.endsWith("/") ? filePath.substring(rootPath.length()) : filePath.substring(rootPath.length());
        if (rel.startsWith("/")) rel = rel.substring(1);
        String encoded = Uri.encode(best.getKey()) + "/" + Uri.encode(rel, "/");
        return new Uri.Builder().scheme("content").authority(authority).encodedPath(encoded).build();
    }

    private File fileFor(Uri uri) {
        String path = uri.getEncodedPath();
        if (path == null) throw new IllegalArgumentException("Invalid uri " + uri);
        int slash = path.indexOf('/', 1);
        if (slash < 0) throw new IllegalArgumentException("Invalid uri " + uri);
        String tag = Uri.decode(path.substring(1, slash));
        String rel = Uri.decode(path.substring(slash + 1));
        File root = roots.get(tag);
        if (root == null) throw new IllegalArgumentException("Unable to find configured root for " + uri);
        File file = new File(root, rel);
        try {
            file = file.getCanonicalFile();
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to resolve canonical path for " + uri);
        }
        String rootPath = root.getPath();
        String filePath = file.getPath();
        if (!(filePath.equals(rootPath) || filePath.startsWith(rootPath.endsWith("/") ? rootPath : rootPath + "/"))) {
            throw new SecurityException("Resolved path jumped beyond configured root");
        }
        return file;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        File file = fileFor(uri);
        String[] cols = projection == null ? COLUMNS : projection;
        String[] names = new String[cols.length];
        Object[] values = new Object[cols.length];
        int n = 0;
        for (String col : cols) {
            if (OpenableColumns.DISPLAY_NAME.equals(col)) {
                names[n] = OpenableColumns.DISPLAY_NAME;
                values[n++] = file.getName();
            } else if (OpenableColumns.SIZE.equals(col)) {
                names[n] = OpenableColumns.SIZE;
                values[n++] = file.length();
            }
        }
        String[] outNames = new String[n];
        Object[] outValues = new Object[n];
        System.arraycopy(names, 0, outNames, 0, n);
        System.arraycopy(values, 0, outValues, 0, n);
        MatrixCursor cursor = new MatrixCursor(outNames, 1);
        cursor.addRow(outValues);
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        File file = fileFor(uri);
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT));
            if (mime != null) return mime;
        }
        return "application/octet-stream";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("No external inserts");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("No external updates");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return fileFor(uri).delete() ? 1 : 0;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = fileFor(uri);
        int flags;
        if ("r".equals(mode)) {
            flags = ParcelFileDescriptor.MODE_READ_ONLY;
        } else if ("w".equals(mode) || "wt".equals(mode)) {
            flags = ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE;
        } else if ("wa".equals(mode)) {
            flags = ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_APPEND;
        } else if ("rw".equals(mode)) {
            flags = ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_CREATE;
        } else if ("rwt".equals(mode)) {
            flags = ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE;
        } else {
            throw new IllegalArgumentException("Invalid mode: " + mode);
        }
        return ParcelFileDescriptor.open(file, flags);
    }
}
