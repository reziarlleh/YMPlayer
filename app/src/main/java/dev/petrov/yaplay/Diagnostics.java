package dev.petrov.yaplay;

import android.content.Context;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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

    private static String safe(String message) {
        if (message == null || message.isEmpty()) {
            return "(empty)";
        }
        return message.replace('\r', ' ').replace('\n', ' ');
    }
}
