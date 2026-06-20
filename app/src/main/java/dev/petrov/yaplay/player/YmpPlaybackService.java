package dev.petrov.yaplay.player;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadata;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.browse.MediaBrowser;
import android.media.session.MediaSession;
import android.net.Uri;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.service.media.MediaBrowserService;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

import dev.petrov.yaplay.Diagnostics;
import dev.petrov.yaplay.MainActivity;
import dev.petrov.yaplay.R;
import dev.petrov.yaplay.ymusic.TokenStore;
import dev.petrov.yaplay.ymusic.YandexMusicClient;

public class YmpPlaybackService extends MediaBrowserService {
    public static final String ACTION_PLAY_WAVE = "dev.petrov.yaplay.action.PLAY_WAVE";
    public static final String ACTION_PLAY_LIKED_CACHE = "dev.petrov.yaplay.action.PLAY_LIKED_CACHE";
    public static final String ACTION_PLAY_PLAYLIST = "dev.petrov.yaplay.action.PLAY_PLAYLIST";
    public static final String ACTION_PLAY_LOCAL_PLAYLIST = "dev.petrov.yaplay.action.PLAY_LOCAL_PLAYLIST";
    public static final String ACTION_SELECT_WAVE = "dev.petrov.yaplay.action.SELECT_WAVE";
    public static final String ACTION_SELECT_LIKED_CACHE = "dev.petrov.yaplay.action.SELECT_LIKED_CACHE";
    public static final String ACTION_SELECT_PLAYLIST = "dev.petrov.yaplay.action.SELECT_PLAYLIST";
    public static final String ACTION_SELECT_LOCAL_PLAYLIST = "dev.petrov.yaplay.action.SELECT_LOCAL_PLAYLIST";
    public static final String ACTION_PLAY_SEARCH_TRACK = "dev.petrov.yaplay.action.PLAY_SEARCH_TRACK";
    public static final String ACTION_PLAY_ALBUM = "dev.petrov.yaplay.action.PLAY_ALBUM";
    public static final String ACTION_PLAY_ARTIST = "dev.petrov.yaplay.action.PLAY_ARTIST";
    public static final String ACTION_PLAY_PAUSE = "dev.petrov.yaplay.action.PLAY_PAUSE";
    public static final String ACTION_STOP = "dev.petrov.yaplay.action.STOP";
    public static final String ACTION_NEXT = "dev.petrov.yaplay.action.NEXT";
    public static final String ACTION_PREVIOUS = "dev.petrov.yaplay.action.PREVIOUS";
    public static final String ACTION_TOGGLE_SHUFFLE = "dev.petrov.yaplay.action.TOGGLE_SHUFFLE";
    public static final String ACTION_LIKE = "dev.petrov.yaplay.action.LIKE";
    public static final String ACTION_DISLIKE = "dev.petrov.yaplay.action.DISLIKE";
    public static final String ACTION_ADD_CURRENT_TO_PLAYLIST = "dev.petrov.yaplay.action.ADD_CURRENT_TO_PLAYLIST";
    public static final String ACTION_CREATE_PLAYLIST_AND_ADD = "dev.petrov.yaplay.action.CREATE_PLAYLIST_AND_ADD";
    public static final String ACTION_STATUS = "dev.petrov.yaplay.action.PLAYER_STATUS";

    public static final int SOURCE_WAVE = 0;
    public static final int SOURCE_OFFLINE = 1;
    public static final int SOURCE_PLAYLIST = 2;
    public static final int SOURCE_LOCAL_PLAYLIST = 3;
    public static final int SOURCE_SEARCH = 4;

    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_ARTIST = "artist";
    public static final String EXTRA_ALBUM = "album";
    public static final String EXTRA_COVER_URL = "cover_url";
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_QUEUE = "queue";
    public static final String EXTRA_INDEX = "index";
    public static final String EXTRA_WAVE = "wave";
    public static final String EXTRA_SHUFFLE = "shuffle";
    public static final String EXTRA_PLAYING = "playing";
    public static final String EXTRA_PREPARED = "prepared";
    public static final String EXTRA_PLAY_MODE = "play_mode";
    public static final String EXTRA_LIKED = "liked";
    public static final String EXTRA_SOURCE_TYPE = "source_type";
    public static final String EXTRA_SOURCE_TITLE = "source_title";
    public static final String EXTRA_PLAYLIST_KIND = "playlist_kind";
    public static final String EXTRA_PLAYLIST_TITLE = "playlist_title";
    public static final String EXTRA_LOCAL_PLAYLIST_ID = "local_playlist_id";
    public static final String EXTRA_LOCAL_PLAYLIST_TITLE = "local_playlist_title";
    public static final String EXTRA_TRACK_KEY = "track_key";
    public static final String EXTRA_ALBUM_ID = "album_id";
    public static final String EXTRA_ARTIST_ID = "artist_id";
    public static final String EXTRA_SOURCE_LABEL = "source_label";
    public static final String EXTRA_AUDIO_SESSION_ID = "audio_session_id";
    public static final String EXTRA_POSITION = "position";
    public static final String EXTRA_SOURCE_SELECTED = "source_selected";

    private static final String STATE_PREFS = "ymplayer_playback_state";
    private static final String KEY_HAS_STATE = "has_state";
    private static final String KEY_QUEUE_JSON = "queue_json";
    private static final String KEY_SOURCE_STATE_PREFIX = "source_state.";
    private static final String KEY_SOURCE_TRACK = ".track";
    private static final String KEY_SOURCE_INDEX = ".index";
    private static final String KEY_SOURCE_POSITION = ".position";
    private static final String KEY_SOURCE_PLAY_MODE = ".play_mode";
    private static final String CHANNEL_ID = "playback";
    private static final int NOTIFICATION_ID = 2001;
    private static final String ROOT_ID = "ymp_root";
    private static final String MEDIA_ID_WAVE = "ymp_my_wave";
    private static final String MEDIA_ID_LIKED_CACHE = "ymp_liked_cache";
    private static final long SIDEBAR_WATCHDOG_INTERVAL_MS = 30_000L;
    private static final long POSITION_SAVE_INTERVAL_MS = 2_000L;
    private static final int PLAY_MODE_ORDER = 0;
    private static final int PLAY_MODE_SHUFFLE = 1;
    private static final int PLAY_MODE_REPEAT = 2;
    private static volatile Intent latestStatus;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<YandexMusicClient.Track> queue = new ArrayList<>();
    private final Set<String> likedTrackKeys = new HashSet<>();
    private final Random random = new Random();
    private final Runnable sidebarWatchdogRunnable = new Runnable() {
        @Override
        public void run() {
            ensureSideBar(false);
            mainHandler.postDelayed(this, SIDEBAR_WATCHDOG_INTERVAL_MS);
        }
    };
    private final Runnable positionSaveRunnable = new Runnable() {
        @Override
        public void run() {
            persistCurrentPlaybackState();
            if (mediaPlayer != null && (prepared || safeIsPlaying())) {
                mainHandler.postDelayed(this, POSITION_SAVE_INTERVAL_MS);
            }
        }
    };

    private YmpRepository repository;
    private YmpRepository.WaveQueue waveQueue;
    private MediaSession mediaSession;
    private MediaPlayer mediaPlayer;
    private ParcelFileDescriptor currentPfd;

    private int queueIndex = -1;
    private boolean waveMode;
    private boolean shuffle;
    private boolean loading;
    private boolean prepared;
    private boolean sidebarWatchdogStarted;
    private boolean likedKeysLoaded;
    private boolean wavePrefetching;
    private boolean forceInitialWavePrefetch;
    private int playMode = PLAY_MODE_ORDER;
    private int currentSourceType = SOURCE_WAVE;
    private int currentPlaylistKind = -1;
    private String currentLocalPlaylistId = "";
    private String currentSourceTitle = "My Wave";
    private String statusText = "Idle";
    private String lastWaveFeedbackTrackKey = "";
    private String prefetchingTrackKey = "";
    private String prefetchedTrackKey = "";
    private String metadataCoverUrl = "";
    private Bitmap metadataCoverBitmap;
    private Bitmap defaultArtworkBitmap;
    private boolean metadataCoverLoading;
    private long pendingSeekMs;

    public static Intent latestStatusSnapshot(Context context) {
        Intent status = latestStatus;
        if (status == null) {
            return null;
        }
        Intent copy = new Intent(status);
        copy.setPackage(context.getPackageName());
        return copy;
    }

