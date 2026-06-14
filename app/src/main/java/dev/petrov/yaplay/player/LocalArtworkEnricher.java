package dev.petrov.yaplay.player;

import android.content.ContentResolver;
import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;
import org.jaudiotagger.tag.images.ArtworkFactory;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import dev.petrov.yaplay.Diagnostics;

public final class LocalArtworkEnricher {
    private static final int MAX_COVER_BYTES = 5 * 1024 * 1024;
    private static final String USER_AGENT = "YMPlayer/0.3 local artwork lookup";

    private LocalArtworkEnricher() {
    }

    public static int enrichMissingArtwork(Context context, List<LocalPlaylistStore.LocalTrack> tracks) {
        if (tracks == null || tracks.isEmpty()) {
            return 0;
        }
        Context appContext = context.getApplicationContext();
        int changed = 0;
        for (LocalPlaylistStore.LocalTrack track : tracks) {
            try {
                if (track == null || track.uri.isEmpty()) {
                    continue;
                }
                Uri uri = Uri.parse(track.uri);
                if (hasEmbeddedArtwork(appContext, uri)) {
                    continue;
                }
                CoverCandidate candidate = findCover(track);
                if (candidate == null || candidate.url.isEmpty()) {
                    continue;
                }
                if (writeArtwork(appContext, track, uri, candidate)) {
                    changed++;
                    Diagnostics.log(appContext, "YMP local artwork embedded: " + track.title
                            + ", source=" + candidate.source);
                }
            } catch (Exception ex) {
                Diagnostics.log(appContext, "YMP local artwork enrichment skipped: "
                        + (track == null ? "" : track.title), ex);
            }
        }
        return changed;
    }

    private static boolean hasEmbeddedArtwork(Context context, Uri uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            byte[] picture = retriever.getEmbeddedPicture();
            return picture != null && picture.length > 0;
        } catch (Exception ex) {
            return false;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
    }

    private static CoverCandidate findCover(LocalPlaylistStore.LocalTrack track) {
        CoverCandidate candidate = findItunesCover(track);
        if (candidate != null) {
            return candidate;
        }
        return findCoverArtArchiveCover(track);
    }

