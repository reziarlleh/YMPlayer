package dev.petrov.yaplay.poweramp;

import android.content.res.AssetFileDescriptor;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Point;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import android.provider.MediaStore;

import com.maxmpz.poweramp.player.TrackProviderConsts;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import dev.petrov.yaplay.R;
import dev.petrov.yaplay.Diagnostics;
import dev.petrov.yaplay.ymusic.YandexMusicClient;

public class YaPlayProvider extends DocumentsProvider {
    static final String ROOT_ID = "root";
    static final String SETUP_ID = "setup";
    static final String CACHE_ID = "cache";
    static final String WAVE_ID = "wave";
    static final String LIKED_ID = "liked";

    private static final String PLAYLIST_PREFIX = "playlist|";
    private static final String TRACK_PREFIX = "track|";
    private static final String TRACK_PATH_PREFIX = "track/";
    private static final String TRACK_ID_EXTENSION = ".dynamicurl";
    private static final String DISPLAY_AUDIO_EXTENSION = ".mp3";
    static final String TRACK_STORAGE_LIKED = "liked";
    static final String TRACK_STORAGE_PLAYBACK = "playback";
    static final String TRACK_SOURCE_CACHE = "cache";
    static final String TRACK_SOURCE_LIKED = "liked";
    static final String TRACK_SOURCE_WAVE = "wave";
    static final String TRACK_SOURCE_PLAYLIST = "playlist";
    static final String TRACK_SOURCE_UNKNOWN = "unknown";

    private static final String[] ROOT_COLUMNS = new String[] {
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_TITLE,
            Root.COLUMN_SUMMARY,
            Root.COLUMN_FLAGS,
            Root.COLUMN_ICON,
            Root.COLUMN_DOCUMENT_ID
    };

    private static final String[] DOCUMENT_COLUMNS = new String[] {
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE,
            Document.COLUMN_SUMMARY,
            MediaStore.MediaColumns.TITLE,
            MediaStore.Audio.AudioColumns.DURATION,
            MediaStore.Audio.AudioColumns.ARTIST,
            MediaStore.Audio.AudioColumns.ALBUM,
            MediaStore.Audio.AudioColumns.YEAR,
            TrackProviderConsts.COLUMN_ALBUM_ARTIST,
            MediaStore.Audio.AudioColumns.COMPOSER,
            TrackProviderConsts.COLUMN_GENRE,
            MediaStore.Audio.AudioColumns.TRACK,
            TrackProviderConsts.COLUMN_TRACK_ALT,
            TrackProviderConsts.COLUMN_FLAGS,
            MediaFormat.KEY_SAMPLE_RATE,
            MediaFormat.KEY_CHANNEL_COUNT,
            MediaFormat.KEY_BIT_RATE,
            TrackProviderConsts.COLUMN_BITS_PER_SAMPLE,
            TrackProviderConsts.COLUMN_URL,
            TrackProviderConsts.COLUMN_TRACK_WAVE
    };

    private YandexMusicRepository repository;

    public static Uri treeUri(Context context, String documentId) {
        return DocumentsContract.buildTreeDocumentUri(context.getPackageName() + ".provider", documentId);
    }

    public static String rootDocumentId() {
        return ROOT_ID;
    }

    public static String cacheDocumentId() {
        return CACHE_ID;
    }

    public static String waveDocumentId() {
        return WAVE_ID;
    }

    public static String likedDocumentId() {
        return LIKED_ID;
    }

    @Override
    public boolean onCreate() {
        repository = YandexMusicRepository.get(getContext());
        return true;
    }

