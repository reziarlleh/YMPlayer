package dev.petrov.yaplay.player;

import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import dev.petrov.yaplay.Diagnostics;

public final class LocalPlaylistStore {
    public static final String LOCAL_TRACK_PREFIX = "local:";
    public static final String LOCAL_FAVORITES_ID = "local-favorites";
    public static final String LOCAL_FAVORITES_TITLE = "\u041b\u043e\u043a\u0430\u043b\u044c\u043d\u043e\u0435 \u0438\u0437\u0431\u0440\u0430\u043d\u043d\u043e\u0435";

    private static final String PREFS = "ymp_local_playlists";
    private static final String KEY_PLAYLISTS = "playlists";
    private static final int MAX_FOLDER_TRACKS = 5000;

    private final Context context;

    public LocalPlaylistStore(Context context) {
        this.context = context.getApplicationContext();
    }

    public synchronized List<LocalPlaylist> list() {
        return withSystemPlaylists(readAll());
    }

    public synchronized LocalPlaylist get(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        for (LocalPlaylist playlist : withSystemPlaylists(readAll())) {
            if (id.equals(playlist.id)) {
                return playlist;
            }
        }
        return null;
    }

    public synchronized LocalPlaylist create(String title) {
        String safeTitle = title == null ? "" : title.trim();
        if (safeTitle.isEmpty()) {
            safeTitle = "Local playlist";
        }
        List<LocalPlaylist> playlists = readAll();
        LocalPlaylist playlist = new LocalPlaylist(
                "local-" + UUID.randomUUID().toString(),
                safeTitle,
                new ArrayList<>()
        );
        playlists.add(playlist);
        writeAll(playlists);
        return playlist;
    }

    public synchronized LocalPlaylist addTracks(String playlistId, List<LocalTrack> tracks) {
        List<LocalPlaylist> playlists = withSystemPlaylists(readAll());
        LocalPlaylist updated = null;
        for (int i = 0; i < playlists.size(); i++) {
            LocalPlaylist playlist = playlists.get(i);
            if (!playlist.id.equals(playlistId)) {
                continue;
            }
            Map<String, LocalTrack> unique = new LinkedHashMap<>();
            for (LocalTrack track : playlist.tracks) {
                unique.put(track.uri, track);
            }
            if (tracks != null) {
                for (LocalTrack track : tracks) {
                    if (track != null && track.isPlayable()) {
                        unique.put(track.uri, track);
                    }
                }
            }
            updated = new LocalPlaylist(playlist.id, playlist.title, new ArrayList<>(unique.values()));
            playlists.set(i, updated);
            break;
        }
        if (updated != null) {
            writeAll(playlists);
        }
        return updated;
    }

    public synchronized boolean delete(String playlistId) {
        if (playlistId == null || playlistId.isEmpty() || isLocalFavoritesId(playlistId)) {
            return false;
        }
        List<LocalPlaylist> playlists = readAll();
        boolean removed = false;
        for (int i = playlists.size() - 1; i >= 0; i--) {
            if (playlistId.equals(playlists.get(i).id)) {
                playlists.remove(i);
                removed = true;
            }
        }
        if (removed) {
            writeAll(playlists);
        }
        return removed;
    }

    public synchronized boolean clear(String playlistId) {
        if (playlistId == null || playlistId.isEmpty()) {
            return false;
        }
        List<LocalPlaylist> playlists = withSystemPlaylists(readAll());
        boolean changed = false;
        for (int i = 0; i < playlists.size(); i++) {
            LocalPlaylist playlist = playlists.get(i);
            if (playlistId.equals(playlist.id)) {
                playlists.set(i, new LocalPlaylist(playlist.id, playlist.title, new ArrayList<>()));
                changed = true;
                break;
            }
        }
        if (changed) {
            writeAll(playlists);
        }
        return changed;
    }

    public synchronized boolean addToLocalFavorites(LocalTrack track) {
        if (track == null || !track.isPlayable()) {
            return false;
        }
        addTracks(LOCAL_FAVORITES_ID, single(track));
        return true;
    }

