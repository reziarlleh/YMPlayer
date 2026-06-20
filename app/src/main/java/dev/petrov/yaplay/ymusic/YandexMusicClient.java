package dev.petrov.yaplay.ymusic;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.xml.parsers.DocumentBuilderFactory;

public final class YandexMusicClient {
    public static final String MY_WAVE_STATION = "user:onyourwave";

    private static final String API_BASE = "https://api.music.yandex.net";
    private static final String OAUTH_BASE = "https://oauth.yandex.ru";
    private static final String USER_AGENT = "Yandex-Music-API";
    private static final String YM_CLIENT = "YandexMusicAndroid/24023621";
    private static final String DEVICE_CLIENT_ID = "23cabbbdc6cd418abb4b39c32c41195d";
    private static final String DEVICE_CLIENT_SECRET = "53bc75238f0c4d08a118e51fe9203300";
    private static final String SIGN_SALT = "XGRlBW9FXlekgbPrRHuSiA";
    private static final int DEFAULT_WAVE_TARGET_TRACKS = 50;

    private final String accessToken;

    public YandexMusicClient(String accessToken) {
        this.accessToken = accessToken == null ? "" : accessToken.trim();
    }

    public DeviceCode requestDeviceCode() throws IOException, JSONException {
        List<Param> form = new ArrayList<>();
        form.add(new Param("client_id", DEVICE_CLIENT_ID));
        form.add(new Param("device_id", UUID.randomUUID().toString().replace("-", "").substring(0, 12)));
        form.add(new Param("device_name", "YMPlayer"));
        JSONObject json = new JSONObject(request("POST", OAUTH_BASE + "/device/code", form, false));
        return new DeviceCode(
                json.optString("device_code"),
                json.optString("user_code"),
                json.optString("verification_url"),
                json.optLong("expires_in", 300),
                Math.max(2, json.optLong("interval", 5))
        );
    }