    @Override
    public Cursor queryRoots(String[] projection) {
        MatrixCursor cursor = new MatrixCursor(resolveRootProjection(projection));
        MatrixCursor.RowBuilder row = cursor.newRow();
        add(cursor, row, Root.COLUMN_ROOT_ID, "yaplay");
        add(cursor, row, Root.COLUMN_TITLE, "YaPlay");
        add(cursor, row, Root.COLUMN_SUMMARY, "Yandex Music for Poweramp");
        add(cursor, row, Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_IS_CHILD);
        add(cursor, row, Root.COLUMN_ICON, R.mipmap.ic_launcher);
        add(cursor, row, Root.COLUMN_DOCUMENT_ID, ROOT_ID);
        return cursor;
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
        MatrixCursor cursor = new MatrixCursor(isTrackDocumentId(documentId)
                ? resolveTrackProjection(projection)
                : resolveDocumentProjection(projection));
        if (ROOT_ID.equals(documentId)) {
            addFolder(cursor, ROOT_ID, "YaPlay");
        } else if (SETUP_ID.equals(documentId)) {
            addFolder(cursor, SETUP_ID, "Open YaPlay and sign in");
        } else if (CACHE_ID.equals(documentId)) {
            addFolder(cursor, CACHE_ID, "Downloaded Liked Tracks");
        } else if (WAVE_ID.equals(documentId)) {
            addFolder(cursor, WAVE_ID, "My Wave");
        } else if (LIKED_ID.equals(documentId)) {
            addFolder(cursor, LIKED_ID, "Liked Tracks");
        } else if (documentId.startsWith(PLAYLIST_PREFIX)) {
            addFolder(cursor, documentId, playlistTitleFromDocumentId(documentId));
        } else if (isTrackDocumentId(documentId)) {
            try {
                YandexMusicClient.Track track = repository.trackByDocumentId(documentId);
                Diagnostics.log(getContext(), "Provider queryDocument track: id=" + compact(documentId)
                        + ", key=" + track.key
                        + ", projection=" + projectionSummary(projection));
                addTrack(cursor, documentId, track, repository.cachedSize(track.key));
            } catch (Exception ex) {
                Diagnostics.log(getContext(), "Provider queryDocument failed for " + documentId, ex);
                throw new FileNotFoundException(ex.getMessage());
            }
        } else {
            throw new FileNotFoundException(documentId);
        }
        return cursor;
    }

    @Override
    public Cursor queryChildDocuments(String parentDocumentId, String[] projection, String sortOrder) {
        MatrixCursor cursor = new MatrixCursor(resolveDocumentProjection(projection));
        List<YaPlayNode> nodes = repository.children(parentDocumentId);
        Diagnostics.log(getContext(), "Provider queryChildDocuments: parent=" + parentDocumentId
                + ", nodes=" + nodes.size()
                + ", first=" + firstDocumentId(nodes)
                + ", projection=" + projectionSummary(projection));
        for (YaPlayNode node : nodes) {
            if (node.directory) {
                addFolder(cursor, node.documentId, node.title);
            } else {
                addTrack(cursor, node.documentId, node.track, repository.cachedSize(node.track.key));
            }
        }
        return cursor;
    }

    @Override
    public String getDocumentType(String documentId) {
        return isTrackDocumentId(documentId) ? "audio/mpeg" : Document.MIME_TYPE_DIR;
    }

    @Override
    public ParcelFileDescriptor openDocument(String documentId, String mode, CancellationSignal signal) throws FileNotFoundException {
        if (mode == null || !mode.contains("r") || mode.contains("w")) {
            Diagnostics.log(getContext(), "Provider openDocument rejected writable mode for " + documentId);
            throw new FileNotFoundException("YaPlay tracks are read-only");
        }
        try {
            Diagnostics.log(getContext(), "Provider openDocument: source=" + trackSourceFromDocumentId(documentId)
                    + ", storage=" + trackStorageFromDocumentId(documentId)
                    + ", key=" + trackKeyFromDocumentId(documentId));
            return repository.openTrack(documentId);
        } catch (Exception ex) {
            Diagnostics.log(getContext(), "Provider openDocument failed for " + documentId, ex);
            throw new FileNotFoundException(ex.getMessage());
        }
    }

    @Override
    public AssetFileDescriptor openDocumentThumbnail(String documentId, Point sizeHint, CancellationSignal signal) throws FileNotFoundException {
        try {
            return repository.thumbnail(documentId);
        } catch (Exception ex) {
            Diagnostics.log(getContext(), "Provider thumbnail failed for " + documentId, ex);
            throw new FileNotFoundException(ex.getMessage());
        }
    }