    public synchronized boolean removeFromLocalFavorites(String trackKeyOrUri) {
        String uri = uriString(trackKeyOrUri);
        if (uri.isEmpty()) {
            return false;
        }
        List<LocalPlaylist> playlists = withSystemPlaylists(readAll());
        boolean changed = false;
        for (int i = 0; i < playlists.size(); i++) {
            LocalPlaylist playlist = playlists.get(i);
            if (!LOCAL_FAVORITES_ID.equals(playlist.id)) {
                continue;
            }
            List<LocalTrack> tracks = new ArrayList<>();
            for (LocalTrack track : playlist.tracks) {
                if (!uri.equals(track.uri)) {
                    tracks.add(track);
                } else {
                    changed = true;
                }
            }
            playlists.set(i, new LocalPlaylist(playlist.id, playlist.title, tracks));
            break;
        }
        if (changed) {
            writeAll(playlists);
        }
        return changed;
    }

    public synchronized boolean isLocalFavorite(String trackKeyOrUri) {
        String uri = uriString(trackKeyOrUri);
        if (uri.isEmpty()) {
            return false;
        }
        LocalPlaylist favorites = get(LOCAL_FAVORITES_ID);
        if (favorites == null) {
            return false;
        }
        for (LocalTrack track : favorites.tracks) {
            if (uri.equals(track.uri)) {
                return true;
            }
        }
        return false;
    }

    public synchronized List<String> localFavoriteTrackKeys() {
        List<String> keys = new ArrayList<>();
        LocalPlaylist favorites = get(LOCAL_FAVORITES_ID);
        if (favorites == null) {
            return keys;
        }
        for (LocalTrack track : favorites.tracks) {
            if (track != null && track.isPlayable()) {
                keys.add(LOCAL_TRACK_PREFIX + track.uri);
            }
        }
        return keys;
    }

    public synchronized boolean isEmpty() {
        return readAll().isEmpty();
    }

    public static boolean isLocalFavoritesId(String id) {
        return LOCAL_FAVORITES_ID.equals(id);
    }

