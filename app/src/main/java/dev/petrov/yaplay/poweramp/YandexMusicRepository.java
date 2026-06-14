package dev.petrov.yaplay.poweramp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import dev.petrov.yaplay.Diagnostics;
import dev.petrov.yaplay.ymusic.TokenStore;
import dev.petrov.yaplay.ymusic.YandexMusicClient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

public final class YandexMusicRepository {
    private static final String TAG = "YaPlayRepository";
    private static final long CHILDREN_TTL_MS = 5 * 60 * 1000L;

    @SuppressLint("StaticFieldLeak")
    private static YandexMusicRepository instance;

    private final Context context;
    private final Map<String, CacheEntry<List<YaPlayNode>>> childrenCache = new HashMap<>();
    private final Map<String, YandexMusicClient.Track> trackCache = new HashMap<>();
    private final Map<String, String> waveBatchByTrackKey = new HashMap<>();
    private final YandexTrackCache audioCache;
    private YandexMusicClient.AccountStatus accountStatus;

    public static synchronized YandexMusicRepository get(Context context) {
        if (instance == null) {
            instance = new YandexMusicRepository(context.getApplicationContext());
        }
        return instance;
    }

    private YandexMusicRepository(Context context) {
        this.context = context;
        this.audioCache = new YandexTrackCache(context);
    }

    synchronized boolean hasToken() {
        return !TokenStore.getAccessToken(context).trim().isEmpty();
    }

    public synchronized void invalidate() {
        childrenCache.clear();
        trackCache.clear();
        waveBatchByTrackKey.clear();
        accountStatus = null;
    }

    synchronized List<YaPlayNode> children(String parentDocumentId) {
        long now = System.currentTimeMillis();
        CacheEntry<List<YaPlayNode>> cached = childrenCache.get(parentDocumentId);
        if (cached != null && now - cached.createdAtMs < CHILDREN_TTL_MS) {
            return cached.value;
        }

        List<YaPlayNode> nodes;
        try {
            nodes = loadChildren(parentDocumentId);
        } catch (Exception ex) {
            Log.e(TAG, "Failed to load children for " + parentDocumentId, ex);
            Diagnostics.log(context, "Failed to load children for " + parentDocumentId, ex);
            nodes = new ArrayList<>();
            nodes.add(YaPlayNode.folder(YaPlayProvider.SETUP_ID, "YaPlay setup required"));
        }
        childrenCache.put(parentDocumentId, new CacheEntry<>(nodes, now));
        return nodes;
    }

    synchronized YandexMusicClient.Track trackByDocumentId(String documentId) throws IOException {
        YandexMusicClient.Track cached = trackCache.get(documentId);
        if (cached != null) {
            return cached;
        }
        String trackKey = YaPlayProvider.trackKeyFromDocumentId(documentId);
        if (trackKey == null) {
            throw new IOException("Not a track document: " + documentId);
        }
        YandexMusicClient.Track cachedTrack = audioCache.metadata(trackKey);
        if (cachedTrack != null) {
            trackCache.put(documentId, cachedTrack);
            return cachedTrack;
        }
        try {
            List<String> ids = new ArrayList<>();
            ids.add(trackKey);
            List<YandexMusicClient.Track> tracks = client().getTracks(ids);
            if (tracks.isEmpty()) {
                throw new IOException("Track metadata not found: " + trackKey);
            }
            YandexMusicClient.Track track = tracks.get(0);
            trackCache.put(documentId, track);
            return track;
        } catch (Exception ex) {
            throw new IOException("Unable to load track metadata: " + trackKey, ex);
        }
    }

    synchronized ParcelFileDescriptor openTrack(String documentId) throws IOException {
        String trackKey = YaPlayProvider.trackKeyFromDocumentId(documentId);
        if (trackKey == null) {
            throw new IOException("Not a track document: " + documentId);
        }

        String storage = YaPlayProvider.trackStorageFromDocumentId(documentId);
        String source = YaPlayProvider.trackSourceFromDocumentId(documentId);
        try {
            YandexMusicClient.Track track = trackCache.get(documentId);
            if (track == null) {
                YandexMusicClient.Track cachedTrack = audioCache.metadata(trackKey);
                track = cachedTrack != null ? cachedTrack : trackByDocumentId(documentId);
            }
            maybeSendWaveTrackStarted(source, track);
            Diagnostics.log(context, "Open track: source=" + source + ", storage=" + storage + ", key=" + trackKey);
            if (YaPlayProvider.TRACK_STORAGE_LIKED.equals(storage)) {
                return audioCache.openLiked(client(), track);
            }
            return audioCache.openPlayback(client(), track);
        } catch (IOException ex) {
            Diagnostics.log(context, "Open track failed: source=" + source + ", storage=" + storage + ", key=" + trackKey, ex);
            throw ex;
        }
    }