    @Override
    public boolean isChildDocument(String parentDocumentId, String documentId) {
        if (ROOT_ID.equals(parentDocumentId)) {
            return SETUP_ID.equals(documentId)
                    || CACHE_ID.equals(documentId)
                    || WAVE_ID.equals(documentId)
                    || LIKED_ID.equals(documentId)
                    || documentId.startsWith(PLAYLIST_PREFIX);
        }
        if (CACHE_ID.equals(parentDocumentId) || WAVE_ID.equals(parentDocumentId) || LIKED_ID.equals(parentDocumentId) || parentDocumentId.startsWith(PLAYLIST_PREFIX)) {
            return isTrackDocumentId(documentId);
        }
        return false;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (TrackProviderConsts.CALL_GET_URL.equals(method)) {
            Bundle result = new Bundle();
            try {
                String documentId = DocumentsContract.getDocumentId(Uri.parse(arg));
                Uri audioUri = YaPlayAudioProvider.uriFor(getContext(), documentId);
                Diagnostics.log(getContext(), "Provider CALL_GET_URL: documentId=" + documentId + ", uri=" + audioUri);
                result.putString(TrackProviderConsts.COLUMN_URL, audioUri.toString());
                return result;
            } catch (Exception ex) {
                Diagnostics.log(getContext(), "Provider CALL_GET_URL failed", ex);
                result.putString(TrackProviderConsts.COLUMN_URL, "");
                result.putString("error", ex.getMessage());
                return result;
            }
        }
        if (TrackProviderConsts.CALL_RESCAN.equals(method)) {
            Diagnostics.log(getContext(), "Provider rescan requested");
            repository.invalidate();
            return Bundle.EMPTY;
        }
        return super.call(method, arg, extras);
    }

    private static void addFolder(MatrixCursor cursor, String documentId, String title) {
        MatrixCursor.RowBuilder row = cursor.newRow();
        add(cursor, row, Document.COLUMN_DOCUMENT_ID, documentId);
        add(cursor, row, Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR);
        add(cursor, row, Document.COLUMN_DISPLAY_NAME, title);
        add(cursor, row, Document.COLUMN_LAST_MODIFIED, System.currentTimeMillis());
        add(cursor, row, Document.COLUMN_FLAGS, 0);
        add(cursor, row, Document.COLUMN_SUMMARY, title);
        add(cursor, row, TrackProviderConsts.COLUMN_FLAGS, TrackProviderConsts.FLAG_HAS_SUBDIRS);
    }

    private static void addTrack(MatrixCursor cursor, String documentId, YandexMusicClient.Track track, long cachedSize) {
        MatrixCursor.RowBuilder row = cursor.newRow();
        add(cursor, row, Document.COLUMN_DOCUMENT_ID, documentId);
        add(cursor, row, Document.COLUMN_MIME_TYPE, "audio/mpeg");
        add(cursor, row, Document.COLUMN_DISPLAY_NAME, audioDisplayName(track));
        add(cursor, row, Document.COLUMN_LAST_MODIFIED, System.currentTimeMillis());
        add(cursor, row, Document.COLUMN_FLAGS, track.coverUrl == null || track.coverUrl.isEmpty() ? 0 : Document.FLAG_SUPPORTS_THUMBNAIL);
        add(cursor, row, Document.COLUMN_SIZE, cachedSize);
        add(cursor, row, Document.COLUMN_SUMMARY, track.artist);
        add(cursor, row, MediaStore.MediaColumns.TITLE, track.title);
        add(cursor, row, MediaStore.Audio.AudioColumns.DURATION, track.durationMs);
        add(cursor, row, MediaStore.Audio.AudioColumns.ARTIST, track.artist);
        add(cursor, row, MediaStore.Audio.AudioColumns.ALBUM, track.album);
        if (track.year > 0) {
            add(cursor, row, MediaStore.Audio.AudioColumns.YEAR, track.year);
        }
        add(cursor, row, TrackProviderConsts.COLUMN_ALBUM_ARTIST, track.artist);
        add(cursor, row, MediaStore.Audio.AudioColumns.COMPOSER, "");
        add(cursor, row, TrackProviderConsts.COLUMN_GENRE, "");
        add(cursor, row, MediaStore.Audio.AudioColumns.TRACK, track.order);
        add(cursor, row, TrackProviderConsts.COLUMN_TRACK_ALT, track.order);
        add(cursor, row, TrackProviderConsts.COLUMN_FLAGS, 0);
        add(cursor, row, MediaFormat.KEY_SAMPLE_RATE, 44100);
        add(cursor, row, MediaFormat.KEY_CHANNEL_COUNT, 2);
        add(cursor, row, MediaFormat.KEY_BIT_RATE, 320000);
        add(cursor, row, TrackProviderConsts.COLUMN_BITS_PER_SAMPLE, 16);
        add(cursor, row, TrackProviderConsts.COLUMN_URL, TrackProviderConsts.DYNAMIC_URL);
        add(cursor, row, TrackProviderConsts.COLUMN_TRACK_WAVE, new byte[0]);
    }