    private List<LocalPlaylist> readAll() {
        String json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PLAYLISTS, "[]");
        List<LocalPlaylist> playlists = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                LocalPlaylist playlist = LocalPlaylist.fromJson(array.optJSONObject(i));
                if (playlist != null) {
                    playlists.add(playlist);
                }
            }
        } catch (JSONException ex) {
            Diagnostics.log(context, "YMP local playlist storage parse failed", ex);
        }
        return playlists;
    }

    private void writeAll(List<LocalPlaylist> playlists) {
        JSONArray array = new JSONArray();
        if (playlists != null) {
            for (LocalPlaylist playlist : playlists) {
                array.put(playlist.toJson());
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PLAYLISTS, array.toString())
                .apply();
    }

    private List<LocalPlaylist> withSystemPlaylists(List<LocalPlaylist> playlists) {
        List<LocalPlaylist> result = new ArrayList<>();
        LocalPlaylist favorites = null;
        if (playlists != null) {
            for (LocalPlaylist playlist : playlists) {
                if (playlist == null) {
                    continue;
                }
                if (LOCAL_FAVORITES_ID.equals(playlist.id)) {
                    favorites = new LocalPlaylist(LOCAL_FAVORITES_ID, LOCAL_FAVORITES_TITLE, playlist.tracks);
                } else {
                    result.add(playlist);
                }
            }
        }
        result.add(0, favorites == null
                ? new LocalPlaylist(LOCAL_FAVORITES_ID, LOCAL_FAVORITES_TITLE, new ArrayList<>())
                : favorites);
        return result;
    }

    private static List<LocalTrack> single(LocalTrack track) {
        List<LocalTrack> tracks = new ArrayList<>();
        tracks.add(track);
        return tracks;
    }

    public static List<LocalTrack> tracksFromFileIntent(Context context, Intent data) {
        List<LocalTrack> tracks = new ArrayList<>();
        if (data == null) {
            return tracks;
        }
        ClipData clipData = data.getClipData();
        if (clipData != null) {
            for (int i = 0; i < clipData.getItemCount(); i++) {
                Uri uri = clipData.getItemAt(i).getUri();
                LocalTrack track = trackFromUri(context, uri);
                if (track != null && track.isPlayable()) {
                    tracks.add(track);
                }
            }
        }
        Uri uri = data.getData();
        if (uri != null) {
            LocalTrack track = trackFromUri(context, uri);
            if (track != null && track.isPlayable()) {
                tracks.add(track);
            }
        }
        return dedupe(tracks);
    }

    public static List<LocalTrack> tracksFromTree(Context context, Uri treeUri) throws IOException {
        List<LocalTrack> tracks = new ArrayList<>();
        if (treeUri == null) {
            return tracks;
        }
        try {
            String rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri);
            collectTree(context, treeUri, rootDocumentId, tracks, 0);
        } catch (Exception ex) {
            throw new IOException("Unable to scan selected folder", ex);
        }
        return dedupe(tracks);
    }

    public static boolean isLocalTrackKey(String key) {
        return key != null && key.startsWith(LOCAL_TRACK_PREFIX);
    }

    public static Uri uriFromTrackKey(String key) {
        if (!isLocalTrackKey(key)) {
            return null;
        }
        return Uri.parse(key.substring(LOCAL_TRACK_PREFIX.length()));
    }

    public static String uriString(String trackKeyOrUri) {
        if (trackKeyOrUri == null || trackKeyOrUri.trim().isEmpty()) {
            return "";
        }
        String value = trackKeyOrUri.trim();
        return isLocalTrackKey(value) ? value.substring(LOCAL_TRACK_PREFIX.length()) : value;
    }

    private static void collectTree(
            Context context,
            Uri treeUri,
            String documentId,
            List<LocalTrack> tracks,
            int depth
    ) {
        if (documentId == null || tracks.size() >= MAX_FOLDER_TRACKS || depth > 24) {
            return;
        }
        ContentResolver resolver = context.getContentResolver();
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId);
        String[] projection = new String[] {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE
        };
        try (Cursor cursor = resolver.query(childrenUri, projection, null, null, null)) {
            if (cursor == null) {
                return;
            }
            int idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
            int nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
            int mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE);
            int sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE);
            while (cursor.moveToNext() && tracks.size() < MAX_FOLDER_TRACKS) {
                String childId = idIndex < 0 ? "" : cursor.getString(idIndex);
                String name = nameIndex < 0 ? "" : cursor.getString(nameIndex);
                String mime = mimeIndex < 0 ? "" : cursor.getString(mimeIndex);
                long size = sizeIndex < 0 || cursor.isNull(sizeIndex) ? 0L : cursor.getLong(sizeIndex);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    collectTree(context, treeUri, childId, tracks, depth + 1);
                } else if (isAudio(name, mime)) {
                    Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId);
                    tracks.add(trackFromUri(context, documentUri, name, mime, size));
                }
            }
        } catch (Exception ex) {
            Diagnostics.log(context, "YMP local folder scan skipped branch: " + documentId, ex);
        }
    }

    private static LocalTrack trackFromUri(Context context, Uri uri) {
        if (uri == null) {
            return null;
        }
        String name = "";
        String mime = "";
        long size = 0L;
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (nameIndex >= 0) {
                    name = cursor.getString(nameIndex);
                }
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    size = cursor.getLong(sizeIndex);
                }
            }
        } catch (Exception ignored) {
        }
        try {
            mime = context.getContentResolver().getType(uri);
        } catch (Exception ignored) {
        }
        if (!isAudio(name, mime)) {
            return null;
        }
        return trackFromUri(context, uri, name, mime, size);
    }

    private static LocalTrack trackFromUri(Context context, Uri uri, String name, String mime, long size) {
        Metadata metadata = readMetadata(context, uri);
        String fallbackTitle = cleanTitle(name, uri);
        return new LocalTrack(
                uri.toString(),
                firstNonEmpty(metadata.title, fallbackTitle),
                metadata.artist,
                metadata.album,
                mime,
                size,
                metadata.durationMs
        );
    }

    private static List<LocalTrack> dedupe(List<LocalTrack> tracks) {
        Map<String, LocalTrack> unique = new LinkedHashMap<>();
        for (LocalTrack track : tracks) {
            if (track != null && track.isPlayable()) {
                unique.put(track.uri, track);
            }
        }
        return new ArrayList<>(unique.values());
    }

    private static boolean isAudio(String name, String mime) {
        String safeMime = mime == null ? "" : mime.toLowerCase(Locale.US);
        if (safeMime.startsWith("audio/")) {
            return true;
        }
        String safeName = name == null ? "" : name.toLowerCase(Locale.US);
        return safeName.endsWith(".mp3")
                || safeName.endsWith(".flac")
                || safeName.endsWith(".m4a")
                || safeName.endsWith(".aac")
                || safeName.endsWith(".ogg")
                || safeName.endsWith(".opus")
                || safeName.endsWith(".wav")
                || safeName.endsWith(".wma");
    }

    private static String cleanTitle(String name, Uri fallback) {
        String value = name == null ? "" : name.trim();
        if (value.isEmpty() && fallback != null) {
            value = fallback.getLastPathSegment();
        }
        if (value == null || value.trim().isEmpty()) {
            return "Local track";
        }
        int dot = value.lastIndexOf('.');
        return dot > 0 ? value.substring(0, dot) : value;
    }

    private static Metadata readMetadata(Context context, Uri uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            return new Metadata(
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                    parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION))
            );
        } catch (Exception ignored) {
            return new Metadata("", "", "", 0L);
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
    }

    private static long parseLong(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private static final class Metadata {
        final String title;
        final String artist;
        final String album;
        final long durationMs;

        Metadata(String title, String artist, String album, long durationMs) {
            this.title = title == null ? "" : title.trim();
            this.artist = artist == null ? "" : artist.trim();
            this.album = album == null ? "" : album.trim();
            this.durationMs = Math.max(0L, durationMs);
        }
    }

    public static final class LocalPlaylist {
        public final String id;
        public final String title;
        public final List<LocalTrack> tracks;

        LocalPlaylist(String id, String title, List<LocalTrack> tracks) {
            this.id = id == null ? "" : id;
            this.title = title == null ? "" : title;
            this.tracks = tracks == null ? new ArrayList<>() : tracks;
        }

        public int trackCount() {
            return tracks.size();
        }

        JSONObject toJson() {
            JSONObject object = new JSONObject();
            JSONArray array = new JSONArray();
            try {
                object.put("id", id);
                object.put("title", title);
                for (LocalTrack track : tracks) {
                    array.put(track.toJson());
                }
                object.put("tracks", array);
            } catch (JSONException ignored) {
            }
            return object;
        }

        static LocalPlaylist fromJson(JSONObject object) {
            if (object == null) {
                return null;
            }
            String id = object.optString("id", "");
            String title = object.optString("title", "");
            if (id.isEmpty() || title.isEmpty()) {
                return null;
            }
            List<LocalTrack> tracks = new ArrayList<>();
            JSONArray array = object.optJSONArray("tracks");
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    LocalTrack track = LocalTrack.fromJson(array.optJSONObject(i));
                    if (track != null && track.isPlayable()) {
                        tracks.add(track);
                    }
                }
            }
            return new LocalPlaylist(id, title, tracks);
        }
    }

    public static final class LocalTrack {
        public final String uri;
        public final String title;
        public final String artist;
        public final String album;
        public final String mime;
        public final long size;
        public final long durationMs;

        LocalTrack(String uri, String title, String artist, String album, String mime, long size, long durationMs) {
            this.uri = uri == null ? "" : uri;
            this.title = title == null || title.trim().isEmpty() ? "Local track" : title.trim();
            this.artist = artist == null ? "" : artist.trim();
            this.album = album == null ? "" : album.trim();
            this.mime = mime == null ? "" : mime;
            this.size = Math.max(0L, size);
            this.durationMs = Math.max(0L, durationMs);
        }

        boolean isPlayable() {
            return !uri.isEmpty();
        }

        JSONObject toJson() {
            JSONObject object = new JSONObject();
            try {
                object.put("uri", uri);
                object.put("title", title);
                object.put("artist", artist);
                object.put("album", album);
                object.put("mime", mime);
                object.put("size", size);
                object.put("durationMs", durationMs);
            } catch (JSONException ignored) {
            }
            return object;
        }

        static LocalTrack fromJson(JSONObject object) {
            if (object == null) {
                return null;
            }
            return new LocalTrack(
                    object.optString("uri", ""),
                    object.optString("title", ""),
                    object.optString("artist", ""),
                    object.optString("album", ""),
                    object.optString("mime", ""),
                    object.optLong("size", 0L),
                    object.optLong("durationMs", 0L)
            );
        }
    }
}