    public OAuthToken pollDeviceToken(String deviceCode) throws IOException, JSONException {
        List<Param> form = new ArrayList<>();
        form.add(new Param("grant_type", "device_code"));
        form.add(new Param("code", deviceCode));
        form.add(new Param("client_id", DEVICE_CLIENT_ID));
        form.add(new Param("client_secret", DEVICE_CLIENT_SECRET));
        try {
            JSONObject json = new JSONObject(request("POST", OAUTH_BASE + "/token", form, false));
            String access = json.optString("access_token");
            if (access == null || access.isEmpty()) {
                return null;
            }
            return new OAuthToken(access, json.optString("refresh_token"));
        } catch (IOException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("authorization_pending")) {
                return null;
            }
            throw ex;
        }
    }

    public AccountStatus getAccountStatus() throws IOException, JSONException {
        JSONObject result = asObject(apiGet("/account/status"));
        JSONObject account = result.optJSONObject("account");
        if (account == null) {
            throw new IOException("Yandex Music account status has no account field");
        }
        long uid = account.optLong("uid", 0L);
        String name = firstNonEmpty(account.optString("displayName"), account.optString("login"), String.valueOf(uid));
        return new AccountStatus(uid, name);
    }

    public List<PlaylistSummary> getPlaylists(long uid) throws IOException, JSONException {
        Object result = apiGet("/users/" + uid + "/playlists/list");
        JSONArray array = asArray(result);
        List<PlaylistSummary> playlists = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            PlaylistSummary playlist = parsePlaylistSummary(item);
            if (playlist == null) {
                continue;
            }
            playlists.add(playlist);
        }
        return playlists;
    }

    public PlaylistSummary getPlaylist(long uid, int kind) throws IOException, JSONException {
        return playlistSummaryFromObject(asObject(apiGet("/users/" + uid + "/playlists/" + kind)));
    }

    public SearchResults search(String text) throws IOException, JSONException {
        String query = text == null ? "" : text.trim();
        if (query.isEmpty()) {
            throw new IOException("Search query is empty");
        }
        String path = "/search?type=all&page=0&nocorrect=false&text=" + URLEncoder.encode(query, "UTF-8");
        JSONObject object = asObject(apiGet(path));
        return parseSearchResults(query, object);
    }

    public List<Track> getAlbumTracks(String albumId) throws IOException, JSONException {
        String safeId = albumId == null ? "" : albumId.trim();
        if (safeId.isEmpty()) {
            throw new IOException("Album id is empty");
        }
        JSONObject album = asObject(apiGet("/albums/" + URLEncoder.encode(safeId, "UTF-8") + "/with-tracks"));
        String title = album.optString("title", "");
        String artist = joinTitles(album.optJSONArray("artists"));
        String coverUrl = normalizeCoverUrl(album.optString("coverUri", ""));
        JSONArray volumes = album.optJSONArray("volumes");
        List<Track> tracks = new ArrayList<>();
        int order = 1;
        if (volumes != null) {
            for (int i = 0; i < volumes.length(); i++) {
                JSONArray volume = volumes.optJSONArray(i);
                if (volume == null) {
                    continue;
                }
                for (int j = 0; j < volume.length(); j++) {
                    JSONObject item = volume.optJSONObject(j);
                    Track track = parseTrack(item, order, safeId, title, artist, coverUrl);
                    if (track != null) {
                        tracks.add(track);
                        order++;
                    }
                }
            }
        }
        return tracks;
    }

    public List<Track> getArtistTracks(String artistId) throws IOException, JSONException {
        String safeId = artistId == null ? "" : artistId.trim();
        if (safeId.isEmpty()) {
            throw new IOException("Artist id is empty");
        }
        Object result = apiGet("/artists/" + URLEncoder.encode(safeId, "UTF-8") + "/tracks?page=0&page-size=100");
        JSONObject object = asObject(result);
        JSONArray array = object.optJSONArray("tracks");
        if (array == null) {
            array = object.optJSONArray("results");
        }
        List<Track> parsed = parseTrackArray(array);
        if (!parsed.isEmpty()) {
            return parsed;
        }
        JSONArray ids = object.optJSONArray("trackIds");
        List<String> trackIds = new ArrayList<>();
        if (ids != null) {
            for (int i = 0; i < ids.length(); i++) {
                String id = ids.optString(i, "");
                if (!id.isEmpty()) {
                    trackIds.add(id);
                }
            }
        }
        return getTracks(trackIds);
    }

    public PlaylistSummary createPlaylist(long uid, String title) throws IOException, JSONException {
        String safeTitle = title == null ? "" : title.trim();
        if (safeTitle.isEmpty()) {
            throw new IOException("Playlist title is empty");
        }
        List<Param> form = new ArrayList<>();
        form.add(new Param("title", safeTitle));
        form.add(new Param("visibility", "private"));
        return playlistSummaryFromObject(asObject(apiPost("/users/" + uid + "/playlists/create", form)));
    }

    public boolean deletePlaylist(long uid, int kind) throws IOException {
        if (kind < 0) {
            throw new IOException("Playlist kind is invalid");
        }
        List<Param> form = new ArrayList<>();
        form.add(new Param("kind", String.valueOf(kind)));
        try {
            request("POST", API_BASE + "/users/" + uid + "/playlists/" + kind + "/delete", form, true);
        } catch (IOException ex) {
            request("POST", API_BASE + "/users/" + uid + "/playlists/delete", form, true);
        }
        return true;
    }

    public PlaylistSummary addTrackToPlaylist(long uid, int kind, Track track) throws IOException, JSONException {
        if (track == null) {
            throw new IOException("Track is empty");
        }
        PlaylistSummary playlist = getPlaylist(uid, kind);
        int revision = Math.max(1, playlist.revision);
        int at = Math.max(0, playlist.trackCount);
        String diff = insertTrackDiff(track, at);
        List<Param> form = new ArrayList<>();
        form.add(new Param("kind", String.valueOf(kind)));
        form.add(new Param("revision", String.valueOf(revision)));
        form.add(new Param("diff", diff));
        JSONObject changed;
        try {
            changed = asObject(apiPost("/users/" + uid + "/playlists/" + kind + "/change", form));
        } catch (IOException ex) {
            changed = asObject(apiPost("/users/" + uid + "/playlists/" + kind + "/change-relative", form));
        }
        return playlistSummaryFromObject(changed);
    }

    public List<Track> getLikedTracks(long uid) throws IOException, JSONException {
        return getTracks(getLikedTrackKeys(uid));
    }

    public List<String> getLikedTrackKeys(long uid) throws IOException, JSONException {
        Object result = apiGet("/users/" + uid + "/likes/tracks?if-modified-since-revision=0");
        JSONObject object = asObject(result);
        JSONObject library = object.optJSONObject("library");
        JSONArray array = library == null ? null : library.optJSONArray("tracks");
        List<String> ids = new ArrayList<>();
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                String id = item == null ? "" : item.optString("id", "");
                String albumId = item == null ? "" : item.optString("albumId", "");
                if (!id.isEmpty()) {
                    ids.add(albumId.isEmpty() ? id : id + ":" + albumId);
                }
            }
        }
        return ids;
    }

    public boolean likeTrack(long uid, String trackKey) throws IOException, JSONException {
        List<Param> form = new ArrayList<>();
        form.add(new Param("track-ids", trackKey));
        apiPost("/users/" + uid + "/likes/tracks/add-multiple", form);
        return true;
    }

    public boolean removeLikedTrack(long uid, String trackKey) throws IOException, JSONException {
        List<Param> form = new ArrayList<>();
        form.add(new Param("track-ids", trackKey));
        try {
            apiPost("/users/" + uid + "/likes/tracks/remove", form);
        } catch (IOException ex) {
            apiPost("/users/" + uid + "/likes/tracks/" + URLEncoder.encode(trackKey, "UTF-8") + "/remove", form);
        }
        return true;
    }

    public boolean dislikeTrack(long uid, String trackKey) throws IOException, JSONException {
        List<Param> form = new ArrayList<>();
        form.add(new Param("track-ids", trackKey));
        apiPost("/users/" + uid + "/dislikes/tracks/add-multiple", form);
        return true;
    }

    public boolean removeDislikedTrack(long uid, String trackKey) throws IOException, JSONException {
        List<Param> form = new ArrayList<>();
        form.add(new Param("track-ids", trackKey));
        try {
            apiPost("/users/" + uid + "/dislikes/tracks/remove", form);
        } catch (IOException ex) {
            apiPost("/users/" + uid + "/dislikes/tracks/" + URLEncoder.encode(trackKey, "UTF-8") + "/remove", form);
        }
        return true;
    }

    public List<Track> getPlaylistTracks(long uid, int kind) throws IOException, JSONException {
        JSONObject playlist = asObject(apiGet("/users/" + uid + "/playlists/" + kind));
        JSONArray array = playlist.optJSONArray("tracks");
        List<String> ids = new ArrayList<>();
        List<Track> parsed = new ArrayList<>();
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                JSONObject trackObject = item.optJSONObject("track");
                if (trackObject != null) {
                    Track track = parseTrack(trackObject, i + 1);
                    if (track != null) {
                        parsed.add(track);
                    }
                } else {
                    String id = item.optString("id", "");
                    String albumId = item.optString("albumId", "");
                    if (!id.isEmpty()) {
                        ids.add(albumId.isEmpty() ? id : id + ":" + albumId);
                    }
                }
            }
        }
        if (!ids.isEmpty()) {
            parsed.addAll(getTracks(ids));
        }
        return parsed;
    }

    public List<Track> getMyWaveTracks() throws IOException, JSONException {
        return getMyWave().tracks;
    }

    public WaveTracks getMyWave() throws IOException, JSONException {
        return getMyWave(DEFAULT_WAVE_TARGET_TRACKS);
    }

    public WaveTracks getMyWave(int targetTracks) throws IOException, JSONException {
        int target = Math.max(1, targetTracks);
        try {
            WaveTracks session = getMyWaveSession(target);
            if (!session.tracks.isEmpty()) {
                return session;
            }
        } catch (Exception ignored) {
            // Fall back to the legacy station endpoint below. Some accounts or API
            // edges may not expose the newer session endpoint consistently.
        }
        return getMyWaveLegacy(target);
    }

    public WaveTracks getMyWaveFastStart(int fallbackTargetTracks) throws IOException, JSONException {
        int target = Math.max(1, fallbackTargetTracks);
        try {
            WaveTracks session = getMyWaveSessionFirstBatch(target);
            if (!session.tracks.isEmpty()) {
                return session;
            }
        } catch (Exception ignored) {
            // The full legacy path below still gives us a playable first track if
            // the newer rotor session endpoint is unavailable for this account.
        }
        return getMyWaveLegacy(target);
    }

    public WaveTracks getMoreMyWave(String sessionId, String currentTrackId, int targetTracks) throws IOException, JSONException {
        if (sessionId == null || sessionId.isEmpty() || currentTrackId == null || currentTrackId.isEmpty()) {
            return getMyWave(targetTracks);
        }
        int target = Math.max(1, targetTracks);
        List<String> trackIds = new ArrayList<>();
        Map<String, String> batchIdByTrackKey = new HashMap<>();
        String batchId = "";
        String cursor = currentTrackId;

        for (int batch = 0; batch < 16 && trackIds.size() < target; batch++) {
            JSONObject body = new JSONObject();
            JSONArray queue = new JSONArray();
            queue.put(cursor);
            body.put("queue", queue);
            JSONObject result = asObject(apiPostJson("/rotor/session/" + sessionId + "/tracks", body));
            batchId = firstNonEmpty(result.optString("batchId"), result.optString("batch_id"), batchId);
            List<String> ids = trackIdsFromSequence(result.optJSONArray("sequence"));
            if (ids.isEmpty()) {
                break;
            }
            for (String id : ids) {
                if (trackIds.size() >= target) {
                    break;
                }
                if (!trackIds.contains(id)) {
                    trackIds.add(id);
                    if (!batchId.isEmpty()) {
                        batchIdByTrackKey.put(id, batchId);
                    }
                }
            }
            cursor = ids.get(0);
        }

        List<Track> tracks = getTracks(trackIds);
        for (Track track : tracks) {
            if (!batchId.isEmpty()) {
                batchIdByTrackKey.put(track.key, batchIdByTrackKey.get(track.id));
            }
        }
        return new WaveTracks(tracks, batchIdByTrackKey, batchId, sessionId);
    }

    private WaveTracks getMyWaveSessionFirstBatch(int targetTracks) throws IOException, JSONException {
        JSONObject body = new JSONObject();
        JSONArray seeds = new JSONArray();
        seeds.put(MY_WAVE_STATION);
        body.put("seeds", seeds);
        body.put("queue", new JSONArray());
        body.put("includeTracksInResponse", true);
        body.put("includeWaveModel", true);
        body.put("interactive", true);

        JSONObject result = asObject(apiPostJson("/rotor/session/new", body));
        String sessionId = result.optString("radioSessionId");
        String batchId = firstNonEmpty(result.optString("batchId"), result.optString("batch_id"));
        JSONArray sequence = result.optJSONArray("sequence");
        int target = Math.max(1, targetTracks);
        List<String> trackIds = limitStrings(trackIdsFromSequence(sequence), target);
        Map<String, String> batchIdByTrackKey = new HashMap<>();
        for (String id : trackIds) {
            if (!batchId.isEmpty()) {
                batchIdByTrackKey.put(id, batchId);
            }
        }

        List<Track> tracks = limitTracks(tracksFromSequence(sequence), target);
        if (tracks.isEmpty()) {
            tracks = getTracks(trackIds);
        }
        for (Track track : tracks) {
            String trackBatch = batchIdByTrackKey.get(track.id);
            if (trackBatch != null && !trackBatch.isEmpty()) {
                batchIdByTrackKey.put(track.key, trackBatch);
            }
        }
        return new WaveTracks(tracks, batchIdByTrackKey, batchId, sessionId);
    }

    private WaveTracks getMyWaveSession(int targetTracks) throws IOException, JSONException {
        JSONObject body = new JSONObject();
        JSONArray seeds = new JSONArray();
        seeds.put(MY_WAVE_STATION);
        body.put("seeds", seeds);
        body.put("queue", new JSONArray());
        body.put("includeTracksInResponse", true);
        body.put("includeWaveModel", true);
        body.put("interactive", true);

        JSONObject result = asObject(apiPostJson("/rotor/session/new", body));
        String sessionId = result.optString("radioSessionId");
        String batchId = firstNonEmpty(result.optString("batchId"), result.optString("batch_id"));
        List<String> trackIds = trackIdsFromSequence(result.optJSONArray("sequence"));
        Map<String, String> batchIdByTrackKey = new HashMap<>();
        for (String id : trackIds) {
            if (!batchId.isEmpty()) {
                batchIdByTrackKey.put(id, batchId);
            }
        }

        String cursor = trackIds.isEmpty() ? "" : trackIds.get(0);
        while (!sessionId.isEmpty() && !cursor.isEmpty() && trackIds.size() < targetTracks) {
            WaveTracks more = getMoreMyWave(sessionId, cursor, targetTracks - trackIds.size());
            if (more.tracks.isEmpty()) {
                break;
            }
            int before = trackIds.size();
            for (Track track : more.tracks) {
                if (!trackIds.contains(track.id)) {
                    trackIds.add(track.id);
                    String trackBatch = more.batchIdByTrackKey.get(track.key);
                    if (trackBatch == null) {
                        trackBatch = more.batchIdByTrackKey.get(track.id);
                    }
                    if (trackBatch != null && !trackBatch.isEmpty()) {
                        batchIdByTrackKey.put(track.id, trackBatch);
                    }
                }
            }
            cursor = more.tracks.get(0).id;
            if (trackIds.size() == before) {
                break;
            }
        }

        List<Track> tracks = getTracks(trackIds);
        for (Track track : tracks) {
            String trackBatch = batchIdByTrackKey.get(track.id);
            if (trackBatch != null && !trackBatch.isEmpty()) {
                batchIdByTrackKey.put(track.key, trackBatch);
            }
        }
        return new WaveTracks(tracks, batchIdByTrackKey, batchId, sessionId);
    }

    private WaveTracks getMyWaveLegacy(int targetTracks) throws IOException, JSONException {
        List<String> trackIds = new ArrayList<>();
        Map<String, String> batchIdByTrackKey = new HashMap<>();
        Set<String> seen = new HashSet<>();
        String queue = null;
        String firstBatchId = "";

        for (int batch = 0; batch < 24 && trackIds.size() < targetTracks; batch++) {
            JSONObject wave;
            try {
                wave = getRotorStationTracks(MY_WAVE_STATION, queue);
            } catch (IOException ex) {
                if (!trackIds.isEmpty()) {
                    break;
                }
                throw ex;
            }

            String batchId = firstNonEmpty(wave.optString("batchId"), wave.optString("batch_id"));
            if (firstBatchId.isEmpty()) {
                firstBatchId = batchId;
            }

            JSONArray sequence = wave.optJSONArray("sequence");
            if (sequence == null || sequence.length() == 0) {
                break;
            }

            String lastTrackId = null;
            String firstTrackId = null;
            int before = trackIds.size();
            for (int i = 0; i < sequence.length(); i++) {
                JSONObject item = sequence.optJSONObject(i);
                JSONObject trackObject = item == null ? null : item.optJSONObject("track");
                String key = trackObject == null ? "" : trackKeyFromJson(trackObject);
                if (key.isEmpty()) {
                    continue;
                }
                lastTrackId = key.contains(":") ? key.substring(0, key.indexOf(':')) : key;
                if (firstTrackId == null) {
                    firstTrackId = lastTrackId;
                }
                if (seen.add(key)) {
                    trackIds.add(key);
                    if (!batchId.isEmpty()) {
                        batchIdByTrackKey.put(key, batchId);
                        batchIdByTrackKey.put(lastTrackId, batchId);
                    }
                    if (trackIds.size() >= targetTracks) {
                        break;
                    }
                }
            }

            if (firstTrackId == null || trackIds.size() == before) {
                break;
            }
            queue = firstTrackId;
        }

        return new WaveTracks(getTracks(trackIds), batchIdByTrackKey, firstBatchId, "");
    }

    public boolean rotorStationFeedbackRadioStarted(String station, String from, String batchId) throws IOException, JSONException {
        return rotorStationFeedback(station, "radioStarted", from, batchId, null);
    }

    public boolean rotorStationFeedbackTrackStarted(String station, String trackId, String batchId) throws IOException, JSONException {
        return rotorStationFeedback(station, "trackStarted", null, batchId, trackId);
    }

    public boolean rotorStationFeedbackDislike(String station, String trackId, String batchId) throws IOException, JSONException {
        return rotorStationFeedback(station, "dislike", null, batchId, trackId);
    }

    public List<Track> getTracks(List<String> trackIds) throws IOException, JSONException {
        List<Track> ordered = new ArrayList<>();
        if (trackIds == null || trackIds.isEmpty()) {
            return ordered;
        }

        for (int start = 0; start < trackIds.size(); start += 50) {
            int end = Math.min(start + 50, trackIds.size());
            List<String> batch = trackIds.subList(start, end);
            List<Param> form = new ArrayList<>();
            form.add(new Param("with-positions", "true"));
            for (String id : batch) {
                form.add(new Param("track-ids", id));
            }

            JSONArray array = asArray(apiPost("/tracks", form));
            Map<String, Track> byKey = new HashMap<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                Track track = item == null ? null : parseTrack(item, start + i + 1);
                if (track != null) {
                    byKey.put(track.key, track);
                    byKey.put(track.id, track);
                }
            }
            for (String requested : batch) {
                Track found = byKey.get(requested);
                if (found == null && requested.contains(":")) {
                    found = byKey.get(requested.substring(0, requested.indexOf(':')));
                }
                if (found != null) {
                    ordered.add(found);
                }
            }
        }
        return ordered;
    }

    public String getDirectUrl(String trackKey) throws IOException, JSONException {
        return getDirectUrl(trackKey, AudioQuality.from(null));
    }

    public String getDirectUrl(String trackKey, AudioQuality quality) throws IOException, JSONException {
        JSONArray infos = asArray(apiGet("/tracks/" + trackKey + "/download-info"));
        JSONObject best = chooseDownloadInfo(infos, quality == null ? AudioQuality.from(null) : quality);
        if (best == null) {
            throw new IOException("No playable download-info variant for " + trackKey);
        }
        String downloadInfoUrl = best.optString("downloadInfoUrl", "");
        if (downloadInfoUrl.isEmpty()) {
            throw new IOException("downloadInfoUrl is empty for " + trackKey);
        }
        String xml = request("GET", downloadInfoUrl, null, false);
        return buildDirectLink(xml);
    }

    private static JSONObject chooseDownloadInfo(JSONArray infos, AudioQuality quality) {
        JSONObject best = null;
        int bestScore = Integer.MIN_VALUE;
        int target = quality.targetBitrateKbps();
        for (int i = 0; i < infos.length(); i++) {
            JSONObject info = infos.optJSONObject(i);
            if (info == null || info.optBoolean("preview", false)) {
                continue;
            }
            String codec = info.optString("codec", "");
            int bitrate = info.optInt("bitrateInKbps", 0);
            int codecBonus = "mp3".equalsIgnoreCase(codec) ? 2 : 0;
            int score;
            if (quality.preferHighest()) {
                score = bitrate + codecBonus;
            } else if (bitrate <= target) {
                score = 200000 + bitrate + codecBonus;
            } else {
                score = 100000 - Math.abs(bitrate - target) + codecBonus;
            }
            if (score > bestScore) {
                best = info;
                bestScore = score;
            }
        }
        return best;
    }

    public byte[] downloadBytes(String url) throws IOException {
        HttpURLConnection connection = open(url, "GET", false);
        try {
            int status = connection.getResponseCode();
            byte[] body = readAll(status >= 400 ? connection.getErrorStream() : connection.getInputStream());
            if (status < 200 || status > 299) {
                throw new IOException("HTTP " + status + ": " + new String(body, StandardCharsets.UTF_8));
            }
            return body;
        } finally {
            connection.disconnect();
        }
    }

    public void downloadToFile(String url, File target) throws IOException {
        HttpURLConnection connection = open(url, "GET", false);
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status > 299) {
                byte[] body = readAll(connection.getErrorStream());
                throw new IOException("HTTP " + status + ": " + new String(body, StandardCharsets.UTF_8));
            }
            File parent = target.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IOException("Unable to create directory: " + parent);
            }
            try (BufferedInputStream in = new BufferedInputStream(connection.getInputStream());
                 FileOutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[128 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
        } finally {
            connection.disconnect();
        }
    }

    private Object apiGet(String path) throws IOException, JSONException {
        return unwrap(new JSONObject(request("GET", API_BASE + path, null, true)));
    }

    private Object apiPost(String path, List<Param> form) throws IOException, JSONException {
        return unwrap(new JSONObject(request("POST", API_BASE + path, form, true)));
    }

    private Object apiPostJson(String path, JSONObject body) throws IOException, JSONException {
        return unwrap(new JSONObject(requestJson("POST", API_BASE + path, body, true)));
    }

    private JSONObject getRotorStationTracks(String station, String queue) throws IOException, JSONException {
        StringBuilder path = new StringBuilder("/rotor/station/");
        path.append(station);
        path.append("/tracks?settings2=True");
        if (queue != null && !queue.isEmpty()) {
            path.append("&queue=");
            path.append(URLEncoder.encode(queue, "UTF-8"));
        }
        return asObject(apiGet(path.toString()));
    }

    private boolean rotorStationFeedback(
            String station,
            String type,
            String from,
            String batchId,
            String trackId
    ) throws IOException, JSONException {
        StringBuilder path = new StringBuilder(API_BASE);
        path.append("/rotor/station/");
        path.append(station);
        path.append("/feedback");
        if (batchId != null && !batchId.isEmpty()) {
            path.append("?batch-id=");
            path.append(URLEncoder.encode(batchId, "UTF-8"));
        }

        List<Param> form = new ArrayList<>();
        form.add(new Param("type", type));
        form.add(new Param("timestamp", String.format(Locale.US, "%.3f", System.currentTimeMillis() / 1000.0)));
        if (from != null && !from.isEmpty()) {
            form.add(new Param("from", from));
        }
        if (trackId != null && !trackId.isEmpty()) {
            form.add(new Param("trackId", trackId));
        }

        String response = request("POST", path.toString(), form, true).trim();
        if ("ok".equalsIgnoreCase(response)) {
            return true;
        }
        Object result = unwrap(new JSONObject(response));
        return "ok".equalsIgnoreCase(String.valueOf(result));
    }

    private String request(String method, String url, List<Param> form, boolean auth) throws IOException {
        HttpURLConnection connection = open(url, method, auth);
        if (form != null && !form.isEmpty()) {
            byte[] body = encodeForm(form).getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body);
            }
        }
        try {
            int status = connection.getResponseCode();
            byte[] body = readAll(status >= 400 ? connection.getErrorStream() : connection.getInputStream());
            String text = new String(body, StandardCharsets.UTF_8);
            if (status < 200 || status > 299) {
                throw new IOException("HTTP " + status + ": " + text);
            }
            return text;
        } finally {
            connection.disconnect();
        }
    }

    private String requestJson(String method, String url, JSONObject json, boolean auth) throws IOException {
        HttpURLConnection connection = open(url, method, auth);
        byte[] body = (json == null ? "{}" : json.toString()).getBytes(StandardCharsets.UTF_8);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setFixedLengthStreamingMode(body.length);
        try (OutputStream out = connection.getOutputStream()) {
            out.write(body);
        }
        try {
            int status = connection.getResponseCode();
            byte[] response = readAll(status >= 400 ? connection.getErrorStream() : connection.getInputStream());
            String text = new String(response, StandardCharsets.UTF_8);
            if (status < 200 || status > 299) {
                throw new IOException("HTTP " + status + ": " + text);
            }
            return text;
        } finally {
            connection.disconnect();
        }
    }

    private HttpURLConnection open(String url, String method, boolean auth) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("X-Yandex-Music-Client", YM_CLIENT);
        connection.setRequestProperty("Accept-Language", "ru");
        if (auth && !accessToken.isEmpty()) {
            connection.setRequestProperty("Authorization", "OAuth " + accessToken);
        }
        return connection;
    }

    private static String encodeForm(List<Param> form) throws IOException {
        StringBuilder builder = new StringBuilder();
        for (Param param : form) {
            if (builder.length() > 0) {
                builder.append('&');
            }
            builder.append(URLEncoder.encode(param.name, "UTF-8"));
            builder.append('=');
            builder.append(URLEncoder.encode(param.value, "UTF-8"));
        }
        return builder.toString();
    }

    private static byte[] readAll(java.io.InputStream stream) throws IOException {
        if (stream == null) {
            return new byte[0];
        }
        try (BufferedInputStream in = new BufferedInputStream(stream);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private static Object unwrap(JSONObject response) {
        return response.has("result") ? response.opt("result") : response;
    }

    private static JSONObject asObject(Object value) throws JSONException {
        if (value instanceof JSONObject) {
            return (JSONObject) value;
        }
        throw new JSONException("Expected object, got " + value);
    }

    private static JSONArray asArray(Object value) throws JSONException {
        if (value instanceof JSONArray) {
            return (JSONArray) value;
        }
        throw new JSONException("Expected array, got " + value);
    }

    private static Track parseTrack(JSONObject item, int order) {
        return parseTrack(item, order, "", "", "", "");
    }

    private static Track parseTrack(
            JSONObject item,
            int order,
            String fallbackAlbumId,
            String fallbackAlbumTitle,
            String fallbackArtist,
            String fallbackCoverUrl
    ) {
        if (item == null) {
            return null;
        }
        if (!item.optBoolean("available", true)) {
            return null;
        }
        String id = item.optString("id", "");
        if (id.isEmpty()) {
            return null;
        }

        JSONArray albums = item.optJSONArray("albums");
        JSONObject album = albums != null && albums.length() > 0 ? albums.optJSONObject(0) : null;
        String albumId = firstNonEmpty(album == null ? "" : album.optString("id", ""), fallbackAlbumId);
        String key = albumId.isEmpty() ? id : id + ":" + albumId;
        String title = firstNonEmpty(item.optString("title"), item.optString("realId"), id);
        String artist = firstNonEmpty(joinTitles(item.optJSONArray("artists")), fallbackArtist);
        String albumTitle = firstNonEmpty(album == null ? "" : album.optString("title", ""), fallbackAlbumTitle);
        int year = album == null ? 0 : album.optInt("year", 0);
        long duration = item.optLong("durationMs", item.optLong("duration", 0L));
        String cover = album == null ? "" : firstNonEmpty(album.optString("coverUri"), item.optString("coverUri"));
        String coverUrl = firstNonEmpty(normalizeCoverUrl(cover), fallbackCoverUrl);
        return new Track(id, albumId, key, title, artist, albumTitle, year, duration, coverUrl, order);
    }

    private static SearchResults parseSearchResults(String query, JSONObject object) {
        List<Track> tracks = parseSearchTracks(object.optJSONObject("tracks"));
        List<AlbumInfo> albums = parseSearchAlbums(object.optJSONObject("albums"));
        List<ArtistInfo> artists = parseSearchArtists(object.optJSONObject("artists"));
        return new SearchResults(query, tracks, albums, artists);
    }

    private static List<Track> parseSearchTracks(JSONObject block) {
        JSONArray array = block == null ? null : block.optJSONArray("results");
        return parseTrackArray(array);
    }

    private static List<Track> parseTrackArray(JSONArray array) {
        List<Track> tracks = new ArrayList<>();
        if (array == null) {
            return tracks;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            JSONObject trackObject = item == null ? null : item.optJSONObject("track");
            Track track = parseTrack(trackObject == null ? item : trackObject, i + 1);
            if (track != null) {
                tracks.add(track);
            }
        }
        return tracks;
    }

    private static List<AlbumInfo> parseSearchAlbums(JSONObject block) {
        JSONArray array = block == null ? null : block.optJSONArray("results");
        List<AlbumInfo> albums = new ArrayList<>();
        if (array == null) {
            return albums;
        }
        for (int i = 0; i < array.length(); i++) {
            AlbumInfo album = parseAlbum(array.optJSONObject(i));
            if (album != null) {
                albums.add(album);
            }
        }
        return albums;
    }

    private static AlbumInfo parseAlbum(JSONObject item) {
        if (item == null) {
            return null;
        }
        String id = item.optString("id", "");
        String title = item.optString("title", "");
        if (id.isEmpty() || title.isEmpty()) {
            return null;
        }
        String artist = joinTitles(item.optJSONArray("artists"));
        String coverUrl = normalizeCoverUrl(item.optString("coverUri", ""));
        int trackCount = item.optInt("trackCount", item.optInt("track_count", 0));
        int year = item.optInt("year", 0);
        return new AlbumInfo(id, title, artist, coverUrl, Math.max(0, trackCount), year);
    }

    private static List<ArtistInfo> parseSearchArtists(JSONObject block) {
        JSONArray array = block == null ? null : block.optJSONArray("results");
        List<ArtistInfo> artists = new ArrayList<>();
        if (array == null) {
            return artists;
        }
        for (int i = 0; i < array.length(); i++) {
            ArtistInfo artist = parseArtist(array.optJSONObject(i));
            if (artist != null) {
                artists.add(artist);
            }
        }
        return artists;
    }

    private static ArtistInfo parseArtist(JSONObject item) {
        if (item == null) {
            return null;
        }
        String id = item.optString("id", "");
        String name = firstNonEmpty(item.optString("name"), item.optString("title"));
        if (id.isEmpty() || name.isEmpty()) {
            return null;
        }
        String cover = "";
        JSONObject coverObj = item.optJSONObject("cover");
        if (coverObj != null) {
            cover = firstNonEmpty(coverObj.optString("uri"), coverObj.optString("prefix"));
        }
        cover = firstNonEmpty(cover, item.optString("coverUri"));
        return new ArtistInfo(id, name, normalizeCoverUrl(cover));
    }

    private static PlaylistSummary playlistSummaryFromObject(JSONObject item) throws JSONException {
        PlaylistSummary playlist = parsePlaylistSummary(item);
        if (playlist == null) {
            throw new JSONException("Expected playlist summary");
        }
        return playlist;
    }

    private static PlaylistSummary parsePlaylistSummary(JSONObject item) {
        if (item == null) {
            return null;
        }
        int kind = item.optInt("kind", -1);
        String title = item.optString("title", "");
        if (kind < 0 || title.isEmpty()) {
            return null;
        }
        int trackCount = item.optInt("trackCount", -1);
        JSONArray tracks = item.optJSONArray("tracks");
        if (trackCount < 0 && tracks != null) {
            trackCount = tracks.length();
        }
        return new PlaylistSummary(kind, title, Math.max(0, trackCount), item.optInt("revision", 1));
    }

    private static String insertTrackDiff(Track track, int at) throws IOException, JSONException {
        String id = firstNonEmpty(track.id, idFromTrackKey(track.key));
        String albumId = firstNonEmpty(track.albumId, albumIdFromTrackKey(track.key));
        if (id.isEmpty() || albumId.isEmpty()) {
            throw new IOException("Track has no Yandex id/albumId pair: " + track.key);
        }
        JSONObject trackObject = new JSONObject();
        trackObject.put("id", id);
        trackObject.put("albumId", albumId);

        JSONArray tracks = new JSONArray();
        tracks.put(trackObject);

        JSONObject operation = new JSONObject();
        operation.put("op", "insert");
        operation.put("at", Math.max(0, at));
        operation.put("tracks", tracks);

        JSONArray diff = new JSONArray();
        diff.put(operation);
        return diff.toString();
    }

    private static String idFromTrackKey(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }
        int index = key.indexOf(':');
        return index < 0 ? key : key.substring(0, index);
    }

    private static String albumIdFromTrackKey(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }
        int index = key.indexOf(':');
        return index < 0 || index + 1 >= key.length() ? "" : key.substring(index + 1);
    }

    private static String trackKeyFromJson(JSONObject item) {
        String id = firstNonEmpty(
                item.optString("id"),
                item.optString("trackId"),
                item.optString("track_id"),
                item.optString("realId")
        );
        if (id.isEmpty()) {
            return "";
        }
        JSONArray albums = item.optJSONArray("albums");
        JSONObject album = albums != null && albums.length() > 0 ? albums.optJSONObject(0) : null;
        String albumId = firstNonEmpty(
                album == null ? "" : album.optString("id"),
                item.optString("albumId"),
                item.optString("album_id")
        );
        return albumId.isEmpty() ? id : id + ":" + albumId;
    }

    private static List<String> trackIdsFromSequence(JSONArray sequence) {
        List<String> ids = new ArrayList<>();
        if (sequence == null) {
            return ids;
        }
        for (int i = 0; i < sequence.length(); i++) {
            JSONObject item = sequence.optJSONObject(i);
            JSONObject track = item == null ? null : item.optJSONObject("track");
            String id = "";
            if (track != null) {
                id = firstNonEmpty(
                        track.optString("id"),
                        track.optString("trackId"),
                        track.optString("track_id"),
                        track.optString("realId")
                );
            }
            if (!id.isEmpty()) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static List<Track> tracksFromSequence(JSONArray sequence) {
        List<Track> tracks = new ArrayList<>();
        if (sequence == null) {
            return tracks;
        }
        for (int i = 0; i < sequence.length(); i++) {
            JSONObject item = sequence.optJSONObject(i);
            JSONObject trackObject = item == null ? null : item.optJSONObject("track");
            if (trackObject == null) {
                continue;
            }
            Track track = parseTrack(trackObject, tracks.size() + 1);
            if (track != null) {
                tracks.add(track);
            }
        }
        return tracks;
    }

    private static List<String> limitStrings(List<String> values, int limit) {
        if (values == null || values.size() <= limit) {
            return values == null ? new ArrayList<>() : values;
        }
        return new ArrayList<>(values.subList(0, Math.max(0, limit)));
    }

    private static List<Track> limitTracks(List<Track> values, int limit) {
        if (values == null || values.size() <= limit) {
            return values == null ? new ArrayList<>() : values;
        }
        return new ArrayList<>(values.subList(0, Math.max(0, limit)));
    }

    private static String joinTitles(JSONArray array) {
        if (array == null || array.length() == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            String title = item == null ? "" : item.optString("name", item.optString("title", ""));
            if (title.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(title);
        }
        return builder.toString();
    }

    private static String normalizeCoverUrl(String cover) {
        if (cover == null || cover.isEmpty()) {
            return "";
        }
        String url = cover.startsWith("http") ? cover : "https://" + cover;
        return url.replace("%%", "400x400");
    }

    private static String buildDirectLink(String xml) throws IOException {
        try {
            Document document = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            String host = tag(document, "host");
            String path = tag(document, "path");
            String ts = tag(document, "ts");
            String s = tag(document, "s");
            String cleanPath = path.startsWith("/") ? path.substring(1) : path;
            String sign = md5(SIGN_SALT + cleanPath + s);
            return "https://" + host + "/get-mp3/" + sign + "/" + ts + path;
        } catch (Exception ex) {
            throw new IOException("Unable to build direct media URL", ex);
        }
    }

    private static String tag(Document document, String name) throws IOException {
        if (document.getElementsByTagName(name).getLength() == 0) {
            throw new IOException("Missing XML tag: " + name);
        }
        return document.getElementsByTagName(name).item(0).getTextContent();
    }

    private static String md5(String value) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format(Locale.US, "%02x", b & 0xff));
        }
        return builder.toString();
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private static final class Param {
        final String name;
        final String value;

        Param(String name, String value) {
            this.name = name;
            this.value = value == null ? "" : value;
        }
    }

    public static final class DeviceCode {
        public final String deviceCode;
        public final String userCode;
        public final String verificationUrl;
        public final long expiresInSeconds;
        public final long intervalSeconds;

        DeviceCode(String deviceCode, String userCode, String verificationUrl, long expiresInSeconds, long intervalSeconds) {
            this.deviceCode = deviceCode;
            this.userCode = userCode;
            this.verificationUrl = verificationUrl;
            this.expiresInSeconds = expiresInSeconds;
            this.intervalSeconds = intervalSeconds;
        }
    }

    public static final class OAuthToken {
        public final String accessToken;
        public final String refreshToken;

        OAuthToken(String accessToken, String refreshToken) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }
    }

    public static final class AccountStatus {
        public final long uid;
        public final String name;

        AccountStatus(long uid, String name) {
            this.uid = uid;
            this.name = name;
        }
    }

    public static final class PlaylistSummary {
        public final int kind;
        public final String title;
        public final int trackCount;
        public final int revision;

        PlaylistSummary(int kind, String title, int trackCount, int revision) {
            this.kind = kind;
            this.title = title;
            this.trackCount = trackCount;
            this.revision = revision;
        }
    }

    public static final class WaveTracks {
        public final List<Track> tracks;
        public final Map<String, String> batchIdByTrackKey;
        public final String firstBatchId;
        public final String sessionId;

        WaveTracks(List<Track> tracks, Map<String, String> batchIdByTrackKey, String firstBatchId, String sessionId) {
            this.tracks = tracks;
            this.batchIdByTrackKey = batchIdByTrackKey;
            this.firstBatchId = firstBatchId == null ? "" : firstBatchId;
            this.sessionId = sessionId == null ? "" : sessionId;
        }
    }

    public static final class SearchResults {
        public final String query;
        public final List<Track> tracks;
        public final List<AlbumInfo> albums;
        public final List<ArtistInfo> artists;

        SearchResults(String query, List<Track> tracks, List<AlbumInfo> albums, List<ArtistInfo> artists) {
            this.query = query == null ? "" : query;
            this.tracks = tracks == null ? new ArrayList<>() : tracks;
            this.albums = albums == null ? new ArrayList<>() : albums;
            this.artists = artists == null ? new ArrayList<>() : artists;
        }

        public boolean isEmpty() {
            return tracks.isEmpty() && albums.isEmpty() && artists.isEmpty();
        }
    }

    public static final class AlbumInfo {
        public final String id;
        public final String title;
        public final String artist;
        public final String coverUrl;
        public final int trackCount;
        public final int year;

        AlbumInfo(String id, String title, String artist, String coverUrl, int trackCount, int year) {
            this.id = id == null ? "" : id;
            this.title = title == null ? "" : title;
            this.artist = artist == null ? "" : artist;
            this.coverUrl = coverUrl == null ? "" : coverUrl;
            this.trackCount = Math.max(0, trackCount);
            this.year = Math.max(0, year);
        }
    }

    public static final class ArtistInfo {
        public final String id;
        public final String name;
        public final String coverUrl;

        ArtistInfo(String id, String name, String coverUrl) {
            this.id = id == null ? "" : id;
            this.name = name == null ? "" : name;
            this.coverUrl = coverUrl == null ? "" : coverUrl;
        }
    }

    public static final class Track {
        public final String id;
        public final String albumId;
        public final String key;
        public final String title;
        public final String artist;
        public final String album;
        public final int year;
        public final long durationMs;
        public final String coverUrl;
        public final int order;

        public Track(String id, String albumId, String key, String title, String artist, String album, int year, long durationMs, String coverUrl, int order) {
            this.id = id;
            this.albumId = albumId;
            this.key = key;
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.year = year;
            this.durationMs = durationMs;
            this.coverUrl = coverUrl;
            this.order = order;
        }

        public String displayName() {
            String prefix = artist == null || artist.isEmpty() ? "" : artist + " - ";
            return sanitizeFileName(prefix + title) + ".mp3";
        }

        private static String sanitizeFileName(String value) {
            return value == null ? "track" : value.replaceAll("[\\\\/:*?\"<>|]", "_");
        }
    }
}
