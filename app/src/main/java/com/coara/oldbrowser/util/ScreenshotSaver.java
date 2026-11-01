package com.coara.oldbrowser.util;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class ScreenshotSaver {
    private static final String RELATIVE_DIR = "DCIM/Screenshot";

    private ScreenshotSaver() {
    }

    public static String save(Context context, Bitmap bitmap, int quality) throws IOException {
        if (context == null || bitmap == null || bitmap.isRecycled()) {
            throw new IOException("invalid bitmap");
        }
        String fileName = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".png";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return saveToMediaStore(context.getApplicationContext().getContentResolver(), bitmap, fileName, quality);
        }
        File dir = new File(Environment.getExternalStorageDirectory(), RELATIVE_DIR);
        if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) {
            throw new IOException("cannot create " + dir);
        }
        File file = new File(dir, fileName);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, quality, fos)) {
                file.delete();
                throw new IOException("compress failed");
            }
            fos.flush();
        }
        return file.getAbsolutePath();
    }

    private static String saveToMediaStore(ContentResolver resolver, Bitmap bitmap, String fileName, int quality)
            throws IOException {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, RELATIVE_DIR);
        values.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new IOException("insert failed");
        }
        boolean ok = false;
        try (OutputStream out = resolver.openOutputStream(uri)) {
            if (out == null || !bitmap.compress(Bitmap.CompressFormat.PNG, quality, out)) {
                throw new IOException("compress failed");
            }
            out.flush();
            ok = true;
        } finally {
            if (ok) {
                ContentValues done = new ContentValues();
                done.put(MediaStore.Images.Media.IS_PENDING, 0);
                try {
                    resolver.update(uri, done, null, null);
                } catch (RuntimeException ignored) {
                }
            } else {
                try {
                    resolver.delete(uri, null, null);
                } catch (RuntimeException ignored) {
                }
            }
        }
        return RELATIVE_DIR + "/" + fileName;
    }
}
