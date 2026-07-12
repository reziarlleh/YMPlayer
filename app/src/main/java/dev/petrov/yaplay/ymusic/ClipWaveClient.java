package dev.petrov.yaplay.ymusic;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Client for the video-clip rotor exposed by the Yandex Music Android TV SDK. */
public final class ClipWaveClient {
    public static final String VIDEO_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; YMPlayer) YandexMusicAndroidTVKPHD/2.256.1";

    private static final String API_BASE = "https://api.music.yandex.net";
    private static final String VH_BASE = "https://frontend.vh.yandex.ru/player/";
    private static final String TV_CLIENT = "YandexMusicAndroidTVKPHD";
    private static final String TV_SERVICE = "ya-music";

    private final String accessToken;

    public ClipWaveClient(String accessToken) {
        this.accessToken = accessToken == null ? "" : accessToken.trim();
    }

    public ClipSession startSession() throws IOException, JSONException {
        requireToken();
        JSONObject body = new JSONObject();
        body.put("supportedTypes", new JSONArray().put("clip"));
        body.put("queue", new JSONArray());

        JSONObject result = unwrap(requestJson(
                "POST",
                API_BASE + "/rotor/combined/session/new",
                body,
                true
        ));
        String sessionId = result.optString("sessionId", "");
        if (sessionId.isEmpty()) {
            throw new IOException("Clip Wave did not return a session id");
        }
        return new ClipSession(
                sessionId,
                parseClips(result.optJSONArray("list"), result.optString("batchId", "")),
                result.optBoolean("pumpkin", false)
        );
    }

    public ClipBatch loadNext(String sessionId, List<String> queueClipIds) throws IOException, JSONException {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IOException("Clip Wave session is not initialized");
        }
        JSONArray queue = new JSONArray();
        if (queueClipIds != null) {
            for (String id : queueClipIds) {
                if (id == null || id.trim().isEmpty()) {
                    continue;
                }
                queue.put(new JSONObject().put("type", "clip").put("id", id.trim()));
            }
        }
        JSONObject body = new JSONObject().put("queue", queue);
        JSONObject result = unwrap(requestJson(
                "POST",
                API_BASE + "/rotor/combined/session/" + pathSegment(sessionId) + "/next",
                body,
                true
        ));
        return new ClipBatch(
                parseClips(result.optJSONArray("list"), result.optString("batchId", "")),
                result.optBoolean("pumpkin", false)
        );
    }

    public StreamInfo resolveStream(Clip clip) throws IOException, JSONException {
        if (clip == null) {
            throw new IOException("Clip is empty");
        }
        if (clip.playerId.isEmpty()) {
            return previewOrThrow(clip, "Clip has no player id");
        }

        JSONObject response;
        try {
            String url = VH_BASE + pathSegment(clip.playerId) + ".json"
                    + "?service=" + urlValue(TV_SERVICE)
                    + "&from=" + urlValue(TV_CLIENT);
            response = requestJson("GET", url, null, true);
        } catch (IOException | JSONException ex) {
            if (!clip.previewUrl.isEmpty()) {
                return StreamInfo.preview(clip.previewUrl, ex.getMessage());
            }
            throw ex;
        }

        JSONObject content = response.optJSONObject("content");
        if (content == null) {
            JSONObject result = response.optJSONObject("result");
            content = result == null ? null : result.optJSONObject("content");
        }
        if (content == null) {
            String error = firstNonEmpty(response.optString("error", ""), response.optString("error_cause", ""));
            return previewOrThrow(clip, error.isEmpty() ? "VH response has no content" : error);
        }

        JSONObject episode = content.optJSONObject("actual_episode");
        StreamInfo selected = episode == null
                ? null
                : selectUnprotectedStream(episode.optJSONArray("streams"));
        if (selected == null) {
            selected = selectUnprotectedStream(content.optJSONArray("streams"));
        }
        if (selected != null) {
            return selected;
        }

        String contentUrl = normalizeHttpUrl(content.optString("content_url", ""));
        if (!contentUrl.isEmpty()) {
            return StreamInfo.fromUrl(contentUrl, false, "content_url");
        }

        boolean drmOnly = hasProtectedStreams(content.optJSONArray("streams"));
        drmOnly |= episode != null && hasProtectedStreams(episode.optJSONArray("streams"));
        return previewOrThrow(clip, drmOnly
                ? "Only DRM-protected streams are available"
                : "VH response has no playable stream");
    }

    public void sendQueueStarted(String sessionId, String batchId) throws IOException, JSONException {
        JSONObject event = baseEvent("combinedQueueStarted").put("from", TV_CLIENT);
        sendFeedback(sessionId, batchId, event);
    }

    public void sendClipStarted(String sessionId, Clip clip) throws IOException, JSONException {
        sendFeedback(sessionId, clip == null ? "" : clip.batchId,
                playableEvent("playableItemStarted", clip, -1f));
    }

    public void sendClipFinished(String sessionId, Clip clip, float playedSeconds) throws IOException, JSONException {
        sendFeedback(sessionId, clip == null ? "" : clip.batchId,
                playableEvent("playableItemFinished", clip, Math.max(0f, playedSeconds)));
    }

    public void sendClipSkipped(String sessionId, Clip clip, float playedSeconds) throws IOException, JSONException {
        sendFeedback(sessionId, clip == null ? "" : clip.batchId,
                playableEvent("playableItemSkip", clip, Math.max(0f, playedSeconds)));
    }

    private void sendFeedback(String sessionId, String batchId, JSONObject event) throws IOException, JSONException {
        if (sessionId == null || sessionId.isEmpty() || event == null) {
            return;
        }
        JSONObject body = new JSONObject().put("event", event);
        if (batchId != null && !batchId.isEmpty()) {
            body.put("batchId", batchId);
        }
        requestText(
                "POST",
                API_BASE + "/rotor/session/" + pathSegment(sessionId) + "/feedback",
                body,
                true
        );
    }

    private static JSONObject playableEvent(String type, Clip clip, float playedSeconds) throws JSONException {
        if (clip == null || clip.clipId.isEmpty()) {
            throw new JSONException("Clip feedback has no clip id");
        }
        JSONObject event = baseEvent(type)
                .put("playable", new JSONObject().put("type", "clip").put("id", clip.clipId));
        if (playedSeconds >= 0f) {
            event.put("totalPlayedSeconds", playedSeconds);
        }
        return event;
    }

    private static JSONObject baseEvent(String type) throws JSONException {
        return new JSONObject()
                .put("type", type)
                .put("timestamp", Instant.now().toString());
    }

    private StreamInfo previewOrThrow(Clip clip, String reason) throws IOException {
        if (clip != null && !clip.previewUrl.isEmpty()) {
            return StreamInfo.preview(clip.previewUrl, reason);
        }
        throw new IOException(reason == null || reason.isEmpty() ? "Clip is not playable" : reason);
    }

    private static StreamInfo selectUnprotectedStream(JSONArray streams) {
        if (streams == null) {
            return null;
        }
        StreamInfo hls = null;
        StreamInfo dash = null;
        StreamInfo fallback = null;
        for (int i = 0; i < streams.length(); i++) {
            JSONObject stream = streams.optJSONObject(i);
            if (stream == null || isProtected(stream)) {
                continue;
            }
            String url = normalizeHttpUrl(stream.optString("url", ""));
            if (url.isEmpty()) {
                continue;
            }
            String type = stream.optString("stream_type", "").trim().toLowerCase(Locale.US);
            StreamInfo info = StreamInfo.fromUrl(url, false, type);
            if (type.contains("hls")) {
                hls = info;
            } else if (type.contains("dash")) {
                dash = info;
            } else {
                fallback = info;
            }
        }
        return hls != null ? hls : dash != null ? dash : fallback;
    }

    private static boolean hasProtectedStreams(JSONArray streams) {
        if (streams == null) {
            return false;
        }
        for (int i = 0; i < streams.length(); i++) {
            JSONObject stream = streams.optJSONObject(i);
            if (stream != null && isProtected(stream) && !stream.optString("url", "").isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isProtected(JSONObject stream) {
        JSONObject drm = stream.optJSONObject("drmConfig");
        if (drm == null) {
            drm = stream.optJSONObject("drm_config");
        }
        return drm != null && drm.length() > 0;
    }

    private static List<Clip> parseClips(JSONArray list, String batchId) {
        if (list == null || list.length() == 0) {
            return Collections.emptyList();
        }
        List<Clip> clips = new ArrayList<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String type = item.optString("type", "clip");
            if (!type.isEmpty() && !"clip".equalsIgnoreCase(type)) {
                continue;
            }
            JSONObject data = item.optJSONObject("data");
            if (data == null && item.has("clipId")) {
                data = item;
            }
            Clip clip = parseClip(data, batchId);
            if (clip != null) {
                clips.add(clip);
            }
        }
        return clips;
    }

    private static Clip parseClip(JSONObject data, String batchId) {
        if (data == null) {
            return null;
        }
        String clipId = data.optString("clipId", "").trim();
        if (clipId.isEmpty()) {
            return null;
        }
        List<String> trackIds = new ArrayList<>();
        JSONArray trackArray = data.optJSONArray("trackIds");
        if (trackArray != null) {
            for (int i = 0; i < trackArray.length(); i++) {
                String id = trackArray.optString(i, "").trim();
                if (!id.isEmpty()) {
                    trackIds.add(id);
                }
            }
        }

        StringBuilder artists = new StringBuilder();
        JSONArray artistArray = data.optJSONArray("artists");
        if (artistArray != null) {
            for (int i = 0; i < artistArray.length(); i++) {
                JSONObject artist = artistArray.optJSONObject(i);
                String name = artist == null ? "" : artist.optString("name", "").trim();
                if (name.isEmpty()) {
                    continue;
                }
                if (artists.length() > 0) {
                    artists.append(", ");
                }
                artists.append(name);
            }
        }
        return new Clip(
                clipId,
                data.optString("title", "").trim(),
                data.optString("playerId", "").trim(),
                normalizeArtworkUrl(data.optString("thumbnail", "")),
                normalizeHttpUrl(data.optString("previewUrl", "")),
                Math.max(0L, data.optLong("duration", 0L)),
                trackIds,
                artists.toString(),
                batchId == null ? "" : batchId
        );
    }

    private JSONObject requestJson(String method, String url, JSONObject body, boolean auth)
            throws IOException, JSONException {
        String text = requestText(method, url, body, auth).trim();
        if (text.isEmpty()) {
            return new JSONObject();
        }
        return new JSONObject(text);
    }

    private String requestText(String method, String url, JSONObject body, boolean auth) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(12_000);
        connection.setReadTimeout(25_000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Accept-Language", "ru");
        connection.setRequestProperty("User-Agent", VIDEO_USER_AGENT);
        connection.setRequestProperty("X-Yandex-Music-Client", TV_CLIENT + "/2.256.1");
        if (auth && !accessToken.isEmpty()) {
            connection.setRequestProperty("Authorization", "OAuth " + accessToken);
        }
        if (body != null) {
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }
        }
        try {
            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String text = new String(readAll(stream), StandardCharsets.UTF_8);
            if (status < 200 || status > 299) {
                throw new IOException("HTTP " + status + ": " + compactError(text));
            }
            return text;
        } finally {
            connection.disconnect();
        }
    }

    private void requireToken() throws IOException {
        if (accessToken.isEmpty()) {
            throw new IOException("Yandex OAuth token is missing");
        }
    }

    private static JSONObject unwrap(JSONObject response) throws JSONException {
        Object result = response.opt("result");
        if (result instanceof JSONObject) {
            return (JSONObject) result;
        }
        return response;
    }

    private static byte[] readAll(InputStream stream) throws IOException {
        if (stream == null) {
            return new byte[0];
        }
        try (BufferedInputStream input = new BufferedInputStream(stream);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static String pathSegment(String value) throws IOException {
        return URLEncoder.encode(value == null ? "" : value, "UTF-8").replace("+", "%20");
    }

    private static String urlValue(String value) throws IOException {
        return URLEncoder.encode(value == null ? "" : value, "UTF-8");
    }

    private static String normalizeArtworkUrl(String value) {
        String url = value == null ? "" : value.trim();
        if (url.isEmpty()) {
            return "";
        }
        if (url.startsWith("//")) {
            url = "https:" + url;
        } else if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        return url.replace("%%", "1280x720");
    }

    private static String normalizeHttpUrl(String value) {
        String url = value == null ? "" : value.trim();
        if (url.startsWith("//")) {
            return "https:" + url;
        }
        return url.startsWith("http://") || url.startsWith("https://") ? url : "";
    }

    private static String compactError(String value) {
        String error = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').trim();
        return error.length() > 500 ? error.substring(0, 500) : error;
    }

    private static String firstNonEmpty(String first, String second) {
        return first != null && !first.trim().isEmpty() ? first.trim()
                : second == null ? "" : second.trim();
    }

    public static final class ClipSession {
        public final String sessionId;
        public final List<Clip> clips;
        public final boolean pumpkin;

        ClipSession(String sessionId, List<Clip> clips, boolean pumpkin) {
            this.sessionId = sessionId;
            this.clips = clips == null ? Collections.emptyList() : clips;
            this.pumpkin = pumpkin;
        }
    }

    public static final class ClipBatch {
        public final List<Clip> clips;
        public final boolean pumpkin;

        ClipBatch(List<Clip> clips, boolean pumpkin) {
            this.clips = clips == null ? Collections.emptyList() : clips;
            this.pumpkin = pumpkin;
        }
    }

    public static final class Clip {
        public final String clipId;
        public final String title;
        public final String playerId;
        public final String thumbnailUrl;
        public final String previewUrl;
        public final long duration;
        public final List<String> trackIds;
        public final String artist;
        public final String batchId;

        Clip(
                String clipId,
                String title,
                String playerId,
                String thumbnailUrl,
                String previewUrl,
                long duration,
                List<String> trackIds,
                String artist,
                String batchId
        ) {
            this.clipId = clipId == null ? "" : clipId;
            this.title = title == null || title.isEmpty() ? "Clip Wave" : title;
            this.playerId = playerId == null ? "" : playerId;
            this.thumbnailUrl = thumbnailUrl == null ? "" : thumbnailUrl;
            this.previewUrl = previewUrl == null ? "" : previewUrl;
            this.duration = duration;
            this.trackIds = trackIds == null
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(trackIds));
            this.artist = artist == null ? "" : artist;
            this.batchId = batchId == null ? "" : batchId;
        }

        public String primaryTrackId() {
            return trackIds.isEmpty() ? "" : trackIds.get(0);
        }
    }

    public static final class StreamInfo {
        public final String url;
        public final String mimeType;
        public final boolean preview;
        public final String note;

        private StreamInfo(String url, String mimeType, boolean preview, String note) {
            this.url = url == null ? "" : url;
            this.mimeType = mimeType == null ? "" : mimeType;
            this.preview = preview;
            this.note = note == null ? "" : note;
        }

        static StreamInfo preview(String url, String reason) {
            return new StreamInfo(url, mimeFromUrl(url, ""), true, reason);
        }

        static StreamInfo fromUrl(String url, boolean preview, String type) {
            return new StreamInfo(url, mimeFromUrl(url, type), preview, type);
        }

        private static String mimeFromUrl(String url, String type) {
            String source = ((type == null ? "" : type) + " " + (url == null ? "" : url))
                    .toLowerCase(Locale.US);
            if (source.contains("hls") || source.contains(".m3u8")) {
                return "application/x-mpegURL";
            }
            if (source.contains("dash") || source.contains(".mpd")) {
                return "application/dash+xml";
            }
            if (source.contains(".mp4")) {
                return "video/mp4";
            }
            return "";
        }
    }
}
