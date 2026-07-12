package dev.petrov.yaplay.player;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.IOException;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

import dev.petrov.yaplay.Diagnostics;
import dev.petrov.yaplay.cache.YandexTrackCache;
import dev.petrov.yaplay.ymusic.TokenStore;
import dev.petrov.yaplay.ymusic.YandexMusicClient;

public final class YmpRepository {
    private static final int MORE_WAVE_TARGET = 1;

    private final Context context;
    private final YandexTrackCache audioCache;
    private final LocalPlaylistStore localPlaylists;
    private YandexMusicClient.AccountStatus accountStatus;

    public YmpRepository(Context context) {
        this.context = context.getApplicationContext();
        this.audioCache = new YandexTrackCache(this.context);
        this.localPlaylists = new LocalPlaylistStore(this.context);
    }

    public synchronized boolean hasToken() {
        return !TokenStore.getAccessToken(context).trim().isEmpty();
    }

    public synchronized YandexMusicClient.AccountStatus account() throws Exception {
        if (accountStatus == null) {
            accountStatus = client().getAccountStatus();
        }
        return accountStatus;
    }

    public synchronized void invalidateAccount() {
        accountStatus = null;
    }

    public synchronized WaveQueue loadInitialWave() throws Exception {
        YandexMusicClient.WaveTracks wave = client().getMyWaveFastStart(1);
        Diagnostics.log(context, "YMP loaded My Wave fast-start: tracks=" + wave.tracks.size()
                + ", session=" + safe(wave.sessionId)
                + ", batch=" + safe(wave.firstBatchId));
        return new WaveQueue(wave.tracks, wave.sessionId, wave.firstBatchId, wave.batchIdByTrackKey);
    }

    public synchronized WaveQueue loadMoreWave(WaveQueue current) throws Exception {
        if (current == null || current.tracks.isEmpty()) {
            return loadInitialWave();
        }
        String cursor = current.currentCursorTrackId();
        YandexMusicClient.WaveTracks wave = client().getMoreMyWave(current.sessionId, cursor, MORE_WAVE_TARGET);
        if (wave.tracks.isEmpty()) {
            Diagnostics.log(context, "YMP My Wave session returned empty load-more, restarting wave seed");
            wave = client().getMyWave(MORE_WAVE_TARGET);
        }
        Diagnostics.log(context, "YMP loaded more My Wave: tracks=" + wave.tracks.size()
                + ", session=" + safe(wave.sessionId)
                + ", batch=" + safe(wave.firstBatchId)
                + ", cursor=" + cursor);
        current.append(wave.tracks, wave.sessionId, wave.firstBatchId, wave.batchIdByTrackKey);
        return current;
    }

    public void sendWaveRadioStarted(WaveQueue waveQueue) {
        if (waveQueue == null || waveQueue.batchId == null || waveQueue.batchId.isEmpty()) {
            Diagnostics.log(context, "YMP My Wave radioStarted feedback skipped: no batch");
            return;
        }
        new Thread(() -> {
            try {
                String from = "mobile-radio-user-" + account().uid;
                boolean ok = client().rotorStationFeedbackRadioStarted(
                        YandexMusicClient.MY_WAVE_STATION,
                        from,
                        waveQueue.batchId
                );
                Diagnostics.log(context, "YMP My Wave radioStarted feedback: " + ok);
            } catch (Exception ex) {
                Diagnostics.log(context, "YMP My Wave radioStarted feedback failed", ex);
            }
        }, "YMP-WaveRadioFeedback").start();
    }

    public void sendWaveTrackStarted(WaveQueue waveQueue, YandexMusicClient.Track track) {
        if (waveQueue == null || track == null) {
            return;
        }
        String batchId = waveQueue.batchIdFor(track);
        if (batchId.isEmpty()) {
            Diagnostics.log(context, "YMP My Wave trackStarted feedback skipped: no batch for " + track.key);
            return;
        }
        new Thread(() -> {
            try {
                boolean ok = client().rotorStationFeedbackTrackStarted(
                        YandexMusicClient.MY_WAVE_STATION,
                        track.id,
                        batchId
                );
                Diagnostics.log(context, "YMP My Wave trackStarted feedback: " + track.key + ", ok=" + ok);
            } catch (Exception ex) {
                Diagnostics.log(context, "YMP My Wave trackStarted feedback failed: " + track.key, ex);
            }
        }, "YMP-WaveTrackFeedback").start();
    }