    synchronized String directUrl(String documentId) throws IOException {
        String trackKey = YaPlayProvider.trackKeyFromDocumentId(documentId);
        if (trackKey == null) {
            throw new IOException("Not a track document: " + documentId);
        }
        String source = YaPlayProvider.trackSourceFromDocumentId(documentId);
        try {
            maybeSendWaveTrackStarted(source, trackByDocumentId(documentId));
            Diagnostics.log(context, "Direct URL requested: source=" + source + ", key=" + trackKey);
            return client().getDirectUrl(trackKey);
        } catch (Exception ex) {
            Diagnostics.log(context, "Direct URL failed: source=" + source + ", key=" + trackKey, ex);
            throw new IOException("Unable to resolve media URL for " + trackKey, ex);
        }
    }

    synchronized long cachedSize(String trackKey) {
        return audioCache.cachedSize(trackKey);
    }

    public synchronized String cacheStatusText() {
        YandexTrackCache.Summary liked = audioCache.likedSummary();
        YandexTrackCache.Summary playback = audioCache.playbackSummary();
        return "Liked cache: " + liked.count + " tracks, " + formatBytes(liked.bytes)
                + "\nPlayback cache: " + playback.count + " tracks, " + formatBytes(playback.bytes)
                + " / " + formatBytes(YandexTrackCache.PLAYBACK_CACHE_LIMIT_BYTES);
    }

    public synchronized String clearPlaybackCache() {
        YandexTrackCache.Summary removed = audioCache.clearPlaybackCache();
        invalidate();
        if (removed.count == 0) {
            return "Temporary playback cache was already empty";
        }
        return "Temporary playback cache cleared: removed " + removed.count + " tracks, " + formatBytes(removed.bytes);
    }

    public synchronized String clearLocalCache() {
        YandexTrackCache.Summary removed = audioCache.clearAllCache();
        File covers = new File(context.getCacheDir(), "covers");
        long coverBytes = directoryBytes(covers);
        deleteRecursively(covers);
        invalidate();
        if (removed.count == 0 && coverBytes == 0L) {
            return "Local cache was already empty";
        }
        return "Local cache cleared: removed " + removed.count + " tracks, "
                + formatBytes(removed.bytes) + " audio, " + formatBytes(coverBytes) + " covers";
    }

    public CacheSyncResult syncCache(boolean includeLiked, boolean includePlaylists, CacheProgress progress) throws Exception {
        if (!includeLiked && !includePlaylists) {
            return new CacheSyncResult(0, 0, 0, 0, 0, false);
        }

        Diagnostics.log(context, "Favorite cache sync started");
        YandexMusicClient client = client();
        long uid = account().uid;
        Map<String, YandexMusicClient.Track> unique = new LinkedHashMap<>();

        notifyProgress(progress, "Loading favorite tracks...");
        addUnique(unique, client.getLikedTracks(uid));
        int removed = audioCache.pruneLikedTracks(unique.keySet());
        if (removed > 0) {
            notifyProgress(progress, "Removed " + removed + " tracks no longer in favorites");
        }

        int downloaded = 0;
        int skipped = 0;
        int failed = 0;
        int index = 1;
        for (YandexMusicClient.Track track : unique.values()) {
            if (isCancelled(progress)) {
                invalidate();
                    return new CacheSyncResult(unique.size(), downloaded, skipped, failed, removed, true);
            }
            if (audioCache.hasLikedTrack(track.key)) {
                skipped++;
                notifyProgress(progress, "Cached " + index + "/" + unique.size() + " already: " + track.artist + " - " + track.title);
            } else {
                notifyProgress(progress, "Downloading " + index + "/" + unique.size() + ": " + track.artist + " - " + track.title);
                try (ParcelFileDescriptor ignored = audioCache.openLiked(client, track)) {
                    downloaded++;
                } catch (Exception ex) {
                    failed++;
                    Log.e(TAG, "Unable to cache " + track.key, ex);
                    Diagnostics.log(context, "Unable to cache liked track " + track.key, ex);
                    notifyProgress(progress, "Failed " + index + "/" + unique.size() + ": " + track.title + " (" + ex.getMessage() + ")");
                }
            }
            index++;
        }

        invalidate();
        CacheSyncResult result = new CacheSyncResult(unique.size(), downloaded, skipped, failed, removed, false);
        Diagnostics.log(context, result.summaryText());
        return result;
    }

