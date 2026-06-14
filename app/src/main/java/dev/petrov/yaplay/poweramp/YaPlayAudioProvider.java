package dev.petrov.yaplay.poweramp;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.util.Base64;

import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;

import dev.petrov.yaplay.Diagnostics;
import dev.petrov.yaplay.ymusic.YandexMusicClient;

public class YaPlayAudioProvider extends ContentProvider {
    private static final String MIME_AUDIO = "audio/mpeg";

    private YandexMusicRepository repository;

    public static Uri uriFor(Context context, String documentId) {
        return new Uri.Builder()
                .scheme("content")
                .authority(context.getPackageName() + ".audio")
                .appendPath(encode(documentId))
                .build();
    }

    @Override
    public boolean onCreate() {
        repository = YandexMusicRepository.get(getContext());
        return true;
    }

    @Override
    public String getType(Uri uri) {
        return MIME_AUDIO;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (mode == null || !mode.contains("r") || mode.contains("w")) {
            Diagnostics.log(getContext(), "AudioProvider rejected writable mode for " + uri);
            throw new FileNotFoundException("YaPlay audio is read-only");
        }
        String documentId = decodeFromUri(uri);
        try {
            Diagnostics.log(getContext(), "AudioProvider openFile: documentId=" + documentId);
            return repository.openTrack(documentId);
        } catch (Exception ex) {
            Diagnostics.log(getContext(), "AudioProvider openFile failed for " + documentId, ex);
            throw new FileNotFoundException(ex.getMessage());
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        MatrixCursor cursor = new MatrixCursor(new String[] {
                OpenableColumns.DISPLAY_NAME,
                OpenableColumns.SIZE
        });
        String documentId = decodeFromUri(uri);
        try {
            YandexMusicClient.Track track = repository.trackByDocumentId(documentId);
            cursor.newRow()
                    .add(OpenableColumns.DISPLAY_NAME, track.displayName())
                    .add(OpenableColumns.SIZE, repository.cachedSize(track.key));
        } catch (Exception ex) {
            Diagnostics.log(getContext(), "AudioProvider query failed for " + documentId, ex);
        }
        return cursor;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    private static String encode(String documentId) {
        return Base64.encodeToString(
                documentId.getBytes(StandardCharsets.UTF_8),
                Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING
        );
    }

    private static String decodeFromUri(Uri uri) {
        if (uri == null || uri.getPathSegments().isEmpty()) {
            return "";
        }
        byte[] decoded = Base64.decode(uri.getPathSegments().get(0), Base64.URL_SAFE | Base64.NO_WRAP);
        return new String(decoded, StandardCharsets.UTF_8);
    }
}