    public void sendWaveDislike(WaveQueue waveQueue, YandexMusicClient.Track track) {
        if (waveQueue == null || track == null) {
            return;
        }
        String batchId = waveQueue.batchIdFor(track);
        if (batchId.isEmpty()) {
            Diagnostics.log(context, "YMP My Wave dislike feedback skipped: no batch for " + track.key);
            return;
        }
        new Thread(() -> {
            try {
                boolean ok = client().rotorStationFeedbackDislike(
                        YandexMusicClient.MY_WAVE_STATION,
                        track.id,
                        batchId
                );
                Diagnostics.log(context, "YMP My Wave dislike feedback: " + track.key + ", ok=" + ok);
            } catch (Exception ex) {
                Diagnostics.log(context, "YMP My Wave dislike feedback failed: " + track.key, ex);
            }
        }, "YMP-WaveDislikeFeedback").start();
    }

    public synchronized List<YandexMusicClient.Track> likedCacheTracks() {
        return audioCache.listLikedTracks();
    }

    public synchronized List<YandexMusicClient.PlaylistSummary> playlists() throws Exception {
        return client().getPlaylists(account().uid);
    }

    public synchronized List<YandexMusicClient.Track> playlistTracks(int kind) throws Exception {
        return client().getPlaylistTracks(account().uid, kind);
    }

    public synchronized YandexMusicClient.SearchResults search(String query) throws Exception {
        YandexMusicClient.SearchResults results = client().search(query);
        Diagnostics.log(context, "YMP search complete: query=" + query
                + ", tracks=" + results.tracks.size()
                + ", albums=" + results.albums.size()
                + ", artists=" + results.artists.size());
        return results;
    }

    public synchronized List<YandexMusicClient.Track> searchTrackQueue(String trackKey) throws Exception {
        List<String> ids = new ArrayList<>();
        ids.add(trackKey);
        return client().getTracks(ids);
    }

    public synchronized List<YandexMusicClient.Track> albumTracks(String albumId) throws Exception {
        List<YandexMusicClient.Track> tracks = client().getAlbumTracks(albumId);
        Diagnostics.log(context, "YMP album tracks loaded: album=" + albumId + ", tracks=" + tracks.size());
        return tracks;
    }

    public synchronized List<YandexMusicClient.Track> artistTracks(String artistId) throws Exception {
        List<YandexMusicClient.Track> tracks = client().getArtistTracks(artistId);
        Diagnostics.log(context, "YMP artist tracks loaded: artist=" + artistId + ", tracks=" + tracks.size());
        return tracks;
    }

    public synchronized YandexMusicClient.PlaylistSummary addTrackToPlaylist(
            int kind,
            YandexMusicClient.Track track
    ) throws Exception {
        long uid = account().uid;
        YandexMusicClient.PlaylistSummary playlist = client().addTrackToPlaylist(uid, kind, track);
        Diagnostics.log(context, "YMP added track to Yandex playlist: playlist=" + kind + ", track=" + track.key);
        return playlist;
    }

    public synchronized YandexMusicClient.PlaylistSummary createPlaylistAndAddTrack(
            String title,
            YandexMusicClient.Track track
    ) throws Exception {
        long uid = account().uid;
        YandexMusicClient.PlaylistSummary playlist = client().createPlaylist(uid, title);
        YandexMusicClient.PlaylistSummary updated = client().addTrackToPlaylist(uid, playlist.kind, track);
        Diagnostics.log(context, "YMP created Yandex playlist and added track: playlist="
                + playlist.kind + ", track=" + track.key);
        return updated;
    }

    public synchronized boolean deletePlaylist(int kind) throws Exception {
        long uid = account().uid;
        boolean deleted = client().deletePlaylist(uid, kind);
        Diagnostics.log(context, "YMP deleted Yandex playlist: playlist=" + kind);
        return deleted;
    }

    public synchronized List<LocalPlaylistStore.LocalPlaylist> localPlaylists() {
        return localPlaylists.list();
    }

    public synchronized List<YandexMusicClient.Track> localPlaylistTracks(String playlistId) {
        LocalPlaylistStore.LocalPlaylist playlist = localPlaylists.get(playlistId);
        List<YandexMusicClient.Track> tracks = new ArrayList<>();
        if (playlist == null) {
            return tracks;
        }
        int order = 1;
        for (LocalPlaylistStore.LocalTrack track : playlist.tracks) {
            tracks.add(new YandexMusicClient.Track(
                    "",
                    "",
                    LocalPlaylistStore.LOCAL_TRACK_PREFIX + track.uri,
                    track.title,
                    track.artist,
                    track.album,
                    0,
                    track.durationMs,
                    "",
                    order++
            ));
        }
        return tracks;
    }