    synchronized AssetFileDescriptor thumbnail(String documentId) throws IOException {
        YandexMusicClient.Track track = trackByDocumentId(documentId);
        if (track.coverUrl == null || track.coverUrl.isEmpty()) {
            throw new IOException("Track has no cover");
        }
        File dir = new File(context.getCacheDir(), "covers");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Unable to create cover cache");
        }
        File file = new File(dir, track.id + "_" + track.albumId + ".jpg");
        if (!file.exists() || file.length() == 0L) {
            byte[] bytes;
            try {
                bytes = client().downloadBytes(track.coverUrl);
            } catch (Exception ex) {
                Diagnostics.log(context, "Unable to download cover for " + track.key, ex);
                throw new IOException("Unable to download cover", ex);
            }
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(bytes);
            }
        }
        ParcelFileDescriptor pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        return new AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH);
    }

    private List<YaPlayNode> loadChildren(String parentDocumentId) throws Exception {
        List<YaPlayNode> nodes = new ArrayList<>();
        if (YaPlayProvider.ROOT_ID.equals(parentDocumentId)) {
            nodes.add(YaPlayNode.folder(YaPlayProvider.CACHE_ID, "Downloaded Liked Tracks"));
            if (!hasToken()) {
                nodes.add(YaPlayNode.folder(YaPlayProvider.SETUP_ID, "Open YaPlay and sign in"));
                return nodes;
            }
            YandexMusicClient.AccountStatus status = account();
            nodes.add(YaPlayNode.folder(YaPlayProvider.WAVE_ID, "My Wave"));
            nodes.add(YaPlayNode.folder(YaPlayProvider.LIKED_ID, "Liked Tracks"));
            for (YandexMusicClient.PlaylistSummary playlist : client().getPlaylists(status.uid)) {
                nodes.add(YaPlayNode.folder(YaPlayProvider.playlistDocumentId(playlist.kind, playlist.title), playlist.title));
            }
            return nodes;
        }

        if (YaPlayProvider.CACHE_ID.equals(parentDocumentId)) {
            addTracks(nodes, parentDocumentId, audioCache.listLikedTracks());
            return nodes;
        }

        if (YaPlayProvider.WAVE_ID.equals(parentDocumentId)) {
            YandexMusicClient client = client();
            YandexMusicClient.WaveTracks wave = client.getMyWave();
            waveBatchByTrackKey.clear();
            waveBatchByTrackKey.putAll(wave.batchIdByTrackKey);
            maybeSendWaveRadioStarted(client, wave.firstBatchId);
            Diagnostics.log(context, "Loaded My Wave: tracks=" + wave.tracks.size()
                    + ", batchId=" + safeBatch(wave.firstBatchId));
            addTracks(nodes, parentDocumentId, wave.tracks);
            return nodes;
        }

        if (YaPlayProvider.LIKED_ID.equals(parentDocumentId)) {
            addTracks(nodes, parentDocumentId, client().getLikedTracks(account().uid));
            return nodes;
        }

        Integer kind = YaPlayProvider.playlistKindFromDocumentId(parentDocumentId);
        if (kind != null) {
            addTracks(nodes, parentDocumentId, client().getPlaylistTracks(account().uid, kind));
        }
        return nodes;
    }

    private void addTracks(List<YaPlayNode> nodes, String parentDocumentId, List<YandexMusicClient.Track> tracks) {
        int index = 1;
        for (YandexMusicClient.Track track : tracks) {
            String docId = YaPlayProvider.trackDocumentId(parentDocumentId, track.key);
            YandexMusicClient.Track ordered = new YandexMusicClient.Track(
                    track.id,
                    track.albumId,
                    track.key,
                    track.title,
                    track.artist,
                    track.album,
                    track.year,
                    track.durationMs,
                    track.coverUrl,
                    index++
            );
            trackCache.put(docId, ordered);
            nodes.add(YaPlayNode.track(docId, ordered));
        }
    }

    private YandexMusicClient.AccountStatus account() throws Exception {
        if (accountStatus == null) {
            accountStatus = client().getAccountStatus();
        }
        return accountStatus;
    }

    private YandexMusicClient client() {
        return new YandexMusicClient(TokenStore.getAccessToken(context));
    }

    private void maybeSendWaveRadioStarted(YandexMusicClient client, String batchId) {
        if (batchId == null || batchId.isEmpty()) {
            Diagnostics.log(context, "My Wave radioStarted skipped: no batch id");
            return;
        }
        String from = "mobile-radio-user-yaplay";
        try {
            from = "mobile-radio-user-" + account().uid;
        } catch (Exception ex) {
            Diagnostics.log(context, "My Wave radioStarted from fallback used", ex);
        }
        String feedbackFrom = from;
        String feedbackBatchId = batchId;
        new Thread(() -> {
            try {
                boolean ok = client.rotorStationFeedbackRadioStarted(
                        YandexMusicClient.MY_WAVE_STATION,
                        feedbackFrom,
                        feedbackBatchId
                );
                Diagnostics.log(context, "My Wave radioStarted feedback: " + ok + ", batchId=" + safeBatch(feedbackBatchId));
            } catch (Exception ex) {
                Diagnostics.log(context, "My Wave radioStarted feedback failed", ex);
            }
        }, "YaPlayWaveRadioFeedback").start();
    }

    private void maybeSendWaveTrackStarted(String source, YandexMusicClient.Track track) {
        if (!YaPlayProvider.TRACK_SOURCE_WAVE.equals(source) || track == null) {
            return;
        }
        String batchId = waveBatchByTrackKey.get(track.key);
        if ((batchId == null || batchId.isEmpty()) && track.id != null) {
            batchId = waveBatchByTrackKey.get(track.id);
        }
        if (batchId == null || batchId.isEmpty()) {
            Diagnostics.log(context, "My Wave trackStarted skipped: no batch id for " + track.key);
            return;
        }
        String feedbackTrackId = track.id;
        String feedbackTrackKey = track.key;
        String feedbackBatchId = batchId;
        new Thread(() -> {
            try {
                boolean ok = client().rotorStationFeedbackTrackStarted(YandexMusicClient.MY_WAVE_STATION, feedbackTrackId, feedbackBatchId);
                Diagnostics.log(context, "My Wave trackStarted feedback: " + ok
                        + ", track=" + feedbackTrackId + ", batchId=" + safeBatch(feedbackBatchId));
            } catch (Exception ex) {
                Diagnostics.log(context, "My Wave trackStarted feedback failed for " + feedbackTrackKey, ex);
            }
        }, "YaPlayWaveTrackFeedback").start();
    }

    private static void addUnique(Map<String, YandexMusicClient.Track> unique, List<YandexMusicClient.Track> tracks) {
        for (YandexMusicClient.Track track : tracks) {
            if (track != null && track.key != null && !track.key.isEmpty() && !unique.containsKey(track.key)) {
                unique.put(track.key, track);
            }
        }
    }

    private static void notifyProgress(CacheProgress progress, String message) {
        if (progress != null) {
            progress.onProgress(message);
        }
    }

    private static boolean isCancelled(CacheProgress progress) {
        return progress != null && progress.isCancelled();
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        double value = bytes / 1024.0;
        String[] units = new String[] {"KB", "MB", "GB"};
        int unit = 0;
        while (value >= 1024.0 && unit < units.length - 1) {
            value /= 1024.0;
            unit++;
        }
        return String.format(Locale.US, "%.1f %s", value, units[unit]);
    }

    private static long directoryBytes(File file) {
        if (file == null || !file.exists()) {
            return 0L;
        }
        if (file.isFile()) {
            return file.length();
        }
        long bytes = 0L;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                bytes += directoryBytes(child);
            }
        }
        return bytes;
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
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

    private static String safeBatch(String batchId) {
        if (batchId == null || batchId.isEmpty()) {
            return "none";
        }
        return batchId.length() <= 12 ? batchId : batchId.substring(0, 12) + "...";
    }

    public interface CacheProgress {
        void onProgress(String message);

        boolean isCancelled();
    }

    public static final class CacheSyncResult {
        public final int total;
        public final int downloaded;
        public final int skipped;
        public final int failed;
        public final int removed;
        public final boolean cancelled;

        CacheSyncResult(int total, int downloaded, int skipped, int failed, int removed, boolean cancelled) {
            this.total = total;
            this.downloaded = downloaded;
            this.skipped = skipped;
            this.failed = failed;
            this.removed = removed;
            this.cancelled = cancelled;
        }

        public String summaryText() {
            String status = cancelled ? "Cache sync cancelled" : "Cache sync complete";
            return status + ": favorites " + total
                    + ", downloaded " + downloaded
                    + ", already cached " + skipped
                    + ", removed non-favorites " + removed
                    + ", failed " + failed;
        }
    }

    private static final class CacheEntry<T> {
        final T value;
        final long createdAtMs;

        CacheEntry(T value, long createdAtMs) {
            this.value = value;
            this.createdAtMs = createdAtMs;
        }
    }
}
