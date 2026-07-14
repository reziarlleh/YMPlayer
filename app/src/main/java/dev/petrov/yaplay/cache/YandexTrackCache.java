package dev.petrov.yaplay.cache;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.os.ParcelFileDescriptor;

import org.json.JSONException;
import org.json.JSONObject;

import dev.petrov.yaplay.Diagnostics;
import dev.petrov.yaplay.player.YmpSettings;
import dev.petrov.yaplay.ymusic.AudioQuality;
import dev.petrov.yaplay.ymusic.YandexMusicClient;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class YandexTrackCache {
    public static final long PLAYBACK_CACHE_LIMIT_BYTES = 256L * 1024L * 1024L;
    private static final int PROBE_BYTES = 64;
    private static final int MAX_COVER_BYTES = 8 * 1024 * 1024;

    private static final String AUDIO_EXT = ".mp3";
    private static final String META_EXT = ".json";
    private static final String COVER_EXT = ".cover";

    private final Context context;
    private final File likedRoot;
    private final File playbackRoot;

    public YandexTrackCache(Context context) {
        this.context = context.getApplicationContext();
        likedRoot = new File(this.context.getFilesDir(), "liked-track-cache");
        playbackRoot = new File(this.context.getCacheDir(), "playback-cache");
        deleteRecursively(new File(this.context.getFilesDir(), "track-cache"));
    }

    public synchronized boolean hasLikedTrack(String trackKey) {
        return hasAudio(likedRoot, trackKey);
    }

    public synchronized long cachedSize(String trackKey) {
        File liked = audioFile(likedRoot, trackKey);
        if (liked.exists()) {
            return liked.length();
        }
        File playback = audioFile(playbackRoot, trackKey);
        return playback.exists() ? playback.length() : 0L;
    }

    public synchronized ParcelFileDescriptor openLiked(YandexMusicClient client, YandexMusicClient.Track track) throws IOException {
        File file = ensureCached(likedRoot, client, track, AudioQuality.from(YmpSettings.cacheQuality(context)));
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    public synchronized ArtworkSyncResult cacheLiked(
            YandexMusicClient client,
            YandexMusicClient.Track track
    ) throws IOException {
        ensureCached(likedRoot, client, track, AudioQuality.from(YmpSettings.cacheQuality(context)));
        return syncLikedArtwork(client, track);
    }

    public synchronized ArtworkSyncResult cacheLikedArtwork(
            YandexMusicClient client,
            YandexMusicClient.Track track
    ) throws IOException {
        if (track == null || !hasAudio(likedRoot, track.key)) {
            return ArtworkSyncResult.NOT_CACHED;
        }
        // Refresh metadata too: older cache entries may not contain a cover URL.
        writeMetadata(likedRoot, track);
        return syncLikedArtwork(client, track);
    }

    public static File likedArtworkFile(Context context, String trackKey) {
        Context appContext = context.getApplicationContext();
        return new File(
                new File(appContext.getFilesDir(), "liked-track-cache"),
                cacheId(trackKey == null ? "" : trackKey) + COVER_EXT
        );
    }

    public synchronized ParcelFileDescriptor openPlayback(YandexMusicClient client, YandexMusicClient.Track track) throws IOException {
        File liked = audioFile(likedRoot, track.key);
        if (liked.exists() && liked.length() > 0L) {
            touch(liked);
            writeMetadata(likedRoot, track);
            return ParcelFileDescriptor.open(liked, ParcelFileDescriptor.MODE_READ_ONLY);
        }

        File file = ensureCached(playbackRoot, client, track, AudioQuality.from(YmpSettings.streamQuality(context)));
        evictPlaybackCache();
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    public synchronized void prefetchPlayback(YandexMusicClient client, YandexMusicClient.Track track) throws IOException {
        File liked = audioFile(likedRoot, track.key);
        if (liked.exists() && liked.length() > 0L) {
            touch(liked);
            writeMetadata(likedRoot, track);
            return;
        }
        ensureCached(playbackRoot, client, track, AudioQuality.from(YmpSettings.streamQuality(context)));
        evictPlaybackCache();
    }

    public synchronized YandexMusicClient.Track metadata(String trackKey) throws IOException {
        YandexMusicClient.Track liked = metadata(likedRoot, trackKey);
        return liked != null ? liked : metadata(playbackRoot, trackKey);
    }

    public synchronized List<YandexMusicClient.Track> listLikedTracks() {
        return listTracks(likedRoot);
    }

    public synchronized int pruneLikedTracks(Set<String> likedKeys) {
        int removed = 0;
        if (!likedRoot.exists()) {
            return 0;
        }
        File[] audioFiles = likedRoot.listFiles((dir, name) -> name.endsWith(AUDIO_EXT));
        if (audioFiles != null) {
            for (File audio : audioFiles) {
                String id = stripExtension(audio.getName(), AUDIO_EXT);
                YandexMusicClient.Track track = metadataByCacheId(likedRoot, id);
                if (track == null || !likedKeys.contains(track.key) || !isSupportedAudio(audio)) {
                    if (audio.delete()) {
                        removed++;
                    }
                    File meta = new File(likedRoot, id + META_EXT);
                    if (meta.exists()) {
                        meta.delete();
                    }
                    File cover = new File(likedRoot, id + COVER_EXT);
                    if (cover.exists()) {
                        cover.delete();
                    }
                }
            }
        }

        File[] metadataFiles = likedRoot.listFiles((dir, name) -> name.endsWith(META_EXT));
        if (metadataFiles != null) {
            for (File meta : metadataFiles) {
                String id = stripExtension(meta.getName(), META_EXT);
                File audio = new File(likedRoot, id + AUDIO_EXT);
                if (!audio.exists()) {
                    meta.delete();
                }
            }
        }
        File[] coverFiles = likedRoot.listFiles((dir, name) -> name.endsWith(COVER_EXT));
        if (coverFiles != null) {
            for (File cover : coverFiles) {
                String id = stripExtension(cover.getName(), COVER_EXT);
                File audio = new File(likedRoot, id + AUDIO_EXT);
                if (!audio.exists()) {
                    cover.delete();
                }
            }
        }
        return removed;
    }

    public synchronized void removeLikedTrack(String trackKey) {
        deleteTrackFiles(likedRoot, trackKey);
    }

    public synchronized void removeTrackEverywhere(String trackKey) {
        deleteTrackFiles(likedRoot, trackKey);
        deleteTrackFiles(playbackRoot, trackKey);
    }

    public synchronized Summary likedSummary() {
        return summary(likedRoot);
    }

    public synchronized Summary playbackSummary() {
        return summary(playbackRoot);
    }

    public synchronized Summary clearPlaybackCache() {
        Summary before = summary(playbackRoot);
        deleteRecursively(playbackRoot);
        return before;
    }

    public synchronized Summary clearAllCache() {
        Summary liked = summary(likedRoot);
        Summary playback = summary(playbackRoot);
        deleteRecursively(likedRoot);
        deleteRecursively(playbackRoot);
        return new Summary(
                liked.count + playback.count,
                liked.bytes + playback.bytes,
                liked.coverBytes + playback.coverBytes
        );
    }

    private File ensureCached(File root, YandexMusicClient client, YandexMusicClient.Track track, AudioQuality quality) throws IOException {
        File file = audioFile(root, track.key);
        if (file.exists()) {
            if (file.length() > 0L && isSupportedAudio(file)) {
                touch(file);
                writeMetadata(root, track);
                return file;
            }
            Diagnostics.log(context, "Removing invalid cached media for " + track.key
                    + ": bytes=" + file.length() + ", probe=" + probeHex(file));
            deleteTrackFiles(root, track.key);
        }

        if (!root.exists() && !root.mkdirs()) {
            throw new IOException("Unable to create track cache");
        }

        File tmp = new File(root, file.getName() + ".tmp");
        if (tmp.exists() && !tmp.delete()) {
            throw new IOException("Unable to reset temporary cache file");
        }

        String directUrl;
        try {
            directUrl = client.getDirectUrl(track.key, quality);
        } catch (Exception ex) {
            throw new IOException("Unable to resolve media URL for " + track.key, ex);
        }

        client.downloadToFile(directUrl, tmp);
        if (!isSupportedAudio(tmp)) {
            String probe = probeHex(tmp);
            long length = tmp.length();
            if (!tmp.delete()) {
                tmp.deleteOnExit();
            }
            Diagnostics.log(context, "Downloaded media is not recognized audio for " + track.key
                    + ": bytes=" + length + ", probe=" + probe);
            throw new IOException("Downloaded media is not recognized audio: " + probe);
        }
        if (!tmp.renameTo(file)) {
            copyFile(tmp, file);
            if (!tmp.delete()) {
                tmp.deleteOnExit();
            }
        }
        touch(file);
        writeMetadata(root, track);
        return file;
    }

    private void evictPlaybackCache() {
        Summary summary = summary(playbackRoot);
        if (summary.bytes <= PLAYBACK_CACHE_LIMIT_BYTES) {
            return;
        }
        List<File> audioFiles = audioFilesSortedByOldest(playbackRoot);
        long bytes = summary.bytes;
        for (File audio : audioFiles) {
            if (bytes <= PLAYBACK_CACHE_LIMIT_BYTES) {
                break;
            }
            long length = audio.length();
            String id = stripExtension(audio.getName(), AUDIO_EXT);
            if (audio.delete()) {
                bytes -= length;
                File meta = new File(playbackRoot, id + META_EXT);
                if (meta.exists()) {
                    meta.delete();
                }
            }
        }
    }

    private YandexMusicClient.Track metadata(File root, String trackKey) throws IOException {
        File file = metadataFile(root, trackKey);
        if (!file.exists()) {
            return null;
        }
        try {
            return fromJson(new JSONObject(readText(file)));
        } catch (JSONException ex) {
            throw new IOException("Invalid cached metadata for " + trackKey, ex);
        }
    }

    private YandexMusicClient.Track metadataByCacheId(File root, String cacheId) {
        File file = new File(root, cacheId + META_EXT);
        if (!file.exists()) {
            return null;
        }
        try {
            return fromJson(new JSONObject(readText(file)));
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<YandexMusicClient.Track> listTracks(File root) {
        List<YandexMusicClient.Track> tracks = new ArrayList<>();
        if (!root.exists()) {
            return tracks;
        }
        File[] files = root.listFiles((dir, name) -> name.endsWith(META_EXT));
        if (files == null) {
            return tracks;
        }
        for (File file : files) {
            try {
                YandexMusicClient.Track track = fromJson(new JSONObject(readText(file)));
                if (track != null && hasAudio(root, track.key)) {
                    tracks.add(track);
                }
            } catch (Exception ignored) {
            }
        }
        Collections.sort(tracks, Comparator
                .comparing((YandexMusicClient.Track track) -> safeLower(track.artist))
                .thenComparing(track -> safeLower(track.title)));
        return tracks;
    }

    private Summary summary(File root) {
        int count = 0;
        long bytes = 0L;
        long coverBytes = 0L;
        if (root.exists()) {
            File[] files = root.listFiles((dir, name) -> name.endsWith(AUDIO_EXT));
            if (files != null) {
                for (File file : files) {
                    if (file.length() > 0L) {
                        if (isSupportedAudio(file)) {
                            count++;
                            bytes += file.length();
                        } else {
                            Diagnostics.log(context, "Ignoring invalid cached media in summary: " + file.getName()
                                    + ", bytes=" + file.length() + ", probe=" + probeHex(file));
                        }
                    }
                }
            }
            File[] covers = root.listFiles((dir, name) -> name.endsWith(COVER_EXT));
            if (covers != null) {
                for (File cover : covers) {
                    if (cover.length() > 0L) {
                        coverBytes += cover.length();
                    }
                }
            }
        }
        return new Summary(count, bytes, coverBytes);
    }

    private List<File> audioFilesSortedByOldest(File root) {
        List<File> files = new ArrayList<>();
        File[] audioFiles = root.listFiles((dir, name) -> name.endsWith(AUDIO_EXT));
        if (audioFiles != null) {
            Collections.addAll(files, audioFiles);
        }
        Collections.sort(files, Comparator.comparingLong(File::lastModified));
        return files;
    }

    private void writeMetadata(File root, YandexMusicClient.Track track) throws IOException {
        File file = metadataFile(root, track.key);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(toJson(track).toString().getBytes(StandardCharsets.UTF_8));
        } catch (JSONException ex) {
            throw new IOException("Unable to serialize metadata for " + track.key, ex);
        }
    }

    private boolean hasAudio(File root, String trackKey) {
        File file = audioFile(root, trackKey);
        return file.exists() && file.length() > 0L && isSupportedAudio(file);
    }

    private File audioFile(File root, String trackKey) {
        return new File(root, cacheId(trackKey) + AUDIO_EXT);
    }

    private File metadataFile(File root, String trackKey) {
        return new File(root, cacheId(trackKey) + META_EXT);
    }

    private void deleteTrackFiles(File root, String trackKey) {
        File audio = audioFile(root, trackKey);
        if (audio.exists() && !audio.delete()) {
            audio.deleteOnExit();
        }
        File meta = metadataFile(root, trackKey);
        if (meta.exists() && !meta.delete()) {
            meta.deleteOnExit();
        }
        File cover = artworkFile(root, trackKey);
        if (cover.exists() && !cover.delete()) {
            cover.deleteOnExit();
        }
    }

    private ArtworkSyncResult syncLikedArtwork(
            YandexMusicClient client,
            YandexMusicClient.Track track
    ) {
        if (track == null || track.key == null || track.key.trim().isEmpty()) {
            return ArtworkSyncResult.NO_SOURCE;
        }
        File cover = artworkFile(likedRoot, track.key);
        if (isValidArtwork(cover)) {
            return ArtworkSyncResult.PRESENT;
        }
        if (cover.exists() && !cover.delete()) {
            Diagnostics.log(context, "YMP unable to remove invalid liked artwork: " + cover.getName());
        }

        String coverUrl = track.coverUrl == null ? "" : track.coverUrl.trim();
        if (!coverUrl.startsWith("https://") && !coverUrl.startsWith("http://")) {
            return ArtworkSyncResult.NO_SOURCE;
        }
        try {
            byte[] bytes = client.downloadBytes(coverUrl);
            if (!isValidArtwork(bytes)) {
                Diagnostics.log(context, "YMP liked artwork is not a valid image for " + track.key
                        + ": bytes=" + bytes.length);
                return ArtworkSyncResult.FAILED;
            }
            writeArtworkAtomically(cover, bytes);
            Diagnostics.log(context, "YMP liked artwork cached: " + track.key
                    + ", bytes=" + bytes.length);
            return ArtworkSyncResult.DOWNLOADED;
        } catch (Exception ex) {
            Diagnostics.log(context, "YMP unable to cache liked artwork " + track.key, ex);
            return ArtworkSyncResult.FAILED;
        }
    }

    private static File artworkFile(File root, String trackKey) {
        return new File(root, cacheId(trackKey) + COVER_EXT);
    }

    private static boolean isValidArtwork(File file) {
        if (file == null || !file.exists() || file.length() <= 0L || file.length() > MAX_COVER_BYTES) {
            return false;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        return bounds.outWidth > 0 && bounds.outHeight > 0;
    }

    private static boolean isValidArtwork(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_COVER_BYTES) {
            return false;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        return bounds.outWidth > 0 && bounds.outHeight > 0;
    }

    private static void writeArtworkAtomically(File target, byte[] bytes) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Unable to create liked artwork cache");
        }
        File temp = new File(parent, target.getName() + "." + System.nanoTime() + ".tmp");
        try {
            try (FileOutputStream output = new FileOutputStream(temp)) {
                output.write(bytes);
            }
            if (!temp.renameTo(target)) {
                copyFile(temp, target);
            }
        } finally {
            if (temp.exists() && !temp.delete()) {
                temp.deleteOnExit();
            }
        }
    }

    private static boolean isSupportedAudio(File file) {
        byte[] bytes = probe(file);
        if (bytes.length < 4) {
            return false;
        }
        if (bytes[0] == 'I' && bytes[1] == 'D' && bytes[2] == '3') {
            return true;
        }
        if ((bytes[0] & 0xff) == 0xff && (bytes[1] & 0xe0) == 0xe0) {
            return true;
        }
        if (bytes.length >= 12 && bytes[4] == 'f' && bytes[5] == 't' && bytes[6] == 'y' && bytes[7] == 'p') {
            return true;
        }
        if ((bytes[0] & 0xff) == 0xff && (bytes[1] & 0xf0) == 0xf0) {
            return true;
        }
        if (bytes[0] == 'O' && bytes[1] == 'g' && bytes[2] == 'g' && bytes[3] == 'S') {
            return true;
        }
        if (bytes[0] == 'f' && bytes[1] == 'L' && bytes[2] == 'a' && bytes[3] == 'C') {
            return true;
        }
        return bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'A' && bytes[10] == 'V' && bytes[11] == 'E';
    }

    private static String probeHex(File file) {
        byte[] bytes = probe(file);
        if (bytes.length == 0) {
            return "empty";
        }
        StringBuilder hex = new StringBuilder(bytes.length * 3);
        for (byte b : bytes) {
            if (hex.length() > 0) {
                hex.append(' ');
            }
            hex.append(String.format(Locale.US, "%02x", b & 0xff));
        }
        return hex.toString();
    }

    private static byte[] probe(File file) {
        if (file == null || !file.exists() || file.length() == 0L) {
            return new byte[0];
        }
        int length = (int) Math.min(PROBE_BYTES, file.length());
        byte[] bytes = new byte[length];
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file))) {
            int offset = 0;
            while (offset < length) {
                int read = in.read(bytes, offset, length - offset);
                if (read == -1) {
                    break;
                }
                offset += read;
            }
            if (offset == length) {
                return bytes;
            }
            byte[] smaller = new byte[offset];
            System.arraycopy(bytes, 0, smaller, 0, offset);
            return smaller;
        } catch (IOException ignored) {
            return new byte[0];
        }
    }

    private static JSONObject toJson(YandexMusicClient.Track track) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", track.id);
        json.put("albumId", track.albumId);
        json.put("key", track.key);
        json.put("title", track.title);
        json.put("artist", track.artist);
        json.put("album", track.album);
        json.put("year", track.year);
        json.put("durationMs", track.durationMs);
        json.put("coverUrl", track.coverUrl);
        json.put("order", track.order);
        return json;
    }

    private static YandexMusicClient.Track fromJson(JSONObject json) {
        String key = json.optString("key", "");
        if (key.isEmpty()) {
            return null;
        }
        return new YandexMusicClient.Track(
                json.optString("id", ""),
                json.optString("albumId", ""),
                key,
                json.optString("title", key),
                json.optString("artist", ""),
                json.optString("album", ""),
                json.optInt("year", 0),
                json.optLong("durationMs", 0L),
                json.optString("coverUrl", ""),
                json.optInt("order", 0)
        );
    }

    private static String readText(File file) throws IOException {
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void copyFile(File source, File target) throws IOException {
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(source));
             FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[128 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    private static void touch(File file) {
        file.setLastModified(System.currentTimeMillis());
    }

    private static void deleteRecursively(File file) {
        if (!file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }

    private static String stripExtension(String name, String extension) {
        return name.endsWith(extension) ? name.substring(0, name.length() - extension.length()) : name;
    }

    private static String cacheId(String trackKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(trackKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format(Locale.US, "%02x", b & 0xff));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            return Integer.toHexString(trackKey.hashCode());
        }
    }

    private static String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    public static final class Summary {
        public final int count;
        public final long bytes;
        public final long coverBytes;

        public Summary(int count, long bytes, long coverBytes) {
            this.count = count;
            this.bytes = bytes;
            this.coverBytes = coverBytes;
        }
    }

    public enum ArtworkSyncResult {
        PRESENT,
        DOWNLOADED,
        NOT_CACHED,
        NO_SOURCE,
        FAILED
    }
}