    public synchronized boolean deleteLocalPlaylist(String playlistId) {
        boolean deleted = localPlaylists.delete(playlistId);
        Diagnostics.log(context, "YMP local playlist delete requested: playlist="
                + playlistId + ", deleted=" + deleted);
        return deleted;
    }

    public synchronized LocalPlaylistStore.LocalPlaylist renameLocalPlaylist(String playlistId, String title) {
        LocalPlaylistStore.LocalPlaylist playlist = localPlaylists.rename(playlistId, title);
        Diagnostics.log(context, "YMP local playlist rename requested: playlist="
                + playlistId + ", renamed=" + (playlist != null));
        return playlist;
    }

    public synchronized boolean clearLocalPlaylist(String playlistId) {
        boolean cleared = localPlaylists.clear(playlistId);
        Diagnostics.log(context, "YMP local playlist clear requested: playlist="
                + playlistId + ", cleared=" + cleared);
        return cleared;
    }

    public synchronized LocalPlaylistStore.LocalPlaylist addLocalFolderTracks(
            String playlistId,
            Uri treeUri,
            List<LocalPlaylistStore.LocalTrack> tracks
    ) {
        LocalPlaylistStore.LocalPlaylist playlist = localPlaylists.addFolderTracks(playlistId, treeUri, tracks);
        Diagnostics.log(context, "YMP local folder tracks added: playlist="
                + playlistId + ", folder=" + treeUri + ", tracks=" + (tracks == null ? 0 : tracks.size()));
        return playlist;
    }

    public synchronized LocalPlaylistStore.RefreshResult refreshLocalPlaylistFolders(String playlistId) throws IOException {
        LocalPlaylistStore.RefreshResult result = localPlaylists.refreshFolders(playlistId);
        Diagnostics.log(context, "YMP local playlist folders refreshed: playlist="
                + playlistId
                + ", folders=" + result.folderCount
                + ", tracks=" + result.trackCount
                + ", failed=" + result.failedFolders);
        return result;
    }

    public synchronized boolean removeLocalPlaylistTrack(String playlistId, String trackKeyOrUri) {
        boolean removed = localPlaylists.removeTrack(playlistId, trackKeyOrUri);
        Diagnostics.log(context, "YMP local playlist track remove requested: playlist="
                + playlistId + ", track=" + trackKeyOrUri + ", removed=" + removed);
        return removed;
    }

    public synchronized boolean isLocalFavorite(YandexMusicClient.Track track) {
        return track != null && localPlaylists.isLocalFavorite(track.key);
    }

    public synchronized Set<String> localFavoriteTrackKeys() {
        return new HashSet<>(localPlaylists.localFavoriteTrackKeys());
    }

    public synchronized boolean toggleLocalFavorite(YandexMusicClient.Track track) {
        if (track == null || !LocalPlaylistStore.isLocalTrackKey(track.key)) {
            return false;
        }
        if (localPlaylists.isLocalFavorite(track.key)) {
            localPlaylists.removeFromLocalFavorites(track.key);
            Diagnostics.log(context, "YMP removed local favorite: " + track.key);
            return false;
        }
        LocalPlaylistStore.LocalTrack localTrack = new LocalPlaylistStore.LocalTrack(
                LocalPlaylistStore.uriString(track.key),
                track.title,
                track.artist,
                track.album,
                "",
                0L,
                track.durationMs
        );
        localPlaylists.addToLocalFavorites(localTrack);
        Diagnostics.log(context, "YMP added local favorite: " + track.key);
        return true;
    }

    public synchronized boolean removeLocalFavorite(YandexMusicClient.Track track) {
        if (track == null || !LocalPlaylistStore.isLocalTrackKey(track.key)) {
            return false;
        }
        boolean removed = localPlaylists.removeFromLocalFavorites(track.key);
        Diagnostics.log(context, "YMP removed local favorite via dislike: "
                + track.key + ", removed=" + removed);
        return removed;
    }

    public synchronized Set<String> likedTrackKeys() throws Exception {
        return new HashSet<>(client().getLikedTrackKeys(account().uid));
    }

