package dev.petrov.yaplay.player;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import dev.petrov.yaplay.cache.YandexTrackCache;

public final class YmpArtworkCache {
    private static final int MAX_DECODE_SIZE = 768;
    private static final int MAX_REMOTE_BYTES = 8 * 1024 * 1024;

    private YmpArtworkCache() {
    }

    public static Bitmap loadRemoteBitmap(Context context, String coverUrl) throws Exception {
        String url = coverUrl == null ? "" : coverUrl.trim();
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            return null;
        }

        File cached = coverFile(context, url);
        Bitmap cachedBitmap = decodeCachedFile(cached);
        if (cachedBitmap != null) {
            return cachedBitmap;
        }

        byte[] bytes = downloadBytes(url);
        Bitmap bitmap = decodeBitmap(bytes);
        if (bitmap != null) {
            writeCoverCache(cached, bytes);
        }
        return bitmap;
    }

    public static Bitmap loadYandexTrackBitmap(
            Context context,
            String trackKey,
            String coverUrl
    ) throws Exception {
        String key = trackKey == null ? "" : trackKey.trim();
        if (!key.isEmpty()) {
            Bitmap permanent = decodeCachedFile(YandexTrackCache.likedArtworkFile(context, key));
            if (permanent != null) {
                return permanent;
            }
        }
        return loadRemoteBitmap(context, coverUrl);
    }

    public static Bitmap loadLocalEmbeddedBitmap(Context context, String localTrackKey) {
        Uri uri = LocalPlaylistStore.uriFromTrackKey(localTrackKey);
        if (uri == null) {
            return null;
        }
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context.getApplicationContext(), uri);
            byte[] bytes = retriever.getEmbeddedPicture();
            return bytes == null || bytes.length == 0 ? null : decodeBitmap(bytes);
        } catch (Exception ex) {
            return null;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
    }

    private static File coverFile(Context context, String coverUrl) {
        File root = new File(context.getApplicationContext().getCacheDir(), "covers");
        return new File(root, Integer.toHexString(coverUrl.hashCode()) + ".img");
    }

    private static Bitmap decodeCachedFile(File file) {
        if (file == null || !file.exists() || file.length() <= 0L) {
            return null;
        }
        Bitmap bitmap = decodeFile(file);
        if (bitmap == null) {
            // Corrupt partial files can happen if Android kills the process during a write.
            // Removing them lets the next attempt download the real cover again.
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
        return bitmap;
    }

    private static Bitmap decodeFile(File file) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        BitmapFactory.Options options = decodeOptions(bounds.outWidth, bounds.outHeight);
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    private static Bitmap decodeBitmap(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        BitmapFactory.Options options = decodeOptions(bounds.outWidth, bounds.outHeight);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }

    private static BitmapFactory.Options decodeOptions(int width, int height) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(width, height);
        return options;
    }

    private static int sampleSize(int width, int height) {
        int sample = 1;
        int largest = Math.max(width, height);
        while (largest / sample > MAX_DECODE_SIZE) {
            sample *= 2;
        }
        return Math.max(1, sample);
    }

    private static byte[] downloadBytes(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("Unexpected cover HTTP " + code);
            }
            try (InputStream input = connection.getInputStream()) {
                return readBytes(input);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static byte[] readBytes(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_REMOTE_BYTES) {
                throw new IllegalStateException("Cover is too large");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static void writeCoverCache(File file, byte[] bytes) {
        if (file == null || bytes == null || bytes.length == 0) {
            return;
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return;
        }
        File temp = new File(file.getParentFile(), file.getName() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temp)) {
            output.write(bytes);
        } catch (Exception ignored) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            return;
        }
        if (!temp.renameTo(file)) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
        }
    }
}
