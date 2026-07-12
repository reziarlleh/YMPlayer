package dev.petrov.yaplay;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class Diagnostics {
    private static final String TAG = "YaPlayDiagnostics";
    private static final String FILE_NAME = "diagnostics.log";
    private static final int MAX_BYTES = 96 * 1024;
    private static final int KEEP_BYTES = 64 * 1024;

    private Diagnostics() {
    }

    public static synchronized void log(Context context, String message) {
        if (context == null) {
            return;
        }
        String line = timestamp() + " " + safe(message) + "\n";
        Log.d(TAG, line.trim());
        File file = logFile(context);
        try (FileOutputStream out = new FileOutputStream(file, true)) {
            out.write(line.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            Log.e(TAG, "Unable to write diagnostics", ex);
            return;
        }
        trimIfNeeded(file);
    }

    public static synchronized void log(Context context, String message, Throwable throwable) {
        if (throwable == null) {
            log(context, message);
            return;
        }
        log(context, message + ": " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
    }

    public static synchronized String snapshot(Context context) {
        if (context == null) {
            return "No app context.";
        }
        File file = logFile(context);
        if (!file.exists() || file.length() == 0L) {
            return "No diagnostics yet.";
        }
        try {
            return readText(file);
        } catch (IOException ex) {
            return "Unable to read diagnostics: " + ex.getMessage();
        }
    }

    public static synchronized void clear(Context context) {
        if (context == null) {
            return;
        }
        File file = logFile(context);
        if (file.exists() && !file.delete()) {
            Log.w(TAG, "Unable to delete diagnostics log");
        }
        log(context, "Diagnostics cleared");
    }

    public static synchronized ExportResult exportToDownloads(Context context) throws IOException {
        if (context == null) {
            throw new IOException("No app context");
        }
        String dateStamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        String displayName = "YMPlayer-diagnostics-" + dateStamp + ".txt";
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

        ContentResolver resolver = context.getContentResolver();
        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new IOException("Downloads storage is unavailable");
        }
        try {
            String header = "YMPlayer " + versionName(context) + "\n"
                    + "Exported: " + timestamp() + "\n"
                    + "Device: " + Build.MANUFACTURER + " " + Build.MODEL
                    + ", Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n\n";
            try (OutputStream out = resolver.openOutputStream(uri, "w")) {
                if (out == null) {
                    throw new IOException("Unable to open the Downloads file");
                }
                out.write(header.getBytes(StandardCharsets.UTF_8));
                out.write(snapshot(context).getBytes(StandardCharsets.UTF_8));
            }
            ContentValues ready = new ContentValues();
            ready.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(uri, ready, null, null);
            log(context, "Diagnostics exported to Downloads: " + displayName);
            return new ExportResult(displayName, uri);
        } catch (Exception ex) {
            resolver.delete(uri, null, null);
            if (ex instanceof IOException) {
                throw (IOException) ex;
            }
            throw new IOException(ex.getMessage(), ex);
        }
    }

    private static File logFile(Context context) {
        return new File(context.getFilesDir(), FILE_NAME);
    }

    private static void trimIfNeeded(File file) {
        if (file.length() <= MAX_BYTES) {
            return;
        }
        try {
            byte[] bytes = readBytes(file);
            int start = Math.max(0, bytes.length - KEEP_BYTES);
            try (FileOutputStream out = new FileOutputStream(file, false)) {
                out.write(bytes, start, bytes.length - start);
            }
        } catch (IOException ex) {
            Log.e(TAG, "Unable to trim diagnostics", ex);
        }
    }

    private static String readText(File file) throws IOException {
        return new String(readBytes(file), StandardCharsets.UTF_8);
    }

    private static byte[] readBytes(File file) throws IOException {
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
    }

    @SuppressWarnings("deprecation")
    private static String versionName(Context context) {
        try {
            String value = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0)
                    .versionName;
            return value == null || value.trim().isEmpty() ? "unknown" : value.trim();
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    private static String safe(String message) {
        if (message == null || message.isEmpty()) {
            return "(empty)";
        }
        return message.replace('\r', ' ').replace('\n', ' ');
    }

    public static final class ExportResult {
        public final String displayName;
        public final Uri uri;

        ExportResult(String displayName, Uri uri) {
            this.displayName = displayName;
            this.uri = uri;
        }
    }
}
