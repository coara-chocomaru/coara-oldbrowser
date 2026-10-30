package com.coara.oldbrowser;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

public class asciiart extends Activity {

    private static final int REQUEST_CODE_PICK_IMAGE = 200;
    private Button convertButton;
    private TextView asciiTextView;
    private TextView selectedFilePath;
    private TextView savedFilePath;
    private Uri selectedImageUri = null;
    private static final String FILE_PREFIX = "_ascii_art.txt";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.asciiart);

        Button selectImageButton = findViewById(R.id.selectImageButton);
        convertButton = findViewById(R.id.convertButton);
        asciiTextView = findViewById(R.id.asciiTextView);
        selectedFilePath = findViewById(R.id.selectedFilePath);
        savedFilePath = findViewById(R.id.savedFilePath);

        selectImageButton.setOnClickListener(v -> pickImageFromStorage());

        convertButton.setOnClickListener(v -> convertSelectedImage());
    }

    private void pickImageFromStorage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE);
        } catch (Exception e) {
            Toast.makeText(this, R.string.cannot_get_image_path, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_PICK_IMAGE && resultCode == Activity.RESULT_OK && data != null) {
            selectedImageUri = data.getData();
            if (selectedImageUri != null) {
                selectedFilePath.setText(getFileName(selectedImageUri));
                convertButton.setEnabled(true);
            } else {
                selectedFilePath.setText(R.string.cannot_get_image_path);
                convertButton.setEnabled(false);
                savedFilePath.setText("");
                Toast.makeText(this, R.string.cannot_get_image_path, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String getFileName(Uri uri) {
        if (uri != null) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (column >= 0) {
                        String name = cursor.getString(column);
                        if (name != null && !name.trim().isEmpty()) {
                            return name;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return getString(R.string.unknown_file);
    }

    private void convertSelectedImage() {
        Uri uri = selectedImageUri;
        if (uri == null) {
            return;
        }
        boolean saveText = ((Switch) findViewById(R.id.saveText)).isChecked();
        boolean saveColor = ((Switch) findViewById(R.id.saveColorSwitch)).isChecked();
        Bitmap bitmap = null;
        try {
            bitmap = decodeSelectedImage(uri, saveColor);
            if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
                Toast.makeText(this, R.string.cannot_load_image, Toast.LENGTH_SHORT).show();
                return;
            }
            String asciiArt = convertToAscii(bitmap);
            asciiTextView.setText(asciiArt);
            if (saveText) {
                saveAsciiArt(asciiArt, bitmap);
            }
        } catch (OutOfMemoryError e) {
            Toast.makeText(this, R.string.cannot_load_image, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, R.string.cannot_load_image, Toast.LENGTH_SHORT).show();
        } finally {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
    }

    private Bitmap decodeSelectedImage(Uri uri, boolean preserveColorDetail) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) {
                return null;
            }
            BitmapFactory.decodeStream(input, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        int sample = 1;
        if (preserveColorDetail) {
            long pixels = (long) bounds.outWidth * (long) bounds.outHeight;
            while (pixels / ((long) sample * sample) > 16_000_000L && sample < 1 << 15) {
                sample <<= 1;
            }
        } else {
            while ((bounds.outWidth / sample) > 400 || (bounds.outHeight / sample) > 8000) {
                sample <<= 1;
            }
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = Math.max(1, sample);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inScaled = false;
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            return input == null ? null : BitmapFactory.decodeStream(input, null, options);
        }
    }

    private String convertToAscii(Bitmap bitmap) {
        int sourceWidth = bitmap.getWidth();
        int sourceHeight = bitmap.getHeight();
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            return "";
        }
        int targetWidth = 200;
        int targetHeight = (int) Math.max(1L, Math.min(4000L,
                ((long) sourceHeight * targetWidth) / Math.max(1, sourceWidth)));
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, false);
        int[] row = new int[targetWidth];
        String asciiChars = "@#S%?*+;:,. ";
        StringBuilder asciiArt = new StringBuilder((targetWidth + 1) * targetHeight);
        try {
            for (int y = 0; y < targetHeight; y++) {
                resizedBitmap.getPixels(row, 0, targetWidth, 0, y, targetWidth, 1);
                for (int x = 0; x < targetWidth; x++) {
                    int pixel = row[x];
                    int gray = (int) (0.2989 * ((pixel >> 16) & 0xFF)
                            + 0.5870 * ((pixel >> 8) & 0xFF)
                            + 0.1140 * (pixel & 0xFF));
                    asciiArt.append(asciiChars.charAt(gray * (asciiChars.length() - 1) / 255));
                }
                asciiArt.append('\n');
            }
            return asciiArt.toString();
        } finally {
            if (resizedBitmap != bitmap && !resizedBitmap.isRecycled()) {
                resizedBitmap.recycle();
            }
        }
    }

    private void saveAsciiArt(String asciiArt, Bitmap bitmap) {
        File dir = getExternalFilesDir(null);
        if (dir != null) {
            String fileName = new SimpleDateFormat("MMddHHmmss").format(new Date()) + FILE_PREFIX;
            File asciiFile = new File(dir, fileName);
            File colorFile = new File(dir, fileName.replace(".txt", ".dat"));

            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(asciiFile), StandardCharsets.UTF_8))) {
                writer.write(asciiArt);
                Toast.makeText(this, R.string.file_save_success, Toast.LENGTH_SHORT).show();
                savedFilePath.setText(getString(R.string.file_saved_path) + asciiFile.getAbsolutePath());

                if (((Switch) findViewById(R.id.saveColorSwitch)).isChecked()) {
                    saveColorData(colorFile, bitmap);
                    Toast.makeText(this, "カラー情報の保存先: " + colorFile.getAbsolutePath(), Toast.LENGTH_SHORT).show();
                }
            } catch (IOException e) {
                Toast.makeText(this, R.string.file_save_failed, Toast.LENGTH_SHORT).show();
                savedFilePath.setText("");
                e.printStackTrace();
            }
        }
    }

    private void saveColorData(File file, Bitmap bitmap) {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8), 65536)) {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int chunkWidth = Math.min(512, width);
            int[] pixels = new int[chunkWidth];
            StringBuilder line = new StringBuilder(chunkWidth * 24);
            for (int y = 0; y < height; y++) {
                for (int startX = 0; startX < width; startX += chunkWidth) {
                    int count = Math.min(chunkWidth, width - startX);
                    bitmap.getPixels(pixels, 0, count, startX, y, count, 1);
                    for (int i = 0; i < count; i++) {
                        int pixel = pixels[i];
                        line.append(startX + i).append(',').append(y).append(':')
                                .append((pixel >> 16) & 0xFF).append(',')
                                .append((pixel >> 8) & 0xFF).append(',')
                                .append(pixel & 0xFF).append('\n');
                    }
                    writer.write(line.toString());
                    line.setLength(0);
                }
            }
            writer.flush();
        } catch (IOException e) {
            Toast.makeText(this, R.string.file_save_failed, Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }
}