    public synchronized CacheSyncResult syncFavoriteCache(CacheProgress progress) throws Exception {
        Diagnostics.log(context, "YMP favorite cache sync started");
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
        int coversDownloaded = 0;
        int coverFailures = 0;
        int index = 1;
        for (YandexMusicClient.Track track : unique.values()) {
            if (isCancelled(progress)) {
                CacheSyncResult result = new CacheSyncResult(
                        unique.size(),
                        downloaded,
                        skipped,
                        failed,
                        removed,
                        coversDownloaded,
                        coverFailures,
                        true
                );
                Diagnostics.log(context, result.summaryText());
                return result;
            }
            boolean alreadyCached = audioCache.hasLikedTrack(track.key);
            notifyProgress(progress, (alreadyCached ? "Checking " : "Downloading ")
                    + index + "/" + unique.size() + ": "
                    + track.artist + " - " + track.title);
            try {
                YandexTrackCache.ArtworkSyncResult artwork = audioCache.cacheLiked(client, track);
                if (alreadyCached) {
                    skipped++;
                } else {
                    downloaded++;
                }
                if (artwork == YandexTrackCache.ArtworkSyncResult.DOWNLOADED) {
                    coversDownloaded++;
                    notifyProgress(progress, "Artwork restored " + index + "/" + unique.size() + ": "
                            + track.artist + " - " + track.title);
                } else if (artwork == YandexTrackCache.ArtworkSyncResult.FAILED) {
                    coverFailures++;
                }
            } catch (Exception ex) {
                failed++;
                Diagnostics.log(context, "YMP unable to cache liked track " + track.key, ex);
                notifyProgress(progress, "Failed " + index + "/" + unique.size() + ": "
                        + track.title + " (" + ex.getMessage() + ")");
            }
            index++;
        }

        CacheSyncResult result = new CacheSyncResult(
                unique.size(),
                downloaded,
                skipped,
                failed,
                removed,
                coversDownloaded,
                coverFailures,
                false
        );
        Diagnostics.log(context, result.summaryText());
        return result;
    }

    public synchronized ParcelFileDescriptor openForPlayback(YandexMusicClient.Track track) throws Exception {
        if (track != null && LocalPlaylistStore.isLocalTrackKey(track.key)) {
            Uri uri = LocalPlaylistStore.uriFromTrackKey(track.key);
            ParcelFileDescriptor pfd = uri == null ? null : context.getContentResolver().openFileDescriptor(uri, "r");
            if (pfd == null) {
                throw new IllegalStateException("Unable to open local track");
            }
            return pfd;
        }
        return audioCache.openPlayback(client(), track);
    }

    public synchronized void prefetchForPlayback(YandexMusicClient.Track track) throws Exception {
        audioCache.prefetchPlayback(client(), track);
    }

    public synchronized void like(YandexMusicClient.Track track) throws Exception {
        like(track, false);
    }

    public synchronized void like(YandexMusicClient.Track track, boolean autoCache) throws Exception {
        long uid = account().uid;
        try {
            client().removeDislikedTrack(uid, track.key);
        } catch (Exception ex) {
            Diagnostics.log(context, "YMP remove dislike before like ignored: " + track.key + ", " + ex.getMessage());
        }
        client().likeTrack(uid, track.key);
        if (autoCache) {
            audioCache.cacheLiked(client(), track);
        }
        Diagnostics.log(context, "YMP liked track: " + track.key + ", autoCache=" + autoCache);
    }

    public synchronized void removeLike(YandexMusicClient.Track track) throws Exception {
        long uid = account().uid;
        client().removeLikedTrack(uid, track.key);
        audioCache.removeLikedTrack(track.key);
        Diagnostics.log(context, "YMP removed liked track: " + track.key);
    }

    public synchronized void dislike(YandexMusicClient.Track track) throws Exception {
        long uid = account().uid;
        client().dislikeTrack(uid, track.key);
        try {
            client().removeLikedTrack(uid, track.key);
        } catch (Exception ex) {
            Diagnostics.log(context, "YMP remove like after dislike ignored: " + track.key + ", " + ex.getMessage());
        }
        audioCache.removeTrackEverywhere(track.key);
        Diagnostics.log(context, "YMP disliked track and removed local files: " + track.key);
    }