    private static CoverCandidate findItunesCover(LocalPlaylistStore.LocalTrack track) {
        String query = joinTerms(track.artist, track.album, track.title);
        if (query.isEmpty()) {
            return null;
        }
        try {
            String url = "https://itunes.apple.com/search?media=music&entity=song&limit=8&term="
                    + URLEncoder.encode(query, "UTF-8");
            JSONObject json = new JSONObject(httpGetText(url));
            JSONArray results = json.optJSONArray("results");
            if (results == null) {
                return null;
            }
            for (int i = 0; i < results.length(); i++) {
                JSONObject item = results.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String artwork = item.optString("artworkUrl100", "");
                if (!artwork.isEmpty()) {
                    artwork = artwork.replace("100x100bb", "600x600bb");
                    if (artwork.startsWith("http://")) {
                        artwork = "https://" + artwork.substring("http://".length());
                    }
                    return new CoverCandidate(artwork, "itunes");
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static CoverCandidate findCoverArtArchiveCover(LocalPlaylistStore.LocalTrack track) {
        if (track.album.isEmpty() && track.artist.isEmpty()) {
            return null;
        }
        try {
            String query = joinTerms(track.artist, track.album);
            String url = "https://musicbrainz.org/ws/2/release/?fmt=json&limit=5&query="
                    + URLEncoder.encode(query, "UTF-8");
            JSONObject json = new JSONObject(httpGetText(url));
            JSONArray releases = json.optJSONArray("releases");
            if (releases == null) {
                return null;
            }
            for (int i = 0; i < releases.length(); i++) {
                JSONObject release = releases.optJSONObject(i);
                String id = release == null ? "" : release.optString("id", "");
                if (!id.isEmpty()) {
                    return new CoverCandidate("https://coverartarchive.org/release/" + id + "/front-500",
                            "coverartarchive");
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static boolean writeArtwork(
            Context context,
            LocalPlaylistStore.LocalTrack track,
            Uri uri,
            CoverCandidate candidate
    ) throws Exception {
        File workDir = new File(context.getCacheDir(), "local-artwork-work");
        if (!workDir.exists() && !workDir.mkdirs()) {
            return false;
        }
        File audioFile = File.createTempFile("ymp-audio-", extensionFor(track), workDir);
        File coverFile = File.createTempFile("ymp-cover-", ".jpg", workDir);
        try {
            copyUriToFile(context, uri, audioFile);
            byte[] coverBytes = httpGetBytes(candidate.url);
            if (coverBytes.length == 0 || coverBytes.length > MAX_COVER_BYTES) {
                return false;
            }
            try (FileOutputStream out = new FileOutputStream(coverFile)) {
                out.write(coverBytes);
            }

            AudioFile audio = AudioFileIO.read(audioFile);
            Tag tag = audio.getTagOrCreateAndSetDefault();
            Artwork artwork = ArtworkFactory.createArtworkFromFile(coverFile);
            try {
                tag.deleteArtworkField();
            } catch (Exception ignored) {
            }
            tag.setField(artwork);
            audio.commit();
            copyFileToUri(context, audioFile, uri);
            return true;
        } finally {
            deleteQuietly(audioFile);
            deleteQuietly(coverFile);
        }
    }

    private static void copyUriToFile(Context context, Uri uri, File file) throws Exception {
        ContentResolver resolver = context.getContentResolver();
        try (InputStream in = new BufferedInputStream(resolver.openInputStream(uri));
             OutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
            copy(in, out);
        }
    }

    private static void copyFileToUri(Context context, File file, Uri uri) throws Exception {
        ContentResolver resolver = context.getContentResolver();
        OutputStream stream;
        try {
            stream = resolver.openOutputStream(uri, "rwt");
        } catch (Exception ex) {
            stream = resolver.openOutputStream(uri, "w");
        }
        if (stream == null) {
            throw new IllegalStateException("Unable to open local track for writing");
        }
        try (InputStream in = new BufferedInputStream(new java.io.FileInputStream(file));
             OutputStream out = new BufferedOutputStream(stream)) {
            copy(in, out);
        }
    }

    private static String httpGetText(String url) throws Exception {
        return new String(httpGetBytes(url), StandardCharsets.UTF_8);
    }

    private static byte[] httpGetBytes(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status > 299) {
                return new byte[0];
            }
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                int total = 0;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_COVER_BYTES) {
                        return new byte[0];
                    }
                    output.write(buffer, 0, read);
                }
                return output.toByteArray();
            }
        } finally {
            connection.disconnect();
        }
    }

    private static void copy(InputStream in, OutputStream out) throws Exception {
        if (in == null) {
            throw new IllegalStateException("Input stream is not available");
        }
        byte[] buffer = new byte[128 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    private static String extensionFor(LocalPlaylistStore.LocalTrack track) {
        String mime = track.mime == null ? "" : track.mime.toLowerCase(Locale.US);
        if (mime.contains("flac")) {
            return ".flac";
        }
        if (mime.contains("mp4") || mime.contains("m4a") || mime.contains("aac")) {
            return ".m4a";
        }
        if (mime.contains("ogg") || mime.contains("opus")) {
            return ".ogg";
        }
        if (mime.contains("wav")) {
            return ".wav";
        }
        String uri = track.uri == null ? "" : track.uri.toLowerCase(Locale.US);
        int dot = uri.lastIndexOf('.');
        if (dot >= 0 && dot + 2 < uri.length() && uri.length() - dot <= 6) {
            return uri.substring(dot);
        }
        return ".mp3";
    }

    private static String joinTerms(String... values) {
        StringBuilder builder = new StringBuilder();
        if (values != null) {
            for (String value : values) {
                if (value == null || value.trim().isEmpty()) {
                    continue;
                }
                if (builder.length() > 0) {
                    builder.append(' ');
                }
                builder.append(value.trim());
            }
        }
        return builder.toString();
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists()) {
            try {
                file.delete();
            } catch (Exception ignored) {
            }
        }
    }

    private static final class CoverCandidate {
        final String url;
        final String source;

        CoverCandidate(String url, String source) {
            this.url = url == null ? "" : url;
            this.source = source == null ? "" : source;
        }
    }
}