    public static Intent persistedStatusSnapshot(Context context) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_HAS_STATE, false)) {
            return null;
        }
        Intent intent = new Intent(ACTION_STATUS);
        intent.setPackage(context.getPackageName());
        intent.putExtra(EXTRA_TITLE, prefs.getString(EXTRA_TITLE, ""));
        intent.putExtra(EXTRA_ARTIST, prefs.getString(EXTRA_ARTIST, ""));
        intent.putExtra(EXTRA_ALBUM, prefs.getString(EXTRA_ALBUM, ""));
        intent.putExtra(EXTRA_COVER_URL, prefs.getString(EXTRA_COVER_URL, ""));
        intent.putExtra(EXTRA_STATUS, prefs.getString(EXTRA_STATUS, ""));
        intent.putExtra(EXTRA_QUEUE, prefs.getInt(EXTRA_QUEUE, 0));
        intent.putExtra(EXTRA_INDEX, prefs.getInt(EXTRA_INDEX, -1));
        intent.putExtra(EXTRA_WAVE, prefs.getBoolean(EXTRA_WAVE, true));
        intent.putExtra(EXTRA_SHUFFLE, prefs.getBoolean(EXTRA_SHUFFLE, false));
        intent.putExtra(EXTRA_PLAYING, prefs.getBoolean(EXTRA_PLAYING, false));
        intent.putExtra(EXTRA_PREPARED, prefs.getBoolean(EXTRA_PREPARED, false));
        intent.putExtra(EXTRA_PLAY_MODE, prefs.getInt(EXTRA_PLAY_MODE, PLAY_MODE_ORDER));
        intent.putExtra(EXTRA_LIKED, prefs.getBoolean(EXTRA_LIKED, false));
        intent.putExtra(EXTRA_SOURCE_TYPE, prefs.getInt(EXTRA_SOURCE_TYPE, SOURCE_WAVE));
        intent.putExtra(EXTRA_SOURCE_TITLE, prefs.getString(EXTRA_SOURCE_TITLE, ""));
        intent.putExtra(EXTRA_PLAYLIST_KIND, prefs.getInt(EXTRA_PLAYLIST_KIND, -1));
        intent.putExtra(EXTRA_LOCAL_PLAYLIST_ID, prefs.getString(EXTRA_LOCAL_PLAYLIST_ID, ""));
        intent.putExtra(EXTRA_LOCAL_PLAYLIST_TITLE, prefs.getString(EXTRA_LOCAL_PLAYLIST_TITLE, ""));
        intent.putExtra(EXTRA_TRACK_KEY, prefs.getString(EXTRA_TRACK_KEY, ""));
        intent.putExtra(EXTRA_POSITION, prefs.getLong(EXTRA_POSITION, 0L));
        return intent;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        repository = new YmpRepository(this);
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setOnPreparedListener(mp -> {
            prepared = true;
            loading = false;
            if (pendingSeekMs > 0L) {
                try {
                    mp.seekTo((int) Math.min(pendingSeekMs, Integer.MAX_VALUE));
                } catch (Exception ex) {
                    Diagnostics.log(this, "YMP restore seek ignored", ex);
                }
                pendingSeekMs = 0L;
            }
            mp.start();
            statusText = "Playing";
            updateSession();
            updateNotification();
            broadcastStatus();
            schedulePositionSave();
            maybePrefetchNextAudio();
            if (forceInitialWavePrefetch) {
                forceInitialWavePrefetch = false;
                prefetchWave(true);
            } else {
                maybePrefetchWave();
            }
        });
        mediaPlayer.setOnCompletionListener(mp -> playNextInternal(true));
        mediaPlayer.setOnErrorListener((mp, what, extra) -> {
            Diagnostics.log(this, "YMP MediaPlayer error: what=" + what + ", extra=" + extra);
            statusText = "Playback error: " + what + "/" + extra;
            loading = false;
            prepared = false;
            cancelPositionSave();
            updateSession();
            updateNotification();
            broadcastStatus();
            return true;
        });

        mediaSession = new MediaSession(this, "YMPlayer");
        Intent mediaButton = new Intent(Intent.ACTION_MEDIA_BUTTON);
        mediaButton.setClass(this, YmpMediaButtonReceiver.class);
        PendingIntent mediaButtonIntent = PendingIntent.getBroadcast(this, 20, mediaButton,
                PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());
        mediaSession.setMediaButtonReceiver(mediaButtonIntent);
        mediaSession.setCallback(new SessionCallback());
        mediaSession.setActive(true);
        setSessionToken(mediaSession.getSessionToken());
        restorePlaybackState();
        updateSession();
        createChannel();
        startSidebarWatchdog();
        Diagnostics.log(this, "YMP playback service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            statusText = "Playback service restored";
            updateSession();
            broadcastStatus();
            ensureSideBar(false);
            return START_STICKY;
        }
        String action = intent.getAction();
        if (ACTION_PLAY_WAVE.equals(action)) {
            playMyWave();
        } else if (ACTION_PLAY_LIKED_CACHE.equals(action)) {
            playLikedCache();
        } else if (ACTION_PLAY_PLAYLIST.equals(action)) {
            playPlaylist(
                    intent.getIntExtra(EXTRA_PLAYLIST_KIND, -1),
                    intent.getStringExtra(EXTRA_PLAYLIST_TITLE)
            );
        } else if (ACTION_PLAY_LOCAL_PLAYLIST.equals(action)) {
            playLocalPlaylist(
                    intent.getStringExtra(EXTRA_LOCAL_PLAYLIST_ID),
                    intent.getStringExtra(EXTRA_LOCAL_PLAYLIST_TITLE)
            );
        } else if (ACTION_SELECT_WAVE.equals(action)) {
            selectSourceOnly(SOURCE_WAVE, -1, "", "My Wave");
        } else if (ACTION_SELECT_LIKED_CACHE.equals(action)) {
            selectSourceOnly(SOURCE_OFFLINE, -1, "", "Offline mode");
        } else if (ACTION_SELECT_PLAYLIST.equals(action)) {
            selectSourceOnly(
                    SOURCE_PLAYLIST,
                    intent.getIntExtra(EXTRA_PLAYLIST_KIND, -1),
                    "",
                    intent.getStringExtra(EXTRA_PLAYLIST_TITLE)
            );
        } else if (ACTION_SELECT_LOCAL_PLAYLIST.equals(action)) {
            selectSourceOnly(
                    SOURCE_LOCAL_PLAYLIST,
                    -1,
                    intent.getStringExtra(EXTRA_LOCAL_PLAYLIST_ID),
                    intent.getStringExtra(EXTRA_LOCAL_PLAYLIST_TITLE)
            );
        } else if (ACTION_PLAY_SEARCH_TRACK.equals(action)) {
            playSearchTrack(
                    intent.getStringExtra(EXTRA_TRACK_KEY),
                    intent.getStringExtra(EXTRA_SOURCE_LABEL)
            );
        } else if (ACTION_PLAY_ALBUM.equals(action)) {
            playAlbum(
                    intent.getStringExtra(EXTRA_ALBUM_ID),
                    intent.getStringExtra(EXTRA_SOURCE_LABEL)
            );
        } else if (ACTION_PLAY_ARTIST.equals(action)) {
            playArtist(
                    intent.getStringExtra(EXTRA_ARTIST_ID),
                    intent.getStringExtra(EXTRA_SOURCE_LABEL)
            );
        } else if (ACTION_PLAY_PAUSE.equals(action)) {
            togglePlayPause();
        } else if (ACTION_STOP.equals(action)) {
            stopPlayback();
        } else if (ACTION_NEXT.equals(action)) {
            playNextInternal(false);
        } else if (ACTION_PREVIOUS.equals(action)) {
            playPrevious();
        } else if (ACTION_TOGGLE_SHUFFLE.equals(action)) {
            toggleShuffle();
        } else if (ACTION_LIKE.equals(action)) {
            likeCurrent();
        } else if (ACTION_DISLIKE.equals(action)) {
            dislikeCurrent();
        } else if (ACTION_ADD_CURRENT_TO_PLAYLIST.equals(action)) {
            addCurrentToYandexPlaylist(
                    intent.getIntExtra(EXTRA_PLAYLIST_KIND, -1),
                    intent.getStringExtra(EXTRA_PLAYLIST_TITLE)
            );
        } else if (ACTION_CREATE_PLAYLIST_AND_ADD.equals(action)) {
            createYandexPlaylistAndAddCurrent(intent.getStringExtra(EXTRA_PLAYLIST_TITLE));
        }
        return ACTION_STOP.equals(action) ? START_NOT_STICKY : START_STICKY;
    }

    @Override
    public void onDestroy() {
        saveCurrentSourceProgress();
        persistCurrentPlaybackState();
        cancelPositionSave();
        closePfd();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
        stopSidebarWatchdog();
        super.onDestroy();
    }

    private void restorePlaybackState() {
        SharedPreferences prefs = getSharedPreferences(STATE_PREFS, MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_HAS_STATE, false)) {
            return;
        }
        List<YandexMusicClient.Track> restoredQueue = tracksFromJson(prefs.getString(KEY_QUEUE_JSON, ""));
        if (restoredQueue.isEmpty()) {
            return;
        }
        queue.clear();
        queue.addAll(restoredQueue);
        queueIndex = clamp(prefs.getInt(EXTRA_INDEX, 0), 0, queue.size() - 1);
        currentSourceType = prefs.getInt(EXTRA_SOURCE_TYPE, SOURCE_WAVE);
        currentPlaylistKind = prefs.getInt(EXTRA_PLAYLIST_KIND, -1);
        currentLocalPlaylistId = prefs.getString(EXTRA_LOCAL_PLAYLIST_ID, "");
        currentSourceTitle = prefs.getString(EXTRA_SOURCE_TITLE, sourceTitleFor(currentSourceType));
        if (currentSourceTitle == null || currentSourceTitle.trim().isEmpty()) {
            currentSourceTitle = sourceTitleFor(currentSourceType);
        }
        waveMode = currentSourceType == SOURCE_WAVE;
        playMode = waveMode ? PLAY_MODE_ORDER : prefs.getInt(EXTRA_PLAY_MODE, PLAY_MODE_ORDER);
        shuffle = !waveMode && playMode == PLAY_MODE_SHUFFLE;
        pendingSeekMs = Math.max(0L, prefs.getLong(EXTRA_POSITION, 0L));
        prepared = false;
        loading = false;
        statusText = prefs.getString(EXTRA_STATUS, "Ready to resume");
        if (statusText == null || statusText.trim().isEmpty()) {
            statusText = "Ready to resume";
        }
        if (waveMode) {
            waveQueue = new YmpRepository.WaveQueue(queue, "", "", null);
        }
        Diagnostics.log(this, "YMP restored playback state: source=" + currentSourceType
                + ", index=" + queueIndex
                + ", queue=" + queue.size()
                + ", position=" + pendingSeekMs);
    }

    @Override
    public BrowserRoot onGetRoot(String clientPackageName, int clientUid, Bundle rootHints) {
        return new BrowserRoot(ROOT_ID, null);
    }

    @Override
    public void onLoadChildren(String parentId, Result<List<MediaBrowser.MediaItem>> result) {
        List<MediaBrowser.MediaItem> items = new ArrayList<>();
        if (ROOT_ID.equals(parentId)) {
            items.add(playableItem(MEDIA_ID_WAVE, "My Wave", "Dynamic Yandex Music radio"));
            items.add(playableItem(MEDIA_ID_LIKED_CACHE, "Downloaded liked tracks", "Offline favorite cache"));
        }
        result.sendResult(items);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }

    private void playMyWave() {
        if (!hasTokenOrWarn()) {
            return;
        }
        startLoading("Loading My Wave...");
        new Thread(() -> {
            try {
                YmpRepository.WaveQueue loaded = repository.loadInitialWave();
                if (loaded.tracks.isEmpty()) {
                    postStatus("My Wave returned no tracks");
                    return;
                }
                repository.sendWaveRadioStarted(loaded);
                mainHandler.post(() -> {
                    waveQueue = loaded;
                    setQueue(loaded.tracks, SOURCE_WAVE, "My Wave");
                    forceInitialWavePrefetch = true;
                    playAt(startIndexForPlayback());
                });
            } catch (Exception ex) {
                Diagnostics.log(this, "YMP My Wave load failed", ex);
                postStatus("My Wave failed: " + ex.getMessage());
            }
        }, "YMP-LoadWave").start();
    }

    private void playLikedCache() {
        startLoading("Loading downloaded liked tracks...");
        new Thread(() -> {
            List<YandexMusicClient.Track> tracks = repository.likedCacheTracks();
            mainHandler.post(() -> {
                if (tracks.isEmpty()) {
                    statusText = "Liked cache is empty";
                    loading = false;
                    updateSession();
                    updateNotification();
                    broadcastStatus();
                    return;
                }
                setQueue(tracks, SOURCE_OFFLINE, "Offline mode");
                playAt(startIndexForPlayback());
            });
        }, "YMP-LoadLikedCache").start();
    }

    private void playPlaylist(int kind, String title) {
        if (!hasTokenOrWarn()) {
            return;
        }
        if (kind < 0) {
            postStatus("Playlist is not selected");
            return;
        }
        String safeTitle = title == null || title.trim().isEmpty() ? "Playlist " + kind : title.trim();
        startLoading("Loading playlist: " + safeTitle);
        new Thread(() -> {
            try {
                List<YandexMusicClient.Track> tracks = repository.playlistTracks(kind);
                mainHandler.post(() -> {
                    if (tracks.isEmpty()) {
                        statusText = "Playlist is empty: " + safeTitle;
                        loading = false;
                        updateSession();
                        updateNotification();
                        broadcastStatus();
                        return;
                    }
                    setQueue(tracks, SOURCE_PLAYLIST, safeTitle, kind, "");
                    playAt(startIndexForPlayback());
                });
            } catch (Exception ex) {
                Diagnostics.log(this, "YMP playlist load failed: " + kind, ex);
                postStatus("Playlist failed: " + safeTitle + " (" + ex.getMessage() + ")");
            }
        }, "YMP-LoadPlaylist").start();
    }

    private void playLocalPlaylist(String playlistId, String title) {
        if (playlistId == null || playlistId.trim().isEmpty()) {
            postStatus("Local playlist is not selected");
            return;
        }
        String safeTitle = title == null || title.trim().isEmpty() ? "Local playlist" : title.trim();
        startLoading("Loading local playlist: " + safeTitle);
        new Thread(() -> {
            try {
                List<YandexMusicClient.Track> tracks = repository.localPlaylistTracks(playlistId);
                mainHandler.post(() -> {
                    if (tracks.isEmpty()) {
                        statusText = "Local playlist is empty: " + safeTitle;
                        loading = false;
                        updateSession();
                        updateNotification();
                        broadcastStatus();
                        return;
                    }
                    setQueue(tracks, SOURCE_LOCAL_PLAYLIST, safeTitle, -1, playlistId);
                    playAt(startIndexForPlayback());
                });
            } catch (Exception ex) {
                Diagnostics.log(this, "YMP local playlist load failed: " + playlistId, ex);
                postStatus("Local playlist failed: " + safeTitle + " (" + ex.getMessage() + ")");
            }
        }, "YMP-LoadLocalPlaylist").start();
    }

    private void playSearchTrack(String trackKey, String label) {
        if (trackKey == null || trackKey.trim().isEmpty()) {
            postStatus("Search track is not selected");
            return;
        }
        String safeTitle = label == null || label.trim().isEmpty() ? "Search result" : label.trim();
        startLoading("Loading search result: " + safeTitle);
        new Thread(() -> {
            try {
                List<YandexMusicClient.Track> tracks = repository.searchTrackQueue(trackKey.trim());
                mainHandler.post(() -> {
                    if (tracks.isEmpty()) {
                        statusText = "Search result has no playable tracks: " + safeTitle;
                        loading = false;
                        updateSession();
                        updateNotification();
                        broadcastStatus();
                        return;
                    }
                    setQueue(tracks, SOURCE_SEARCH, safeTitle);
                    playAt(startIndexForPlayback());
                });
            } catch (Exception ex) {
                Diagnostics.log(this, "YMP search track load failed: " + trackKey, ex);
                postStatus("Search track failed: " + safeTitle + " (" + ex.getMessage() + ")");
            }
        }, "YMP-LoadSearchTrack").start();
    }

    private void playAlbum(String albumId, String title) {
        if (albumId == null || albumId.trim().isEmpty()) {
            postStatus("Album is not selected");
            return;
        }
        String safeTitle = title == null || title.trim().isEmpty() ? "Album" : title.trim();
        startLoading("Loading album: " + safeTitle);
        new Thread(() -> {
            try {
                List<YandexMusicClient.Track> tracks = repository.albumTracks(albumId.trim());
                mainHandler.post(() -> {
                    if (tracks.isEmpty()) {
                        statusText = "Album has no playable tracks: " + safeTitle;
                        loading = false;
                        updateSession();
                        updateNotification();
                        broadcastStatus();
                        return;
                    }
                    setQueue(tracks, SOURCE_SEARCH, safeTitle);
                    playAt(startIndexForPlayback());
                });
            } catch (Exception ex) {
                Diagnostics.log(this, "YMP album load failed: " + albumId, ex);
                postStatus("Album failed: " + safeTitle + " (" + ex.getMessage() + ")");
            }
        }, "YMP-LoadAlbum").start();
    }

    private void playArtist(String artistId, String title) {
        if (artistId == null || artistId.trim().isEmpty()) {
            postStatus("Artist is not selected");
            return;
        }
        String safeTitle = title == null || title.trim().isEmpty() ? "Artist" : title.trim();
        startLoading("Loading artist: " + safeTitle);
        new Thread(() -> {
            try {
                List<YandexMusicClient.Track> tracks = repository.artistTracks(artistId.trim());
                mainHandler.post(() -> {
                    if (tracks.isEmpty()) {
                        statusText = "Artist has no playable tracks: " + safeTitle;
                        loading = false;
                        updateSession();
                        updateNotification();
                        broadcastStatus();
                        return;
                    }
                    setQueue(tracks, SOURCE_SEARCH, safeTitle);
                    playAt(startIndexForPlayback());
                });
            } catch (Exception ex) {
                Diagnostics.log(this, "YMP artist load failed: " + artistId, ex);
                postStatus("Artist failed: " + safeTitle + " (" + ex.getMessage() + ")");
            }
        }, "YMP-LoadArtist").start();
    }

    private void selectSourceOnly(int sourceType, int playlistKind, String localPlaylistId, String sourceTitle) {
        saveCurrentSourceProgress();
        stopPlayerForSourceSwitch();
        queue.clear();
        queueIndex = -1;
        currentSourceType = sourceType;
        currentPlaylistKind = sourceType == SOURCE_PLAYLIST ? playlistKind : -1;
        currentLocalPlaylistId = sourceType == SOURCE_LOCAL_PLAYLIST && localPlaylistId != null
                ? localPlaylistId
                : "";
        currentSourceTitle = sourceTitle == null || sourceTitle.trim().isEmpty()
                ? sourceTitleFor(sourceType)
                : sourceTitle.trim();
        waveMode = sourceType == SOURCE_WAVE;
        if (waveMode) {
            playMode = PLAY_MODE_ORDER;
            shuffle = false;
        }
        pendingSeekMs = 0L;
        statusText = currentSourceTitle + " selected. Press Play to start.";
        updateSession();
        updateNotification();
        broadcastStatus(true);
    }

    private void setQueue(List<YandexMusicClient.Track> tracks, int sourceType, String sourceTitle) {
        setQueue(tracks, sourceType, sourceTitle, currentPlaylistKind, currentLocalPlaylistId);
    }

    private void setQueue(
            List<YandexMusicClient.Track> tracks,
            int sourceType,
            String sourceTitle,
            int playlistKind,
            String localPlaylistId
    ) {
        saveCurrentSourceProgress();
        queue.clear();
        queue.addAll(tracks);
        currentSourceType = sourceType;
        currentPlaylistKind = sourceType == SOURCE_PLAYLIST ? playlistKind : -1;
        currentLocalPlaylistId = sourceType == SOURCE_LOCAL_PLAYLIST && localPlaylistId != null
                ? localPlaylistId
                : "";
        if (sourceType == SOURCE_LOCAL_PLAYLIST) {
            likedTrackKeys.removeIf(LocalPlaylistStore::isLocalTrackKey);
            likedTrackKeys.addAll(repository.localFavoriteTrackKeys());
        }
        currentSourceTitle = sourceTitle == null || sourceTitle.trim().isEmpty()
                ? sourceTitleFor(sourceType)
                : sourceTitle.trim();
        waveMode = sourceType == SOURCE_WAVE;
        SavedSourceProgress progress = savedProgressFor(sourceType, currentPlaylistKind, currentLocalPlaylistId);
        queueIndex = restoredQueueIndex(progress);
        pendingSeekMs = queueIndex >= 0 ? Math.max(0L, progress.positionMs) : 0L;
        prepared = false;
        loading = false;
        prefetchingTrackKey = "";
        prefetchedTrackKey = "";
        lastWaveFeedbackTrackKey = "";
        wavePrefetching = false;
        forceInitialWavePrefetch = false;
        if (waveMode) {
            playMode = PLAY_MODE_ORDER;
            shuffle = false;
        } else if (progress.playMode >= PLAY_MODE_ORDER && progress.playMode <= PLAY_MODE_REPEAT) {
            playMode = progress.playMode;
            shuffle = playMode == PLAY_MODE_SHUFFLE;
        }
        statusText = currentSourceTitle + " ready";
        updateSession();
        broadcastStatus();
    }

    private int startIndexForPlayback() {
        return queueIndex >= 0 && queueIndex < queue.size() ? queueIndex : 0;
    }

    private void playAt(int index) {
        if (index < 0 || index >= queue.size()) {
            statusText = "End of queue";
            loading = false;
            updateSession();
            updateNotification();
            broadcastStatus();
            return;
        }
        if (index != queueIndex) {
            saveCurrentSourceProgress();
            pendingSeekMs = 0L;
        }
        queueIndex = index;
        YandexMusicClient.Track track = queue.get(queueIndex);
        maybeSendWaveTrackStarted(track);
        refreshLikedState(track);
        prepared = false;
        startLoading("Loading: " + track.artist + " - " + track.title);
        new Thread(() -> {
            try {
                ParcelFileDescriptor pfd = repository.openForPlayback(track);
                mainHandler.post(() -> preparePlayer(track, pfd));
            } catch (Exception ex) {
                Diagnostics.log(this, "YMP track open failed: " + track.key, ex);
                mainHandler.post(() -> handleTrackFailure(track, ex));
            }
        }, "YMP-OpenTrack").start();
    }

    private void preparePlayer(YandexMusicClient.Track track, ParcelFileDescriptor pfd) {
        try {
            closePfd();
            currentPfd = pfd;
            mediaPlayer.reset();
            mediaPlayer.setDataSource(currentPfd.getFileDescriptor());
            mediaPlayer.prepareAsync();
            statusText = "Preparing: " + track.title;
            updateSession();
            updateNotification();
            broadcastStatus();
        } catch (Exception ex) {
            closePfd();
            Diagnostics.log(this, "YMP prepare failed: " + track.key, ex);
            handleTrackFailure(track, ex);
        }
    }

    private void handleTrackFailure(YandexMusicClient.Track track, Exception ex) {
        if (track != null && currentSourceType == SOURCE_LOCAL_PLAYLIST
                && LocalPlaylistStore.isLocalTrackKey(track.key)) {
            skipUnavailableLocalTrack(track, ex);
            return;
        }
        statusText = "Track failed: " + (track == null ? "" : track.title) + " (" + ex.getMessage() + ")";
        loading = false;
        prepared = false;
        updateSession();
        updateNotification();
        broadcastStatus();
    }

    private void skipUnavailableLocalTrack(YandexMusicClient.Track failedTrack, Exception ex) {
        int failedIndex = queueIndex;
        if (failedIndex >= 0 && failedIndex < queue.size()
                && queue.get(failedIndex).key.equals(failedTrack.key)) {
            queue.remove(failedIndex);
            queueIndex = failedIndex - 1;
        } else {
            for (int i = queue.size() - 1; i >= 0; i--) {
                if (queue.get(i).key.equals(failedTrack.key)) {
                    queue.remove(i);
                    if (queueIndex >= i) {
                        queueIndex--;
                    }
                }
            }
        }
        Diagnostics.log(this, "YMP skipped unavailable local track: "
                + failedTrack.key + ", error=" + ex.getMessage());
        closePfd();
        loading = false;
        prepared = false;
        if (queue.isEmpty()) {
            statusText = "No accessible files in local playlist: " + currentSourceTitle;
            updateSession();
            updateNotification();
            broadcastStatus();
            return;
        }
        int next = playMode == PLAY_MODE_SHUFFLE && queue.size() > 1
                ? random.nextInt(queue.size())
                : Math.min(failedIndex, queue.size() - 1);
        statusText = "Skipped unavailable local file: " + failedTrack.title;
        updateSession();
        updateNotification();
        broadcastStatus();
        playAt(Math.max(0, next));
    }

    private void togglePlayPause() {
        if (mediaPlayer == null) {
            return;
        }
        if (safeIsPlaying()) {
            saveCurrentSourceProgress();
            mediaPlayer.pause();
            statusText = "Paused";
            cancelPositionSave();
        } else if (prepared) {
            ensureForeground();
            mediaPlayer.start();
            statusText = "Playing";
            schedulePositionSave();
        } else if (!queue.isEmpty()) {
            playAt(queueIndex < 0 ? 0 : queueIndex);
            return;
        } else {
            statusText = "Choose My Wave or cache first";
        }
        updateSession();
        updateNotification();
        broadcastStatus();
    }

    private void playNextInternal(boolean fromCompletion) {
        if (queue.isEmpty()) {
            statusText = "Queue is empty";
            broadcastStatus();
            return;
        }
        int next = queueIndex + 1;
        if (waveMode && next >= queue.size()) {
            fetchMoreWaveThenNext(fromCompletion);
            return;
        }
        if (playMode == PLAY_MODE_SHUFFLE && !waveMode && queue.size() > 1) {
            playAt(random.nextInt(queue.size()));
            return;
        }
        if (next >= queue.size()) {
            if (!waveMode && playMode == PLAY_MODE_REPEAT) {
                playAt(0);
                return;
            }
            statusText = fromCompletion ? "End of queue" : "Reached end of queue";
            updateSession();
            updateNotification();
            broadcastStatus();
            return;
        }
        playAt(next);
    }

    private void fetchMoreWaveThenNext(boolean fromCompletion) {
        if (loading) {
            statusText = "My Wave is already loading";
            broadcastStatus();
            return;
        }
        startLoading("Loading more My Wave...");
        new Thread(() -> {
            try {
                int before = queue.size();
                waveQueue = repository.loadMoreWave(waveQueue);
                mainHandler.post(() -> {
                    appendWaveTracks();
                    int next = queueIndex + 1;
                    if (next < queue.size()) {
                        playAt(next);
                    } else {
                        statusText = fromCompletion ? "My Wave has no next track yet" : "No more My Wave tracks yet";
                        loading = false;
                        updateSession();
                        updateNotification();
                        broadcastStatus();
                    }
                    Diagnostics.log(this, "YMP My Wave queue size: before=" + before + ", after=" + queue.size());
                });
            } catch (Exception ex) {
                Diagnostics.log(this, "YMP My Wave load-more failed", ex);
                postStatus("My Wave load-more failed: " + ex.getMessage());
            }
        }, "YMP-MoreWave").start();
    }

    private void maybePrefetchWave() {
        prefetchWave(false);
    }

    private void prefetchWave(boolean force) {
        if (!waveMode || loading || waveQueue == null || wavePrefetching
                || (!force && queueIndex + 1 < queue.size())) {
            return;
        }
        wavePrefetching = true;
        YmpRepository.WaveQueue sourceWaveQueue = waveQueue;
        new Thread(() -> {
            try {
                YmpRepository.WaveQueue loaded = repository.loadMoreWave(sourceWaveQueue);
                mainHandler.post(() -> {
                    if (!waveMode || waveQueue != sourceWaveQueue) {
                        wavePrefetching = false;
                        return;
                    }
                    waveQueue = loaded;
                    wavePrefetching = false;
                    appendWaveTracks();
                    Diagnostics.log(this, "YMP background My Wave prefetch complete: queue=" + queue.size());
                });
            } catch (Exception ex) {
                mainHandler.post(() -> wavePrefetching = false);
                Diagnostics.log(this, "YMP background My Wave prefetch failed", ex);
            }
        }, "YMP-PrefetchWave").start();
    }

    private void appendWaveTracks() {
        if (waveQueue == null) {
            return;
        }
        for (YandexMusicClient.Track track : waveQueue.tracks) {
            if (!queueContains(track.key)) {
                queue.add(track);
            }
        }
        updateSession();
        updateNotification();
        broadcastStatus();
        maybePrefetchNextAudio();
    }

    private void maybePrefetchNextAudio() {
        if (currentSourceType == SOURCE_OFFLINE || currentSourceType == SOURCE_LOCAL_PLAYLIST || loading || queueIndex < 0) {
            return;
        }
        int next = queueIndex + 1;
        if (next < 0 || next >= queue.size()) {
            return;
        }
        YandexMusicClient.Track track = queue.get(next);
        if (track == null || track.key.equals(prefetchingTrackKey) || track.key.equals(prefetchedTrackKey)) {
            return;
        }
        String key = track.key;
        prefetchingTrackKey = key;
        new Thread(() -> {
            try {
                repository.prefetchForPlayback(track);
                mainHandler.post(() -> {
                    if (key.equals(prefetchingTrackKey)) {
                        prefetchedTrackKey = key;
                        prefetchingTrackKey = "";
                    }
                    Diagnostics.log(this, "YMP prefetched next track: " + key);
                });
            } catch (Exception ex) {
                mainHandler.post(() -> {
                    if (key.equals(prefetchingTrackKey)) {
                        prefetchingTrackKey = "";
                    }
                });
                Diagnostics.log(this, "YMP next track prefetch failed: " + key, ex);
            }
        }, "YMP-PrefetchAudio").start();
    }

    private void maybeSendWaveTrackStarted(YandexMusicClient.Track track) {
        if (!waveMode || waveQueue == null || track == null || track.key.equals(lastWaveFeedbackTrackKey)) {
            return;
        }
        lastWaveFeedbackTrackKey = track.key;
        repository.sendWaveTrackStarted(waveQueue, track);
    }

    private void refreshLikedState(YandexMusicClient.Track track) {
        if (track == null || LocalPlaylistStore.isLocalTrackKey(track.key)) {
            return;
        }
        if (likedKeysLoaded || TokenStore.getAccessToken(this).trim().isEmpty()) {
            return;
        }
        new Thread(() -> {
            try {
                Set<String> keys = repository.likedTrackKeys();
                mainHandler.post(() -> {
                    likedTrackKeys.clear();
                    likedTrackKeys.addAll(keys);
                    likedKeysLoaded = true;
                    broadcastStatus();
                });
            } catch (Exception ex) {
                Diagnostics.log(this, "YMP liked state refresh failed", ex);
            }
        }, "YMP-LikedState").start();
    }

    private boolean isLiked(YandexMusicClient.Track track) {
        return track != null && likedTrackKeys.contains(track.key);
    }

    private void playPrevious() {
        if (queueIndex <= 0) {
            statusText = "Already at first track";
            broadcastStatus();
            return;
        }
        playAt(queueIndex - 1);
    }

    private void stopPlayback() {
        saveCurrentSourceProgress();
        cancelPositionSave();
        boolean wasPrepared = prepared;
        loading = false;
        prepared = false;
        pendingSeekMs = 0L;
        if (mediaPlayer != null) {
            try {
                if (wasPrepared || safeIsPlaying()) {
                    mediaPlayer.stop();
                }
            } catch (Exception ex) {
                Diagnostics.log(this, "YMP MediaPlayer stop ignored", ex);
            }
            try {
                mediaPlayer.reset();
            } catch (Exception ex) {
                Diagnostics.log(this, "YMP MediaPlayer reset ignored", ex);
            }
        }
        closePfd();
        statusText = "Stopped";
        updateSession();
        stopForeground(STOP_FOREGROUND_REMOVE);
        broadcastStatus();
    }

    private void stopPlayerForSourceSwitch() {
        cancelPositionSave();
        loading = false;
        prepared = false;
        if (mediaPlayer != null) {
            try {
                if (safeIsPlaying()) {
                    mediaPlayer.stop();
                }
            } catch (Exception ex) {
                Diagnostics.log(this, "YMP source-switch stop ignored", ex);
            }
            try {
                mediaPlayer.reset();
            } catch (Exception ex) {
                Diagnostics.log(this, "YMP source-switch reset ignored", ex);
            }
        }
        closePfd();
        try {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } catch (Exception ignored) {
        }
    }

    private void toggleShuffle() {
        if (waveMode) {
            playMode = PLAY_MODE_ORDER;
            shuffle = false;
            statusText = "Queue mode is disabled for My Wave";
        } else {
            playMode = (playMode + 1) % 3;
            shuffle = playMode == PLAY_MODE_SHUFFLE;
            statusText = playMode == PLAY_MODE_SHUFFLE
                    ? "Shuffle on"
                    : playMode == PLAY_MODE_REPEAT ? "Repeat queue on" : "Queue order mode";
        }
        updateSession();
        updateNotification();
        broadcastStatus();
    }

    private void likeCurrent() {
        YandexMusicClient.Track track = currentTrack();
        if (track == null) {
            return;
        }
        if (LocalPlaylistStore.isLocalTrackKey(track.key)) {
            new Thread(() -> {
                try {
                    boolean liked = repository.toggleLocalFavorite(track);
                    mainHandler.post(() -> {
                        if (liked) {
                            likedTrackKeys.add(track.key);
                            statusText = "Added to local favorites: " + track.title;
                        } else {
                            likedTrackKeys.remove(track.key);
                            statusText = "Removed from local favorites: " + track.title;
                        }
                        loading = false;
                        updateSession();
                        updateNotification();
                        broadcastStatus();
                    });
                } catch (Exception ex) {
                    Diagnostics.log(this, "YMP local favorite toggle failed: " + track.key, ex);
                    postStatus("Local favorite failed: " + ex.getMessage());
                }
            }, "YMP-LocalLike").start();
            return;
        }
        if (!hasTokenOrWarn()) {
            return;
        }
        new Thread(() -> {
            try {
                boolean liked = isLiked(track);
                if (liked) {
                    repository.removeLike(track);
                    likedTrackKeys.remove(track.key);
                    postStatus("Removed from favorites: " + track.title);
                } else {
                    repository.like(track, YmpSettings.isAutoCacheLikedEnabled(this));
                    likedTrackKeys.add(track.key);
                    postStatus("Added to favorites: " + track.title);
                }
            } catch (Exception ex) {
                Diagnostics.log(this, "YMP like failed: " + track.key, ex);
                postStatus("Like failed: " + ex.getMessage());
            }
        }, "YMP-Like").start();
    }

    private void dislikeCurrent() {
        YandexMusicClient.Track track = currentTrack();
        if (track == null) {
            return;
        }
        if (LocalPlaylistStore.isLocalTrackKey(track.key)) {
            new Thread(() -> {
                try {
                    boolean removed = repository.removeLocalFavorite(track);
                    likedTrackKeys.remove(track.key);
                    postStatus((removed ? "Removed from local favorites: " : "Not in local favorites: ") + track.title);
                } catch (Exception ex) {
                    Diagnostics.log(this, "YMP local dislike failed: " + track.key, ex);
                    postStatus("Local dislike failed: " + ex.getMessage());
                }
            }, "YMP-LocalDislike").start();
            return;
        }
        if (!hasTokenOrWarn()) {
            return;
        }
        new Thread(() -> {
            try {
                repository.dislike(track);
                if (waveMode) {
                    repository.sendWaveDislike(waveQueue, track);
                }
                likedTrackKeys.remove(track.key);
                mainHandler.post(() -> {
                    statusText = "Disliked: " + track.title;
                    playNextInternal(false);
                });
            } catch (Exception ex) {
                Diagnostics.log(this, "YMP dislike failed: " + track.key, ex);
                postStatus("Dislike failed: " + ex.getMessage());
            }
        }, "YMP-Dislike").start();
    }

    private void addCurrentToYandexPlaylist(int kind, String title) {
        YandexMusicClient.Track track = currentTrack();
        if (track == null) {
            postStatus("No Yandex track is playing");
            return;
        }
        if (LocalPlaylistStore.isLocalTrackKey(track.key)) {
            postStatus("Local files cannot be added to Yandex playlists");
            return;
        }
        if (!hasTokenOrWarn()) {
            return;
        }
        if (kind < 0) {
            postStatus("Yandex playlist is not selected");
            return;
        }
        String safeTitle = title == null || title.trim().isEmpty() ? "playlist " + kind : title.trim();
        startLoading("Adding to playlist: " + safeTitle);
        new Thread(() -> {
            try {
                YandexMusicClient.PlaylistSummary playlist = repository.addTrackToPlaylist(kind, track);
                postStatus("Added to playlist: " + playlist.title);
            } catch (Exception ex) {
                Diagnostics.log(this, "YMP add current track to playlist failed", ex);
                postStatus("Add to playlist failed: " + ex.getMessage());
            }
        }, "YMP-AddToPlaylist").start();
    }

    private void createYandexPlaylistAndAddCurrent(String title) {
        YandexMusicClient.Track track = currentTrack();
        if (track == null) {
            postStatus("No Yandex track is playing");
            return;
        }
        if (LocalPlaylistStore.isLocalTrackKey(track.key)) {
            postStatus("Local files cannot be added to Yandex playlists");
            return;
        }
        if (!hasTokenOrWarn()) {
            return;
        }
        String safeTitle = title == null ? "" : title.trim();
        if (safeTitle.isEmpty()) {
            postStatus("Playlist name is empty");
            return;
        }
        startLoading("Creating playlist: " + safeTitle);
        new Thread(() -> {
            try {
                YandexMusicClient.PlaylistSummary playlist = repository.createPlaylistAndAddTrack(safeTitle, track);
                postStatus("Created playlist and added track: " + playlist.title);
            } catch (Exception ex) {
                Diagnostics.log(this, "YMP create playlist and add current track failed", ex);
                postStatus("Create playlist failed: " + ex.getMessage());
            }
        }, "YMP-CreatePlaylistAdd").start();
    }

    private void startLoading(String text) {
        loading = true;
        statusText = text;
        ensureForeground();
        updateSession();
        updateNotification();
        broadcastStatus();
    }

    private void postStatus(String text) {
        mainHandler.post(() -> {
            statusText = text == null ? "" : text;
            loading = false;
            updateSession();
            updateNotification();
            broadcastStatus();
        });
    }

    private void updateSession() {
        if (mediaSession == null) {
            return;
        }
        int state;
        if (loading) {
            state = PlaybackState.STATE_BUFFERING;
        } else if (safeIsPlaying()) {
            state = PlaybackState.STATE_PLAYING;
        } else if (prepared) {
            state = PlaybackState.STATE_PAUSED;
        } else {
            state = PlaybackState.STATE_STOPPED;
        }
        long actions = PlaybackState.ACTION_PLAY
                | PlaybackState.ACTION_PAUSE
                | PlaybackState.ACTION_PLAY_PAUSE
                | PlaybackState.ACTION_STOP
                | PlaybackState.ACTION_SKIP_TO_NEXT
                | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                | PlaybackState.ACTION_PLAY_FROM_MEDIA_ID;
        mediaSession.setPlaybackState(new PlaybackState.Builder()
                .setState(state, mediaPlayerPosition(), 1.0f)
                .setActions(actions)
                .build());

        YandexMusicClient.Track track = currentTrack();
        if (track == null) {
            MediaMetadata.Builder metadata = new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, "YMPlayer")
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, "")
                    .putString(MediaMetadata.METADATA_KEY_ALBUM, statusText == null ? "" : statusText)
                    .putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, defaultArtworkUri())
                    .putString(MediaMetadata.METADATA_KEY_ART_URI, defaultArtworkUri());
            putArtwork(metadata, defaultArtworkBitmap());
            mediaSession.setMetadata(metadata.build());
        } else {
            ensureMetadataCover(track);
            String artworkUri = track.coverUrl == null || track.coverUrl.trim().isEmpty()
                    ? defaultArtworkUri()
                    : track.coverUrl;
            MediaMetadata.Builder metadata = new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, track.title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, track.artist)
                    .putString(MediaMetadata.METADATA_KEY_ALBUM, track.album)
                    .putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, artworkUri)
                    .putString(MediaMetadata.METADATA_KEY_ART_URI, artworkUri)
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, track.durationMs);
            putArtwork(metadata, metadataCoverForTrack(track));
            mediaSession.setMetadata(metadata.build());
        }
        mediaSession.setActive(true);
    }

    private void putArtwork(MediaMetadata.Builder metadata, Bitmap cover) {
        Bitmap safeCover = cover == null ? defaultArtworkBitmap() : cover;
        if (safeCover != null) {
            metadata.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, safeCover);
            metadata.putBitmap(MediaMetadata.METADATA_KEY_ART, safeCover);
        }
    }

    private String artworkKeyFor(YandexMusicClient.Track track) {
        if (track == null) {
            return "";
        }
        if (LocalPlaylistStore.isLocalTrackKey(track.key)) {
            return track.key;
        }
        return track.coverUrl == null ? "" : track.coverUrl;
    }

    private Bitmap metadataCoverFor(String coverUrl) {
        String url = coverUrl == null ? "" : coverUrl;
        return url.equals(metadataCoverUrl) ? metadataCoverBitmap : null;
    }

    private Bitmap metadataCoverForTrack(YandexMusicClient.Track track) {
        Bitmap cover = track == null ? null : metadataCoverFor(artworkKeyFor(track));
        return cover == null ? defaultArtworkBitmap() : cover;
    }

    private Bitmap defaultArtworkBitmap() {
        if (defaultArtworkBitmap == null) {
            defaultArtworkBitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
        }
        return defaultArtworkBitmap;
    }

    private String defaultArtworkUri() {
        return "android.resource://" + getPackageName() + "/" + R.mipmap.ic_launcher;
    }

    private void ensureMetadataCover(YandexMusicClient.Track track) {
        String key = artworkKeyFor(track).trim();
        if (key.isEmpty()) {
            metadataCoverUrl = "";
            metadataCoverBitmap = null;
            metadataCoverLoading = false;
            return;
        }
        if (!key.equals(metadataCoverUrl)) {
            metadataCoverUrl = key;
            metadataCoverBitmap = null;
            metadataCoverLoading = false;
        }
        if (metadataCoverBitmap != null || metadataCoverLoading) {
            return;
        }
        metadataCoverLoading = true;
        new Thread(() -> {
            Bitmap bitmap = null;
            try {
                bitmap = LocalPlaylistStore.isLocalTrackKey(track.key)
                        ? localEmbeddedBitmap(track.key)
                        : downloadBitmap(key);
            } catch (Exception ex) {
                Diagnostics.log(this, "YMP metadata cover load failed", ex);
            }
            Bitmap loaded = bitmap;
            mainHandler.post(() -> {
                if (key.equals(metadataCoverUrl)) {
                    metadataCoverBitmap = loaded;
                    metadataCoverLoading = false;
                    if (loaded != null) {
                        updateSession();
                        updateNotification();
                    }
                }
            });
        }, "YMP-MetadataCover").start();
    }

    private Bitmap localEmbeddedBitmap(String localTrackKey) {
        Uri uri = LocalPlaylistStore.uriFromTrackKey(localTrackKey);
        if (uri == null) {
            return null;
        }
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(this, uri);
            byte[] bytes = retriever.getEmbeddedPicture();
            return bytes == null || bytes.length == 0
                    ? null
                    : BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception ex) {
            return null;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
    }

    private static Bitmap downloadBitmap(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        try (InputStream input = connection.getInputStream()) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            byte[] bytes = output.toByteArray();
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } finally {
            connection.disconnect();
        }
    }

    private long mediaPlayerPosition() {
        try {
            return mediaPlayer != null && prepared ? mediaPlayer.getCurrentPosition() : 0L;
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private void updateNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null && canPostNotifications()) {
            manager.notify(NOTIFICATION_ID, notification());
        }
    }

    private void ensureForeground() {
        createChannel();
        startForeground(NOTIFICATION_ID, notification());
        ensureSideBar(false);
    }

    private Notification notification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent content = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());

        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_yaplay)
                .setContentIntent(content)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setContentTitle(notificationTitle())
                .setContentText(notificationText());
        YandexMusicClient.Track track = currentTrack();
        Bitmap cover = metadataCoverForTrack(track);
        if (cover != null) {
            builder.setLargeIcon(cover);
        }

        builder.addAction(R.drawable.ic_stat_yaplay, "Prev", serviceIntent(ACTION_PREVIOUS, 10));
        builder.addAction(R.drawable.ic_stat_yaplay, safeIsPlaying() ? "Pause" : "Play",
                serviceIntent(ACTION_PLAY_PAUSE, 11));
        builder.addAction(R.drawable.ic_stat_yaplay, "Next", serviceIntent(ACTION_NEXT, 12));
        builder.addAction(R.drawable.ic_stat_yaplay, "Stop", serviceIntent(ACTION_STOP, 13));
        builder.setStyle(new Notification.MediaStyle()
                .setMediaSession(mediaSession.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2));
        return builder.build();
    }

    private String notificationTitle() {
        YandexMusicClient.Track track = currentTrack();
        return track == null ? "YMPlayer" : track.title;
    }

    private String notificationText() {
        YandexMusicClient.Track track = currentTrack();
        if (track == null) {
            return statusText;
        }
        String mode = currentSourceTitle == null || currentSourceTitle.isEmpty()
                ? sourceTitleFor(currentSourceType)
                : currentSourceTitle;
        return track.artist + " - " + mode + " " + (queueIndex + 1) + "/" + queue.size();
    }

    private PendingIntent serviceIntent(String action, int requestCode) {
        Intent intent = new Intent(this, YmpPlaybackService.class);
        intent.setAction(action);
        return PendingIntent.getService(this, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());
    }

    private void broadcastStatus() {
        broadcastStatus(false);
    }

    private void broadcastStatus(boolean sourceSelected) {
        Intent intent = buildStatusIntent(sourceSelected);
        latestStatus = new Intent(intent);
        persistPlaybackState(intent);
        sendBroadcast(intent);
    }

    private Intent buildStatusIntent(boolean sourceSelected) {
        Intent intent = new Intent(ACTION_STATUS);
        intent.setPackage(getPackageName());
        YandexMusicClient.Track track = currentTrack();
        intent.putExtra(EXTRA_TITLE, track == null ? "" : track.title);
        intent.putExtra(EXTRA_ARTIST, track == null ? "" : track.artist);
        intent.putExtra(EXTRA_ALBUM, track == null ? "" : track.album);
        intent.putExtra(EXTRA_COVER_URL, track == null ? "" : track.coverUrl);
        intent.putExtra(EXTRA_STATUS, statusText);
        intent.putExtra(EXTRA_QUEUE, queue.size());
        intent.putExtra(EXTRA_INDEX, queueIndex);
        intent.putExtra(EXTRA_WAVE, waveMode);
        intent.putExtra(EXTRA_SHUFFLE, shuffle);
        intent.putExtra(EXTRA_PLAYING, safeIsPlaying());
        intent.putExtra(EXTRA_PREPARED, prepared);
        intent.putExtra(EXTRA_PLAY_MODE, playMode);
        intent.putExtra(EXTRA_LIKED, track != null && isLiked(track));
        intent.putExtra(EXTRA_SOURCE_TYPE, currentSourceType);
        intent.putExtra(EXTRA_SOURCE_TITLE, currentSourceTitle);
        intent.putExtra(EXTRA_PLAYLIST_KIND, currentPlaylistKind);
        intent.putExtra(EXTRA_LOCAL_PLAYLIST_ID, currentLocalPlaylistId);
        intent.putExtra(EXTRA_LOCAL_PLAYLIST_TITLE, currentSourceType == SOURCE_LOCAL_PLAYLIST ? currentSourceTitle : "");
        intent.putExtra(EXTRA_TRACK_KEY, track == null ? "" : track.key);
        long position = (mediaPlayer != null && (prepared || safeIsPlaying()))
                ? mediaPlayerPosition()
                : pendingSeekMs;
        intent.putExtra(EXTRA_POSITION, position);
        intent.putExtra(EXTRA_SOURCE_SELECTED, sourceSelected);
        intent.putExtra(EXTRA_AUDIO_SESSION_ID, mediaPlayer == null ? 0 : mediaPlayer.getAudioSessionId());
        return intent;
    }

    private void persistPlaybackState(Intent intent) {
        try {
            SharedPreferences.Editor editor = getSharedPreferences(STATE_PREFS, MODE_PRIVATE).edit()
                    .putBoolean(KEY_HAS_STATE, true)
                    .putString(KEY_QUEUE_JSON, queueToJson().toString())
                    .putString(EXTRA_TITLE, intent.getStringExtra(EXTRA_TITLE))
                    .putString(EXTRA_ARTIST, intent.getStringExtra(EXTRA_ARTIST))
                    .putString(EXTRA_ALBUM, intent.getStringExtra(EXTRA_ALBUM))
                    .putString(EXTRA_COVER_URL, intent.getStringExtra(EXTRA_COVER_URL))
                    .putString(EXTRA_STATUS, intent.getStringExtra(EXTRA_STATUS))
                    .putInt(EXTRA_QUEUE, intent.getIntExtra(EXTRA_QUEUE, 0))
                    .putInt(EXTRA_INDEX, intent.getIntExtra(EXTRA_INDEX, -1))
                    .putBoolean(EXTRA_WAVE, intent.getBooleanExtra(EXTRA_WAVE, false))
                    .putBoolean(EXTRA_SHUFFLE, intent.getBooleanExtra(EXTRA_SHUFFLE, false))
                    .putBoolean(EXTRA_PLAYING, intent.getBooleanExtra(EXTRA_PLAYING, false))
                    .putBoolean(EXTRA_PREPARED, intent.getBooleanExtra(EXTRA_PREPARED, false))
                    .putInt(EXTRA_PLAY_MODE, intent.getIntExtra(EXTRA_PLAY_MODE, PLAY_MODE_ORDER))
                    .putBoolean(EXTRA_LIKED, intent.getBooleanExtra(EXTRA_LIKED, false))
                    .putInt(EXTRA_SOURCE_TYPE, intent.getIntExtra(EXTRA_SOURCE_TYPE, SOURCE_WAVE))
                    .putString(EXTRA_SOURCE_TITLE, intent.getStringExtra(EXTRA_SOURCE_TITLE))
                    .putInt(EXTRA_PLAYLIST_KIND, intent.getIntExtra(EXTRA_PLAYLIST_KIND, -1))
                    .putString(EXTRA_LOCAL_PLAYLIST_ID, intent.getStringExtra(EXTRA_LOCAL_PLAYLIST_ID))
                    .putString(EXTRA_LOCAL_PLAYLIST_TITLE, intent.getStringExtra(EXTRA_LOCAL_PLAYLIST_TITLE))
                    .putString(EXTRA_TRACK_KEY, intent.getStringExtra(EXTRA_TRACK_KEY))
                    .putLong(EXTRA_POSITION, intent.getLongExtra(EXTRA_POSITION, 0L));
            editor.apply();
        } catch (Exception ex) {
            Diagnostics.log(this, "YMP persist playback state failed", ex);
        }
    }

    private void persistCurrentPlaybackState() {
        persistPlaybackState(buildStatusIntent(false));
        saveCurrentSourceProgress();
    }

    private static String sourceTitleFor(int sourceType) {
        if (sourceType == SOURCE_OFFLINE) {
            return "Offline mode";
        }
        if (sourceType == SOURCE_PLAYLIST) {
            return "Playlist";
        }
        if (sourceType == SOURCE_LOCAL_PLAYLIST) {
            return "Local playlist";
        }
        if (sourceType == SOURCE_SEARCH) {
            return "Search";
        }
        return "My Wave";
    }

    private void saveCurrentSourceProgress() {
        YandexMusicClient.Track track = currentTrack();
        if (track == null || currentSourceType == SOURCE_SEARCH) {
            return;
        }
        String key = sourceStateKey(currentSourceType, currentPlaylistKind, currentLocalPlaylistId);
        if (key.isEmpty()) {
            return;
        }
        long position = mediaPlayer != null && (prepared || safeIsPlaying())
                ? mediaPlayerPosition()
                : pendingSeekMs;
        getSharedPreferences(STATE_PREFS, MODE_PRIVATE).edit()
                .putString(key + KEY_SOURCE_TRACK, track.key)
                .putInt(key + KEY_SOURCE_INDEX, queueIndex)
                .putLong(key + KEY_SOURCE_POSITION, Math.max(0L, position))
                .putInt(key + KEY_SOURCE_PLAY_MODE, playMode)
                .apply();
    }

    private SavedSourceProgress savedProgressFor(int sourceType, int playlistKind, String localPlaylistId) {
        String key = sourceStateKey(sourceType, playlistKind, localPlaylistId);
        if (key.isEmpty()) {
            return SavedSourceProgress.EMPTY;
        }
        SharedPreferences prefs = getSharedPreferences(STATE_PREFS, MODE_PRIVATE);
        return new SavedSourceProgress(
                prefs.getString(key + KEY_SOURCE_TRACK, ""),
                prefs.getInt(key + KEY_SOURCE_INDEX, -1),
                prefs.getLong(key + KEY_SOURCE_POSITION, 0L),
                prefs.getInt(key + KEY_SOURCE_PLAY_MODE, PLAY_MODE_ORDER)
        );
    }

    private int restoredQueueIndex(SavedSourceProgress progress) {
        if (progress.trackKey != null && !progress.trackKey.isEmpty()) {
            for (int i = 0; i < queue.size(); i++) {
                if (progress.trackKey.equals(queue.get(i).key)) {
                    return i;
                }
            }
        }
        return progress.index >= 0 && progress.index < queue.size() ? progress.index : -1;
    }

    private static String sourceStateKey(int sourceType, int playlistKind, String localPlaylistId) {
        if (sourceType == SOURCE_OFFLINE) {
            return KEY_SOURCE_STATE_PREFIX + "offline";
        }
        if (sourceType == SOURCE_PLAYLIST && playlistKind >= 0) {
            return KEY_SOURCE_STATE_PREFIX + "yandex." + playlistKind;
        }
        if (sourceType == SOURCE_LOCAL_PLAYLIST && localPlaylistId != null && !localPlaylistId.trim().isEmpty()) {
            return KEY_SOURCE_STATE_PREFIX + "local." + localPlaylistId.trim();
        }
        return "";
    }

    private void schedulePositionSave() {
        cancelPositionSave();
        mainHandler.postDelayed(positionSaveRunnable, POSITION_SAVE_INTERVAL_MS);
    }

    private void cancelPositionSave() {
        mainHandler.removeCallbacks(positionSaveRunnable);
    }

    private boolean safeIsPlaying() {
        try {
            return mediaPlayer != null && mediaPlayer.isPlaying();
        } catch (Exception ignored) {
            return false;
        }
    }

    private void startSidebarWatchdog() {
        if (sidebarWatchdogStarted) {
            return;
        }
        sidebarWatchdogStarted = true;
        mainHandler.post(sidebarWatchdogRunnable);
    }

    private void stopSidebarWatchdog() {
        sidebarWatchdogStarted = false;
        mainHandler.removeCallbacks(sidebarWatchdogRunnable);
    }

    private void ensureSideBar(boolean launchIfNeeded) {
        if (!YmpSettings.isEmbeddedSideBarEnabled(this) || !EmbeddedSideBarService.hasOverlayPermission(this)) {
            return;
        }
        EmbeddedSideBarService.start(this, launchIfNeeded);
    }

    private static final class SavedSourceProgress {
        static final SavedSourceProgress EMPTY = new SavedSourceProgress("", -1, 0L, PLAY_MODE_ORDER);

        final String trackKey;
        final int index;
        final long positionMs;
        final int playMode;

        SavedSourceProgress(String trackKey, int index, long positionMs, int playMode) {
            this.trackKey = trackKey == null ? "" : trackKey;
            this.index = index;
            this.positionMs = positionMs;
            this.playMode = playMode;
        }
    }

    private void createChannel() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.playback_notification_channel),
                NotificationManager.IMPORTANCE_LOW
        );
        manager.createNotificationChannel(channel);
    }

    private MediaBrowser.MediaItem playableItem(String id, String title, String subtitle) {
        android.media.MediaDescription description = new android.media.MediaDescription.Builder()
                .setMediaId(id)
                .setTitle(title)
                .setSubtitle(subtitle)
                .build();
        return new MediaBrowser.MediaItem(description, MediaBrowser.MediaItem.FLAG_PLAYABLE);
    }

    private boolean hasTokenOrWarn() {
        if (!TokenStore.getAccessToken(this).trim().isEmpty()) {
            return true;
        }
        statusText = "Sign in to Yandex first";
        loading = false;
        updateSession();
        updateNotification();
        broadcastStatus();
        return false;
    }

    private YandexMusicClient.Track currentTrack() {
        if (queueIndex < 0 || queueIndex >= queue.size()) {
            return null;
        }
        return queue.get(queueIndex);
    }

    private boolean queueContains(String key) {
        for (YandexMusicClient.Track track : queue) {
            if (track.key.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private JSONArray queueToJson() {
        JSONArray array = new JSONArray();
        for (YandexMusicClient.Track track : queue) {
            try {
                JSONObject item = new JSONObject();
                item.put("id", track.id);
                item.put("albumId", track.albumId);
                item.put("key", track.key);
                item.put("title", track.title);
                item.put("artist", track.artist);
                item.put("album", track.album);
                item.put("year", track.year);
                item.put("durationMs", track.durationMs);
                item.put("coverUrl", track.coverUrl);
                item.put("order", track.order);
                array.put(item);
            } catch (Exception ex) {
                Diagnostics.log(this, "YMP queue state track skipped", ex);
            }
        }
        return array;
    }

    private List<YandexMusicClient.Track> tracksFromJson(String json) {
        List<YandexMusicClient.Track> tracks = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) {
            return tracks;
        }
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String key = item.optString("key", "");
                if (key.isEmpty()) {
                    continue;
                }
                tracks.add(new YandexMusicClient.Track(
                        item.optString("id", ""),
                        item.optString("albumId", ""),
                        key,
                        item.optString("title", key),
                        item.optString("artist", ""),
                        item.optString("album", ""),
                        item.optInt("year", 0),
                        item.optLong("durationMs", 0L),
                        item.optString("coverUrl", ""),
                        item.optInt("order", i + 1)
                ));
            }
        } catch (Exception ex) {
            Diagnostics.log(this, "YMP restore queue state failed", ex);
        }
        return tracks;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void closePfd() {
        if (currentPfd != null) {
            try {
                currentPfd.close();
            } catch (Exception ignored) {
            }
            currentPfd = null;
        }
    }

    private boolean canPostNotifications() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private static int immutableFlag() {
        return PendingIntent.FLAG_IMMUTABLE;
    }

    private final class SessionCallback extends MediaSession.Callback {
        @Override
        public void onPlay() {
            togglePlayPause();
        }

        @Override
        public void onPause() {
            togglePlayPause();
        }

        @Override
        public void onStop() {
            stopPlayback();
        }

        @Override
        public void onSkipToNext() {
            playNextInternal(false);
        }

        @Override
        public void onSkipToPrevious() {
            playPrevious();
        }

        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            if (MEDIA_ID_WAVE.equals(mediaId)) {
                playMyWave();
            } else if (MEDIA_ID_LIKED_CACHE.equals(mediaId)) {
                playLikedCache();
            }
        }
    }
}