    static String playlistDocumentId(int kind, String title) {
        return PLAYLIST_PREFIX + kind + "|" + Uri.encode(title == null ? "" : title);
    }

    static Integer playlistKindFromDocumentId(String documentId) {
        if (!documentId.startsWith(PLAYLIST_PREFIX)) {
            return null;
        }
        String[] parts = documentId.split("\\|", 3);
        if (parts.length < 2) {
            return null;
        }
        try {
            return Integer.parseInt(parts[1]);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static String playlistTitleFromDocumentId(String documentId) {
        String[] parts = documentId.split("\\|", 3);
        return parts.length >= 3 ? Uri.decode(parts[2]) : "Playlist";
    }

    static String trackDocumentId(String parentDocumentId, String trackKey) {
        String storage = trackStorageForParent(parentDocumentId);
        String sourceKind = trackSourceForParent(parentDocumentId);
        String sourceId = Integer.toHexString(parentDocumentId.hashCode());
        return TRACK_PATH_PREFIX + storage + "/" + sourceKind + "/" + sourceId + "/" + Uri.encode(trackKey) + TRACK_ID_EXTENSION;
    }

    static String trackKeyFromDocumentId(String documentId) {
        if (documentId == null) {
            return null;
        }
        if (documentId.startsWith(TRACK_PATH_PREFIX)) {
            String[] parts = documentId.split("/", 5);
            if (parts.length < 5) {
                return null;
            }
            return Uri.decode(stripTrackExtension(parts[4]));
        }
        if (!documentId.startsWith(TRACK_PREFIX)) {
            return null;
        }

        String[] parts = documentId.split("\\|", 6);
        if (parts.length >= 4 && (TRACK_STORAGE_LIKED.equals(parts[1]) || TRACK_STORAGE_PLAYBACK.equals(parts[1]))) {
            if (parts.length >= 6) {
                String albumId = parts[5];
                return albumId.isEmpty() ? parts[4] : parts[4] + ":" + albumId;
            }
            String albumId = parts.length >= 5 ? parts[4] : "";
            return albumId.isEmpty() ? parts[3] : parts[3] + ":" + albumId;
        }

        String[] legacyParts = documentId.split("\\|", 4);
        if (legacyParts.length < 3) {
            return null;
        }
        String albumId = legacyParts.length >= 4 ? legacyParts[3] : "";
        return albumId.isEmpty() ? legacyParts[2] : legacyParts[2] + ":" + albumId;
    }

    static String trackStorageFromDocumentId(String documentId) {
        if (documentId == null) {
            return TRACK_STORAGE_PLAYBACK;
        }
        if (documentId.startsWith(TRACK_PATH_PREFIX)) {
            String[] parts = documentId.split("/", 5);
            if (parts.length >= 2 && TRACK_STORAGE_LIKED.equals(parts[1])) {
                return TRACK_STORAGE_LIKED;
            }
            return TRACK_STORAGE_PLAYBACK;
        }
        if (!documentId.startsWith(TRACK_PREFIX)) {
            return TRACK_STORAGE_PLAYBACK;
        }

        String[] parts = documentId.split("\\|", 4);
        if (parts.length >= 2 && TRACK_STORAGE_LIKED.equals(parts[1])) {
            return TRACK_STORAGE_LIKED;
        }
        return TRACK_STORAGE_PLAYBACK;
    }

    static String trackSourceFromDocumentId(String documentId) {
        if (documentId == null) {
            return TRACK_SOURCE_UNKNOWN;
        }
        if (documentId.startsWith(TRACK_PATH_PREFIX)) {
            String[] parts = documentId.split("/", 5);
            if (parts.length >= 3 && !parts[2].isEmpty()) {
                return parts[2];
            }
            return TRACK_SOURCE_UNKNOWN;
        }
        if (!documentId.startsWith(TRACK_PREFIX)) {
            return TRACK_SOURCE_UNKNOWN;
        }

        String[] parts = documentId.split("\\|", 6);
        if (parts.length >= 6 && (TRACK_STORAGE_LIKED.equals(parts[1]) || TRACK_STORAGE_PLAYBACK.equals(parts[1]))) {
            return parts[2].isEmpty() ? TRACK_SOURCE_UNKNOWN : parts[2];
        }
        return TRACK_SOURCE_UNKNOWN;
    }

    private static boolean isTrackDocumentId(String documentId) {
        return documentId != null && (documentId.startsWith(TRACK_PATH_PREFIX) || documentId.startsWith(TRACK_PREFIX));
    }

    private static String[] resolveRootProjection(String[] projection) {
        return resolveProjection(projection, ROOT_COLUMNS);
    }

    private static String[] resolveDocumentProjection(String[] projection) {
        return resolveProjection(projection, DOCUMENT_COLUMNS);
    }

    private static String[] resolveTrackProjection(String[] projection) {
        return resolveProjection(projection, DOCUMENT_COLUMNS);
    }

    private static String[] resolveProjection(String[] projection, String[] fallbackColumns) {
        if (projection == null || projection.length == 0) {
            return fallbackColumns;
        }
        List<String> columns = new ArrayList<>();
        for (String column : projection) {
            if (column != null && !column.isEmpty() && !columns.contains(column)) {
                columns.add(column);
            }
        }
        for (String column : fallbackColumns) {
            if (column != null && !column.isEmpty() && !columns.contains(column)) {
                columns.add(column);
            }
        }
        return columns.toArray(new String[0]);
    }

    private static void add(MatrixCursor cursor, MatrixCursor.RowBuilder row, String column, Object value) {
        if (hasColumn(cursor, column)) {
            row.add(column, value);
        }
    }

    private static boolean hasColumn(MatrixCursor cursor, String column) {
        if (cursor == null || column == null) {
            return false;
        }
        for (String existing : cursor.getColumnNames()) {
            if (column.equals(existing)) {
                return true;
            }
        }
        return false;
    }

    private static String stripTrackExtension(String value) {
        if (value == null) {
            return "";
        }
        if (value.endsWith(TRACK_ID_EXTENSION)) {
            return value.substring(0, value.length() - TRACK_ID_EXTENSION.length());
        }
        if (value.endsWith(DISPLAY_AUDIO_EXTENSION)) {
            return value.substring(0, value.length() - DISPLAY_AUDIO_EXTENSION.length());
        }
        return value;
    }

    private static String audioDisplayName(YandexMusicClient.Track track) {
        String displayName = track.displayName();
        if (displayName == null || displayName.trim().isEmpty()) {
            displayName = track.key;
        }
        displayName = sanitizeFileName(displayName);
        return displayName.endsWith(DISPLAY_AUDIO_EXTENSION) ? displayName : displayName + DISPLAY_AUDIO_EXTENSION;
    }

    private static String sanitizeFileName(String value) {
        String sanitized = value
                .replace('\\', '_')
                .replace('/', '_')
                .replace(':', '_')
                .replace('*', '_')
                .replace('?', '_')
                .replace('"', '\'')
                .replace('<', '_')
                .replace('>', '_')
                .replace('|', '_')
                .trim();
        return sanitized.isEmpty() ? "track" : sanitized;
    }

    private static String projectionSummary(String[] projection) {
        if (projection == null) {
            return "default";
        }
        if (projection.length == 0) {
            return "empty";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < projection.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(projection[i]);
            if (builder.length() > 160) {
                builder.append("...");
                break;
            }
        }
        return builder.toString();
    }

    private static String compact(String value) {
        if (value == null || value.length() <= 120) {
            return value;
        }
        return value.substring(0, 117) + "...";
    }

    private static String firstDocumentId(List<YaPlayNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return "none";
        }
        return compact(nodes.get(0).documentId);
    }

    private static String trackStorageForParent(String parentDocumentId) {
        if (CACHE_ID.equals(parentDocumentId) || LIKED_ID.equals(parentDocumentId)) {
            return TRACK_STORAGE_LIKED;
        }
        return TRACK_STORAGE_PLAYBACK;
    }

    private static String trackSourceForParent(String parentDocumentId) {
        if (CACHE_ID.equals(parentDocumentId)) {
            return TRACK_SOURCE_CACHE;
        }
        if (LIKED_ID.equals(parentDocumentId)) {
            return TRACK_SOURCE_LIKED;
        }
        if (WAVE_ID.equals(parentDocumentId)) {
            return TRACK_SOURCE_WAVE;
        }
        if (parentDocumentId.startsWith(PLAYLIST_PREFIX)) {
            return TRACK_SOURCE_PLAYLIST;
        }
        return TRACK_SOURCE_UNKNOWN;
    }
}