    public synchronized String cacheStatusText() {
        YandexTrackCache.Summary liked = audioCache.likedSummary();
        YandexTrackCache.Summary playback = audioCache.playbackSummary();
        return "Liked cache: " + liked.count + " tracks, "
                + formatBytes(liked.bytes + liked.coverBytes)
                + " (covers " + formatBytes(liked.coverBytes) + ")"
                + "\nPlayback cache: " + playback.count + " tracks, " + formatBytes(playback.bytes)
                + " / " + formatBytes(YandexTrackCache.PLAYBACK_CACHE_LIMIT_BYTES);
    }

    public synchronized String clearLocalCache() {
        YandexTrackCache.Summary removed = audioCache.clearAllCache();
        File covers = new File(context.getCacheDir(), "covers");
        long coverBytes = directoryBytes(covers);
        deleteRecursively(covers);
        if (removed.count == 0 && removed.coverBytes == 0L && coverBytes == 0L) {
            return "Local cache was already empty";
        }
        return "Local cache cleared: removed " + removed.count + " tracks, "
                + formatBytes(removed.bytes) + " audio, "
                + formatBytes(removed.coverBytes + coverBytes) + " covers";
    }

    private YandexMusicClient client() {
        return new YandexMusicClient(TokenStore.getAccessToken(context));
    }

    private static void addUnique(Map<String, YandexMusicClient.Track> target, List<YandexMusicClient.Track> tracks) {
        if (target == null || tracks == null) {
            return;
        }
        for (YandexMusicClient.Track track : tracks) {
            if (track != null && track.key != null && !track.key.isEmpty()) {
                target.put(track.key, track);
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

    private static String safe(String value) {
        if (value == null || value.isEmpty()) {
            return "none";
        }
        return value.length() <= 12 ? value : value.substring(0, 12) + "...";
    }

    public static final class WaveQueue {
        public final List<YandexMusicClient.Track> tracks = new ArrayList<>();
        public final Map<String, String> batchIdByTrackKey = new LinkedHashMap<>();
        public String sessionId;
        public String batchId;

        WaveQueue(
                List<YandexMusicClient.Track> tracks,
                String sessionId,
                String batchId,
                Map<String, String> batchIdByTrackKey
        ) {
            append(tracks, sessionId, batchId, batchIdByTrackKey);
        }

        void append(
                List<YandexMusicClient.Track> newTracks,
                String newSessionId,
                String newBatchId,
                Map<String, String> newBatchIds
        ) {
            if (newSessionId != null && !newSessionId.isEmpty()) {
                sessionId = newSessionId;
            }
            if (newBatchId != null && !newBatchId.isEmpty()) {
                batchId = newBatchId;
            }
            if (newBatchIds != null) {
                batchIdByTrackKey.putAll(newBatchIds);
            }
            if (newTracks == null) {
                return;
            }
            for (YandexMusicClient.Track track : newTracks) {
                if (track == null || contains(track.key)) {
                    continue;
                }
                tracks.add(track);
            }
        }

        String currentCursorTrackId() {
            if (tracks.isEmpty()) {
                return "";
            }
            return tracks.get(Math.max(0, tracks.size() - 1)).id;
        }

        String batchIdFor(YandexMusicClient.Track track) {
            if (track == null) {
                return "";
            }
            String value = batchIdByTrackKey.get(track.key);
            if (value == null || value.isEmpty()) {
                value = batchIdByTrackKey.get(track.id);
            }
            if (value == null || value.isEmpty()) {
                value = batchId;
            }
            return value == null ? "" : value;
        }

        private boolean contains(String key) {
            for (YandexMusicClient.Track track : tracks) {
                if (track.key.equals(key)) {
                    return true;
                }
            }
            return false;
        }
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
        public final int coversDownloaded;
        public final int coverFailures;
        public final boolean cancelled;

        CacheSyncResult(
                int total,
                int downloaded,
                int skipped,
                int failed,
                int removed,
                int coversDownloaded,
                int coverFailures,
                boolean cancelled
        ) {
            this.total = total;
            this.downloaded = downloaded;
            this.skipped = skipped;
            this.failed = failed;
            this.removed = removed;
            this.coversDownloaded = coversDownloaded;
            this.coverFailures = coverFailures;
            this.cancelled = cancelled;
        }

        public String summaryText() {
            String status = cancelled ? "Cache sync cancelled" : "Cache sync complete";
            return status + ": favorites " + total
                    + ", downloaded " + downloaded
                    + ", already cached " + skipped
                    + ", covers restored " + coversDownloaded
                    + ", cover failures " + coverFailures
                    + ", removed non-favorites " + removed
                    + ", failed " + failed;
        }
    }
}
