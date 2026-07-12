package dev.petrov.yaplay;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.petrov.yaplay.player.YmpArtworkCache;
import dev.petrov.yaplay.player.YmpPlaybackService;
import dev.petrov.yaplay.player.YmpRepository;
import dev.petrov.yaplay.player.YmpSettings;
import dev.petrov.yaplay.ymusic.ClipWaveClient;
import dev.petrov.yaplay.ymusic.TokenStore;
import dev.petrov.yaplay.ymusic.YandexMusicClient;

/** Full-screen player for the Android TV Yandex Music video-clip rotor. */
public final class ClipWaveActivity extends Activity {
    private static final int COLOR_BG = 0xff05070a;
    private static final int COLOR_PANEL = 0xe611171d;
    private static final int COLOR_SURFACE = 0xff202a34;
    private static final int COLOR_TEXT = 0xfff7fafc;
    private static final int COLOR_MUTED = 0xffa8b6c0;
    private static final int COLOR_ACCENT = 0xffffd21f;
    private static final int COLOR_LIKE = 0xff35d6a5;
    private static final String MEDIA_ACTION_LIKE = "dev.petrov.yaplay.action.LIKE_CLIP";
    private static final long OVERLAY_HIDE_DELAY_MS = 4_500L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService networkExecutor = Executors.newFixedThreadPool(3);
    private final Map<String, ClipWaveClient.StreamInfo> resolvedStreams = new ConcurrentHashMap<>();
    private final Map<String, YandexMusicClient.Track> clipTracks = new ConcurrentHashMap<>();
    private final Set<String> likedTrackKeys = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> seenClipIds = Collections.synchronizedSet(new HashSet<>());
    private final Deque<HistoryEntry> history = new ArrayDeque<>();

    private ExoPlayer player;
    private PlayerView playerView;
    private MediaSession mediaSession;
    private ClipWaveClient clipClient;
    private YmpRepository repository;
    private String accessToken = "";
    private String sessionId = "";
    private String currentClipSessionId = "";
    private String nextClipSessionId = "";
    private ClipWaveClient.Clip currentClip;
    private ClipWaveClient.Clip nextClip;
    private YandexMusicClient.Track currentTrack;
    private boolean currentLiked;
    private boolean likedKeysLoaded;
    private boolean nextLoading;
    private boolean advanceWhenReady;
    private boolean advanceAsFinished;
    private boolean destroyed;
    private boolean recoveringFromError;
    private int playbackGeneration;
    private String startedFeedbackClipId = "";

    private FrameLayout overlayView;
    private LinearLayout loadingPanel;
    private TextView loadingTextView;
    private TextView titleView;
    private TextView artistView;
    private TextView statusView;
    private ImageButton previousButton;
    private ImageButton playPauseButton;
    private ImageButton nextButton;
    private ImageButton likeButton;

    private final Runnable hideOverlayRunnable = () -> {
        if (overlayView != null && player != null && player.isPlaying()) {
            overlayView.setVisibility(View.GONE);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        accessToken = TokenStore.getAccessToken(this).trim();
        clipClient = new ClipWaveClient(accessToken);
        repository = new YmpRepository(this);
        initializePlayer();
        initializeMediaSession();
        rebuildContent();
        enterImmersiveMode();

        Diagnostics.log(this, "YMP Clip Wave opened");
        stopAudioPlayer();
        if (accessToken.isEmpty()) {
            showFatalError(getString(R.string.clip_wave_login_required));
            return;
        }
        loadLikedKeys();
        startClipWave();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        rebuildContent();
        enterImmersiveMode();
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersiveMode();
        updateControls();
    }

    @Override
    protected void onStop() {
        if (!isChangingConfigurations() && player != null && player.isPlaying()) {
            player.pause();
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        mainHandler.removeCallbacksAndMessages(null);
        if (playerView != null) {
            playerView.setPlayer(null);
        }
        if (player != null) {
            player.release();
            player = null;
        }
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
        }
        networkExecutor.shutdownNow();
        Diagnostics.log(this, "YMP Clip Wave closed");
        super.onDestroy();
    }

    private void initializePlayer() {
        player = new ExoPlayer.Builder(this).build();
        player.setAudioAttributes(AudioAttributes.DEFAULT, true);
        player.setHandleAudioBecomingNoisy(true);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_BUFFERING) {
                    showLoading(getString(R.string.clip_wave_buffering));
                } else if (playbackState == Player.STATE_READY) {
                    hideLoading();
                    recoveringFromError = false;
                    sendStartedFeedbackOnce();
                    scheduleOverlayHide();
                } else if (playbackState == Player.STATE_ENDED) {
                    requestAdvance(true);
                }
                updateControls();
                updatePlaybackState();
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (isPlaying) {
                    hideLoading();
                    scheduleOverlayHide();
                } else {
                    showOverlay(false);
                }
                updateControls();
                updatePlaybackState();
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                Diagnostics.log(ClipWaveActivity.this, "YMP Clip Wave playback failed", error);
                showStatus(getString(R.string.clip_wave_playback_failed, readableError(error)));
                showOverlay(false);
                if (!recoveringFromError) {
                    recoveringFromError = true;
                    mainHandler.postDelayed(() -> requestAdvance(false), 1_200L);
                }
                updatePlaybackState();
            }
        });
    }

    private void initializeMediaSession() {
        mediaSession = new MediaSession(this, "YMPlayer Clip Wave");
        Intent activityIntent = new Intent(this, ClipWaveActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                220,
                activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        mediaSession.setSessionActivity(pendingIntent);
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                if (player != null) {
                    player.play();
                }
            }

            @Override
            public void onPause() {
                if (player != null) {
                    player.pause();
                }
            }

            @Override
            public void onStop() {
                finish();
            }

            @Override
            public void onSkipToNext() {
                requestAdvance(false);
            }

            @Override
            public void onSkipToPrevious() {
                playPreviousClip();
            }

            @Override
            public void onSeekTo(long pos) {
                if (player != null) {
                    player.seekTo(Math.max(0L, pos));
                }
            }

            @Override
            public void onCustomAction(String action, Bundle extras) {
                if (MEDIA_ACTION_LIKE.equals(action)) {
                    toggleCurrentLike();
                }
            }
        });
        mediaSession.setActive(true);
        updateMediaMetadata(null);
        updatePlaybackState();
    }

    private void rebuildContent() {
        if (playerView != null) {
            playerView.setPlayer(null);
        }
        setContentView(buildContent());
        playerView.setPlayer(player);
        updateClipText();
        updateControls();
        if (currentClip == null) {
            showLoading(getString(R.string.clip_wave_loading));
        } else if (player != null && player.getPlaybackState() == Player.STATE_BUFFERING) {
            showLoading(getString(R.string.clip_wave_buffering));
        } else if (advanceWhenReady && nextClip == null) {
            showLoading(getString(R.string.clip_wave_loading_next));
        } else if (player != null && player.getPlaybackState() == Player.STATE_READY) {
            hideLoading();
        } else {
            hideLoading();
        }
    }

    private View buildContent() {
        boolean wide = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(COLOR_BG);

        playerView = new PlayerView(this);
        playerView.setUseController(false);
        playerView.setBackgroundColor(Color.BLACK);
        playerView.setOnClickListener(v -> toggleOverlay());
        root.addView(playerView, matchFrame());

        loadingPanel = new LinearLayout(this);
        loadingPanel.setOrientation(LinearLayout.VERTICAL);
        loadingPanel.setGravity(Gravity.CENTER);
        loadingPanel.setPadding(dp(22), dp(18), dp(22), dp(18));
        loadingPanel.setBackground(roundBackground(0xd9141b22, dp(10), 0xff344453));
        ProgressBar progress = new ProgressBar(this);
        loadingPanel.addView(progress, new LinearLayout.LayoutParams(dp(48), dp(48)));
        loadingTextView = new TextView(this);
        loadingTextView.setTextColor(COLOR_TEXT);
        loadingTextView.setTextSize(15);
        loadingTextView.setGravity(Gravity.CENTER);
        loadingTextView.setMaxLines(3);
        LinearLayout.LayoutParams loadingTextParams = wrapWrap();
        loadingTextParams.setMargins(0, dp(12), 0, 0);
        loadingPanel.addView(loadingTextView, loadingTextParams);
        FrameLayout.LayoutParams loadingParams = new FrameLayout.LayoutParams(
                wide ? dp(390) : dp(300),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        root.addView(loadingPanel, loadingParams);

        overlayView = new FrameLayout(this);
        overlayView.setClickable(false);
        root.addView(overlayView, matchFrame());

        ImageButton close = roundIconButton(
                R.drawable.ic_player_close,
                dp(54),
                0xcc151b21,
                COLOR_TEXT,
                getString(R.string.clip_wave_close)
        );
        close.setOnClickListener(v -> finish());
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(dp(54), dp(54), Gravity.TOP | Gravity.END);
        closeParams.setMargins(dp(14), dp(14), dp(14), dp(14));
        overlayView.addView(close, closeParams);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(dp(wide ? 24 : 18), dp(16), dp(wide ? 24 : 18), dp(18));
        bottom.setBackground(roundBackground(COLOR_PANEL, dp(8), 0x00334453));
        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
        );
        bottomParams.setMargins(dp(wide ? 18 : 10), 0, dp(wide ? 18 : 10), dp(wide ? 16 : 10));
        overlayView.addView(bottom, bottomParams);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setGravity(Gravity.CENTER_VERTICAL);
        bottom.addView(info, wide
                ? new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                : matchWrap());

        TextView mode = new TextView(this);
        mode.setText(R.string.clip_wave_title);
        mode.setTextColor(COLOR_ACCENT);
        mode.setTextSize(12);
        mode.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        info.addView(mode, matchWrap());

        titleView = new TextView(this);
        titleView.setTextColor(COLOR_TEXT);
        titleView.setTextSize(wide ? 27 : 23);
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleView.setMaxLines(2);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(titleView, matchWrap());

        artistView = new TextView(this);
        artistView.setTextColor(COLOR_MUTED);
        artistView.setTextSize(wide ? 17 : 15);
        artistView.setSingleLine(true);
        artistView.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(artistView, matchWrap());

        statusView = new TextView(this);
        statusView.setTextColor(0xff8296a5);
        statusView.setTextSize(12);
        statusView.setSingleLine(true);
        statusView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.setMargins(0, dp(5), 0, 0);
        info.addView(statusView, statusParams);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams controlsParams = wrapWrap();
        controlsParams.setMargins(wide ? dp(20) : 0, wide ? 0 : dp(14), 0, 0);
        bottom.addView(controls, controlsParams);

        int side = wide ? dp(68) : dp(60);
        int play = wide ? dp(82) : dp(72);
        previousButton = roundIconButton(
                R.drawable.ic_player_previous, side, COLOR_SURFACE, COLOR_TEXT,
                getString(R.string.clip_wave_previous)
        );
        previousButton.setOnClickListener(v -> playPreviousClip());
        controls.addView(previousButton, controlParams(side));

        playPauseButton = roundIconButton(
                R.drawable.ic_player_play, play, COLOR_ACCENT, COLOR_BG,
                getString(R.string.play_pause)
        );
        playPauseButton.setOnClickListener(v -> togglePlayback());
        controls.addView(playPauseButton, controlParams(play));

        nextButton = roundIconButton(
                R.drawable.ic_player_next, side, COLOR_SURFACE, COLOR_TEXT,
                getString(R.string.clip_wave_next)
        );
        nextButton.setOnClickListener(v -> requestAdvance(false));
        controls.addView(nextButton, controlParams(side));

        likeButton = roundIconButton(
                R.drawable.ic_player_like, side, COLOR_SURFACE, COLOR_TEXT,
                getString(R.string.like_track)
        );
        likeButton.setOnClickListener(v -> toggleCurrentLike());
        controls.addView(likeButton, controlParams(side));

        root.setOnClickListener(v -> toggleOverlay());
        return root;
    }

    private void startClipWave() {
        showLoading(getString(R.string.clip_wave_loading));
        networkExecutor.execute(() -> {
            try {
                ClipWaveClient.ClipSession session = clipClient.startSession();
                List<ClipWaveClient.Clip> initial = uniqueClips(session.clips);
                if (initial.isEmpty()) {
                    throw new IllegalStateException("Clip Wave returned an empty queue");
                }

                ClipWaveClient.Clip resolvedFirst = null;
                ClipWaveClient.StreamInfo resolvedFirstStream = null;
                Exception lastStreamError = null;
                for (int i = 0; i < initial.size(); i++) {
                    ClipWaveClient.Clip candidate = initial.get(i);
                    try {
                        resolvedFirstStream = clipClient.resolveStream(candidate);
                        resolvedFirst = candidate;
                        break;
                    } catch (Exception ex) {
                        lastStreamError = ex;
                        Diagnostics.log(this, "YMP Clip Wave initial clip unavailable: " + candidate.clipId, ex);
                    }
                }
                if (resolvedFirst == null || resolvedFirstStream == null) {
                    throw lastStreamError == null
                            ? new IllegalStateException("Clip Wave returned no playable clip")
                            : lastStreamError;
                }
                final ClipWaveClient.Clip first = resolvedFirst;
                final ClipWaveClient.StreamInfo firstStream = resolvedFirstStream;
                resolvedStreams.put(first.clipId, firstStream);

                postToMain(() -> {
                    sessionId = session.sessionId;
                    seenClipIds.add(first.clipId);
                    nextClip = null;
                    nextClipSessionId = "";
                    nextLoading = true;
                    playClip(first, session.sessionId);
                    prefetchInitialNext(initial, first, session.sessionId);
                });

                try {
                    clipClient.sendQueueStarted(session.sessionId, first.batchId);
                } catch (Exception feedbackError) {
                    Diagnostics.log(this, "YMP Clip Wave queue feedback failed", feedbackError);
                }
                Diagnostics.log(this, "YMP Clip Wave session started: clips=" + initial.size()
                        + ", session=" + shortId(session.sessionId));
            } catch (Exception ex) {
                Diagnostics.log(this, "YMP Clip Wave start failed", ex);
                postToMain(() -> showFatalError(getString(
                        R.string.clip_wave_start_failed,
                        readableError(ex)
                )));
            }
        });
    }

    private void prefetchInitialNext(
            List<ClipWaveClient.Clip> initial,
            ClipWaveClient.Clip anchor,
            String sourceSessionId
    ) {
        networkExecutor.execute(() -> {
            ResolvedClip resolved = null;
            int startIndex = initial == null || anchor == null ? -1 : initial.indexOf(anchor);
            if (initial != null) {
                for (int i = Math.max(0, startIndex + 1); i < initial.size(); i++) {
                    ClipWaveClient.Clip candidate = initial.get(i);
                    if (candidate == null || candidate.clipId.equals(anchor.clipId) || isSeen(candidate.clipId)) {
                        continue;
                    }
                    try {
                        resolved = new ResolvedClip(candidate, clipClient.resolveStream(candidate));
                        break;
                    } catch (Exception ex) {
                        Diagnostics.log(this, "YMP Clip Wave initial next unavailable: " + candidate.clipId, ex);
                    }
                }
            }
            ResolvedClip loaded = resolved;
            postToMain(() -> {
                if (currentClip == null
                        || anchor == null
                        || !anchor.clipId.equals(currentClip.clipId)
                        || !sourceSessionId.equals(currentClipSessionId)
                        || nextClip != null) {
                    return;
                }
                nextLoading = false;
                if (loaded == null) {
                    loadNextClip();
                    return;
                }
                nextClip = loaded.clip;
                nextClipSessionId = sourceSessionId;
                seenClipIds.add(loaded.clip.clipId);
                resolvedStreams.put(loaded.clip.clipId, loaded.stream);
                showStatus(getString(R.string.clip_wave_next_ready, loaded.clip.title));
                updateControls();
                if (advanceWhenReady) {
                    boolean finished = advanceAsFinished;
                    advanceWhenReady = false;
                    advanceToNext(finished);
                }
            });
        });
    }

    private void playClip(ClipWaveClient.Clip clip, String clipSessionId) {
        if (clip == null || destroyed) {
            return;
        }
        playbackGeneration++;
        int generation = playbackGeneration;
        currentClipSessionId = clipSessionId == null ? "" : clipSessionId;
        if (!currentClipSessionId.isEmpty()) {
            sessionId = currentClipSessionId;
        }
        currentClip = clip;
        currentTrack = clipTracks.get(clip.clipId);
        currentLiked = currentTrack != null && likedTrackKeys.contains(currentTrack.key);
        startedFeedbackClipId = "";
        recoveringFromError = false;
        updateClipText();
        updateMediaMetadata(null);
        loadClipArtwork(clip);
        loadTrackForClip(clip, generation);
        updateControls();
        showOverlay(false);
        showLoading(getString(R.string.clip_wave_resolving));

        ClipWaveClient.StreamInfo stream = resolvedStreams.get(clip.clipId);
        if (stream != null) {
            prepareStream(clip, stream, generation);
        } else {
            networkExecutor.execute(() -> {
                try {
                    ClipWaveClient.StreamInfo resolved = clipClient.resolveStream(clip);
                    resolvedStreams.put(clip.clipId, resolved);
                    postToMain(() -> prepareStream(clip, resolved, generation));
                } catch (Exception ex) {
                    Diagnostics.log(this, "YMP Clip Wave stream resolve failed: " + clip.clipId, ex);
                    postToMain(() -> handleUnplayableClip(clip, generation, ex));
                }
            });
        }
    }

    private void prepareStream(
            ClipWaveClient.Clip clip,
            ClipWaveClient.StreamInfo stream,
            int generation
    ) {
        if (!isCurrent(clip, generation) || stream == null || stream.url.isEmpty()) {
            return;
        }
        MediaItem.Builder media = new MediaItem.Builder().setUri(stream.url);
        if (!stream.mimeType.isEmpty()) {
            media.setMimeType(stream.mimeType);
        }
        player.setMediaItem(media.build());
        player.prepare();
        player.play();
        showStatus(stream.preview
                ? getString(R.string.clip_wave_preview_fallback)
                : getString(R.string.clip_wave_next_preparing));
        if (nextClip == null) {
            loadNextClip();
        }
    }

    private void handleUnplayableClip(ClipWaveClient.Clip clip, int generation, Exception error) {
        if (!isCurrent(clip, generation)) {
            return;
        }
        showStatus(getString(R.string.clip_wave_stream_failed, readableError(error)));
        requestAdvance(false);
    }

    private void requestAdvance(boolean finished) {
        if (currentClip == null || destroyed) {
            return;
        }
        showOverlay(false);
        if (nextClip == null) {
            advanceWhenReady = true;
            advanceAsFinished = finished;
            showLoading(getString(R.string.clip_wave_loading_next));
            loadNextClip();
            return;
        }
        advanceToNext(finished);
    }

    private void advanceToNext(boolean finished) {
        ClipWaveClient.Clip old = currentClip;
        ClipWaveClient.Clip target = nextClip;
        if (old == null || target == null) {
            return;
        }
        float playedSeconds = currentPlayedSeconds();
        String oldSessionId = currentClipSessionId;
        String targetSessionId = nextClipSessionId.isEmpty() ? sessionId : nextClipSessionId;
        sendFinishedOrSkipped(old, oldSessionId, finished, playedSeconds);
        history.addLast(new HistoryEntry(old, oldSessionId));
        while (history.size() > 40) {
            history.removeFirst();
        }
        nextClip = null;
        nextClipSessionId = "";
        advanceWhenReady = false;
        playClip(target, targetSessionId);
        loadNextClip();
    }

    private void playPreviousClip() {
        if (history.isEmpty() || currentClip == null) {
            showStatus(getString(R.string.clip_wave_no_previous));
            showOverlay(false);
            return;
        }
        ClipWaveClient.Clip old = currentClip;
        String oldSessionId = currentClipSessionId;
        sendFinishedOrSkipped(old, oldSessionId, false, currentPlayedSeconds());
        HistoryEntry previous = history.removeLast();
        nextClip = old;
        nextClipSessionId = oldSessionId;
        advanceWhenReady = false;
        playClip(previous.clip, previous.sessionId);
        prefetchStream(old);
    }

    private void loadNextClip() {
        if (nextLoading || nextClip != null || sessionId.isEmpty() || currentClip == null || destroyed) {
            return;
        }
        nextLoading = true;
        String anchorId = currentClip.clipId;
        String sourceSessionId = currentClipSessionId.isEmpty() ? sessionId : currentClipSessionId;
        showStatus(getString(R.string.clip_wave_next_loading));
        networkExecutor.execute(() -> {
            try {
                LoadedNext loaded = requestNextPlayable(sourceSessionId, anchorId);
                postToMain(() -> {
                    nextLoading = false;
                    if (currentClip == null || !anchorId.equals(currentClip.clipId) || nextClip != null) {
                        return;
                    }
                    nextClip = loaded.clip;
                    nextClipSessionId = loaded.sessionId;
                    seenClipIds.add(loaded.clip.clipId);
                    resolvedStreams.put(loaded.clip.clipId, loaded.stream);
                    showStatus(getString(R.string.clip_wave_next_ready, loaded.clip.title));
                    updateControls();
                    if (advanceWhenReady) {
                        boolean finished = advanceAsFinished;
                        advanceWhenReady = false;
                        advanceToNext(finished);
                    }
                });
            } catch (Exception ex) {
                Diagnostics.log(this, "YMP Clip Wave next load failed", ex);
                postToMain(() -> {
                    nextLoading = false;
                    showStatus(getString(R.string.clip_wave_next_failed, readableError(ex)));
                    hideLoading();
                    showOverlay(false);
                });
            }
        });
    }

    private LoadedNext requestNextPlayable(String sourceSessionId, String anchorId) throws Exception {
        ClipWaveClient.ClipBatch batch = clipClient.loadNext(
                sourceSessionId,
                Collections.singletonList(anchorId)
        );
        String targetSessionId = sourceSessionId;
        boolean restarted = false;
        List<ClipWaveClient.Clip> candidates = uniqueClips(batch.clips);
        if (firstCandidate(candidates, anchorId) == null) {
            ClipWaveClient.ClipSession newSession = clipClient.startSession();
            targetSessionId = newSession.sessionId;
            candidates = uniqueClips(newSession.clips);
            restarted = true;
        }
        ClipWaveClient.Clip candidate = firstCandidate(candidates, anchorId);
        if (candidate == null) {
            throw new IllegalStateException("Clip Wave returned no next clip");
        }

        ClipWaveClient.StreamInfo stream = null;
        Exception lastError = null;
        for (ClipWaveClient.Clip clip : candidates) {
            if (clip == null || anchorId.equals(clip.clipId) || isSeen(clip.clipId)) {
                continue;
            }
            try {
                stream = clipClient.resolveStream(clip);
                candidate = clip;
                break;
            } catch (Exception ex) {
                lastError = ex;
            }
        }
        if (stream == null) {
            try {
                stream = clipClient.resolveStream(candidate);
            } catch (Exception ex) {
                lastError = ex;
            }
        }
        if (stream == null) {
            throw lastError == null ? new IOException("No playable next clip") : lastError;
        }
        if (restarted) {
            try {
                clipClient.sendQueueStarted(targetSessionId, candidate.batchId);
            } catch (Exception feedbackError) {
                Diagnostics.log(this, "YMP Clip Wave restarted queue feedback failed", feedbackError);
            }
        }
        return new LoadedNext(targetSessionId, candidate, stream);
    }

    private void prefetchStream(ClipWaveClient.Clip clip) {
        if (clip == null || resolvedStreams.containsKey(clip.clipId) || destroyed) {
            return;
        }
        networkExecutor.execute(() -> {
            try {
                resolvedStreams.put(clip.clipId, clipClient.resolveStream(clip));
                postToMain(() -> {
                    if (nextClip != null && clip.clipId.equals(nextClip.clipId)) {
                        showStatus(getString(R.string.clip_wave_next_ready, clip.title));
                    }
                });
            } catch (Exception ex) {
                Diagnostics.log(this, "YMP Clip Wave prefetch failed: " + clip.clipId, ex);
            }
        });
    }

    private void loadLikedKeys() {
        networkExecutor.execute(() -> {
            try {
                Set<String> keys = repository.likedTrackKeys();
                likedTrackKeys.clear();
                likedTrackKeys.addAll(keys);
                likedKeysLoaded = true;
                postToMain(() -> {
                    if (currentTrack != null) {
                        currentLiked = likedTrackKeys.contains(currentTrack.key);
                    }
                    updateControls();
                    updatePlaybackState();
                });
            } catch (Exception ex) {
                Diagnostics.log(this, "YMP Clip Wave liked state failed", ex);
                likedKeysLoaded = true;
                postToMain(this::updateControls);
            }
        });
    }

    private void loadTrackForClip(ClipWaveClient.Clip clip, int generation) {
        if (clip == null || clip.primaryTrackId().isEmpty()) {
            currentTrack = null;
            updateControls();
            return;
        }
        YandexMusicClient.Track cached = clipTracks.get(clip.clipId);
        if (cached != null) {
            currentTrack = cached;
            currentLiked = likedTrackKeys.contains(cached.key);
            updateControls();
            return;
        }
        networkExecutor.execute(() -> {
            try {
                List<YandexMusicClient.Track> tracks = new YandexMusicClient(accessToken)
                        .getTracks(Collections.singletonList(clip.primaryTrackId()));
                YandexMusicClient.Track track = tracks.isEmpty() ? null : tracks.get(0);
                if (track != null) {
                    clipTracks.put(clip.clipId, track);
                }
                postToMain(() -> {
                    if (!isCurrent(clip, generation)) {
                        return;
                    }
                    currentTrack = track;
                    currentLiked = track != null && likedTrackKeys.contains(track.key);
                    updateControls();
                    updatePlaybackState();
                });
            } catch (Exception ex) {
                Diagnostics.log(this, "YMP Clip Wave track mapping failed: " + clip.clipId, ex);
                postToMain(this::updateControls);
            }
        });
    }

    private void toggleCurrentLike() {
        ClipWaveClient.Clip clip = currentClip;
        YandexMusicClient.Track track = currentTrack;
        if (clip == null || track == null || !likedKeysLoaded) {
            showStatus(getString(R.string.clip_wave_like_wait));
            showOverlay(false);
            return;
        }
        boolean remove = likedTrackKeys.contains(track.key);
        likeButton.setEnabled(false);
        showStatus(getString(remove ? R.string.clip_wave_unliking : R.string.clip_wave_liking));
        networkExecutor.execute(() -> {
            try {
                if (remove) {
                    repository.removeLike(track);
                    likedTrackKeys.remove(track.key);
                } else {
                    repository.like(track, YmpSettings.isAutoCacheLikedEnabled(this));
                    likedTrackKeys.add(track.key);
                }
                postToMain(() -> {
                    if (currentClip != null && clip.clipId.equals(currentClip.clipId)) {
                        currentLiked = !remove;
                        showStatus(getString(remove
                                ? R.string.clip_wave_like_removed
                                : R.string.clip_wave_liked));
                    }
                    updateControls();
                    updatePlaybackState();
                });
            } catch (Exception ex) {
                Diagnostics.log(this, "YMP Clip Wave like failed: " + track.key, ex);
                postToMain(() -> {
                    showStatus(getString(R.string.clip_wave_like_failed, readableError(ex)));
                    updateControls();
                });
            }
        });
    }

    private void sendStartedFeedbackOnce() {
        ClipWaveClient.Clip clip = currentClip;
        String sourceSessionId = currentClipSessionId;
        if (clip == null || sourceSessionId.isEmpty() || clip.clipId.equals(startedFeedbackClipId)) {
            return;
        }
        startedFeedbackClipId = clip.clipId;
        networkExecutor.execute(() -> {
            try {
                clipClient.sendClipStarted(sourceSessionId, clip);
            } catch (Exception ex) {
                Diagnostics.log(this, "YMP Clip Wave started feedback failed", ex);
            }
        });
    }

    private void sendFinishedOrSkipped(
            ClipWaveClient.Clip clip,
            String sourceSessionId,
            boolean finished,
            float seconds
    ) {
        if (clip == null || sourceSessionId.isEmpty() || destroyed) {
            return;
        }
        networkExecutor.execute(() -> {
            try {
                if (finished) {
                    clipClient.sendClipFinished(sourceSessionId, clip, seconds);
                } else {
                    clipClient.sendClipSkipped(sourceSessionId, clip, seconds);
                }
            } catch (Exception ex) {
                Diagnostics.log(this, "YMP Clip Wave completion feedback failed", ex);
            }
        });
    }

    private void loadClipArtwork(ClipWaveClient.Clip clip) {
        if (clip == null || clip.thumbnailUrl.isEmpty()) {
            return;
        }
        networkExecutor.execute(() -> {
            try {
                Bitmap artwork = YmpArtworkCache.loadRemoteBitmap(this, clip.thumbnailUrl);
                if (artwork != null) {
                    postToMain(() -> {
                        if (currentClip != null && clip.clipId.equals(currentClip.clipId)) {
                            updateMediaMetadata(artwork);
                        }
                    });
                }
            } catch (Exception ex) {
                Diagnostics.log(this, "YMP Clip Wave artwork failed: " + clip.clipId, ex);
            }
        });
    }

    private void updateMediaMetadata(Bitmap artwork) {
        if (mediaSession == null) {
            return;
        }
        ClipWaveClient.Clip clip = currentClip;
        Bitmap safeArtwork = artwork;
        if (safeArtwork == null) {
            safeArtwork = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
        }
        MediaMetadata.Builder metadata = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, clip == null ? "clip_wave" : clip.clipId)
                .putString(MediaMetadata.METADATA_KEY_TITLE, clip == null
                        ? getString(R.string.clip_wave_title)
                        : clip.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, clip == null ? "YMPlayer" : clip.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, getString(R.string.clip_wave_title));
        if (clip != null) {
            metadata.putLong(MediaMetadata.METADATA_KEY_DURATION, normalizedDurationMs(clip.duration));
            if (!clip.thumbnailUrl.isEmpty()) {
                metadata.putString(MediaMetadata.METADATA_KEY_ART_URI, clip.thumbnailUrl);
                metadata.putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, clip.thumbnailUrl);
                metadata.putString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI, clip.thumbnailUrl);
            }
        }
        if (safeArtwork != null) {
            metadata.putBitmap(MediaMetadata.METADATA_KEY_ART, safeArtwork);
            metadata.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, safeArtwork);
            metadata.putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, safeArtwork);
        }
        mediaSession.setMetadata(metadata.build());
    }

    private void updatePlaybackState() {
        if (mediaSession == null) {
            return;
        }
        long actions = PlaybackState.ACTION_PLAY
                | PlaybackState.ACTION_PAUSE
                | PlaybackState.ACTION_PLAY_PAUSE
                | PlaybackState.ACTION_STOP
                | PlaybackState.ACTION_SKIP_TO_NEXT
                | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                | PlaybackState.ACTION_SEEK_TO;
        int state = PlaybackState.STATE_STOPPED;
        float speed = 0f;
        long position = 0L;
        if (player != null) {
            position = Math.max(0L, player.getCurrentPosition());
            if (player.getPlaybackState() == Player.STATE_BUFFERING) {
                state = PlaybackState.STATE_BUFFERING;
            } else if (player.isPlaying()) {
                state = PlaybackState.STATE_PLAYING;
                speed = 1f;
            } else if (currentClip != null) {
                state = PlaybackState.STATE_PAUSED;
            }
        }
        PlaybackState.Builder builder = new PlaybackState.Builder()
                .setActions(actions)
                .setState(state, position, speed, SystemClock.elapsedRealtime());
        if (currentTrack != null && likedKeysLoaded) {
            builder.addCustomAction(
                    MEDIA_ACTION_LIKE,
                    getString(currentLiked ? R.string.unlike_track : R.string.like_track),
                    R.drawable.ic_player_like
            );
        }
        mediaSession.setPlaybackState(builder.build());
    }

    private void updateClipText() {
        if (titleView == null || artistView == null || statusView == null) {
            return;
        }
        if (currentClip == null) {
            titleView.setText(R.string.clip_wave_loading);
            artistView.setText("");
            statusView.setText(R.string.clip_wave_dynamic_hint);
            return;
        }
        titleView.setText(currentClip.title);
        artistView.setText(currentClip.artist.isEmpty()
                ? getString(R.string.clip_wave_unknown_artist)
                : currentClip.artist);
        if (nextClip == null) {
            statusView.setText(nextLoading
                    ? R.string.clip_wave_next_loading
                    : R.string.clip_wave_dynamic_hint);
        } else {
            statusView.setText(getString(R.string.clip_wave_next_ready, nextClip.title));
        }
    }

    private void updateControls() {
        if (playPauseButton == null) {
            return;
        }
        boolean playing = player != null && player.isPlaying();
        playPauseButton.setImageResource(playing ? R.drawable.ic_player_pause : R.drawable.ic_player_play);
        playPauseButton.setContentDescription(getString(R.string.play_pause));
        previousButton.setEnabled(!history.isEmpty());
        previousButton.setAlpha(history.isEmpty() ? 0.35f : 1f);
        nextButton.setEnabled(currentClip != null);
        nextButton.setAlpha(currentClip == null ? 0.35f : 1f);

        boolean canLike = currentTrack != null && likedKeysLoaded;
        likeButton.setEnabled(canLike);
        likeButton.setAlpha(canLike ? 1f : 0.4f);
        likeButton.setBackground(roundBackground(
                currentLiked ? COLOR_LIKE : COLOR_SURFACE,
                dp(999),
                0x00000000
        ));
        likeButton.setColorFilter(currentLiked ? COLOR_BG : COLOR_TEXT);
        likeButton.setContentDescription(getString(currentLiked ? R.string.unlike_track : R.string.like_track));
        updateClipText();
    }

    private void togglePlayback() {
        showOverlay(false);
        if (player == null) {
            return;
        }
        if (player.isPlaying()) {
            player.pause();
        } else if (currentClip != null) {
            player.play();
        }
    }

    private void toggleOverlay() {
        if (overlayView == null) {
            return;
        }
        if (overlayView.getVisibility() == View.VISIBLE) {
            mainHandler.removeCallbacks(hideOverlayRunnable);
            overlayView.setVisibility(View.GONE);
        } else {
            showOverlay(true);
        }
    }

    private void showOverlay(boolean scheduleHide) {
        if (overlayView == null) {
            return;
        }
        overlayView.setVisibility(View.VISIBLE);
        mainHandler.removeCallbacks(hideOverlayRunnable);
        if (scheduleHide && player != null && player.isPlaying()) {
            mainHandler.postDelayed(hideOverlayRunnable, OVERLAY_HIDE_DELAY_MS);
        }
    }

    private void scheduleOverlayHide() {
        showOverlay(true);
    }

    private void showLoading(String message) {
        if (loadingPanel != null) {
            loadingPanel.setVisibility(View.VISIBLE);
        }
        if (loadingTextView != null) {
            loadingTextView.setText(message == null ? "" : message);
        }
        showOverlay(false);
    }

    private void hideLoading() {
        if (loadingPanel != null) {
            loadingPanel.setVisibility(View.GONE);
        }
    }

    private void showStatus(String message) {
        if (statusView != null) {
            statusView.setText(message == null ? "" : message);
        }
    }

    private void showFatalError(String message) {
        showLoading(message);
        showOverlay(false);
        if (playPauseButton != null) {
            playPauseButton.setEnabled(false);
        }
        if (nextButton != null) {
            nextButton.setEnabled(false);
        }
        Diagnostics.log(this, "YMP Clip Wave fatal: " + message);
    }

    private void stopAudioPlayer() {
        Intent stop = new Intent(this, YmpPlaybackService.class)
                .setAction(YmpPlaybackService.ACTION_STOP);
        startService(stop);
    }

    @SuppressWarnings("deprecation")
    private void enterImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private List<ClipWaveClient.Clip> uniqueClips(List<ClipWaveClient.Clip> source) {
        List<ClipWaveClient.Clip> result = new ArrayList<>();
        Set<String> local = new HashSet<>();
        if (source != null) {
            for (ClipWaveClient.Clip clip : source) {
                if (clip != null && !clip.clipId.isEmpty() && local.add(clip.clipId)) {
                    result.add(clip);
                }
            }
        }
        return result;
    }

    private ClipWaveClient.Clip firstCandidate(List<ClipWaveClient.Clip> source, String anchorId) {
        ClipWaveClient.Clip repeated = null;
        if (source == null) {
            return null;
        }
        for (ClipWaveClient.Clip clip : source) {
            if (clip == null || clip.clipId.isEmpty() || clip.clipId.equals(anchorId)) {
                continue;
            }
            if (!isSeen(clip.clipId)) {
                return clip;
            }
            if (repeated == null) {
                repeated = clip;
            }
        }
        return repeated;
    }

    private boolean isSeen(String clipId) {
        return clipId != null && seenClipIds.contains(clipId);
    }

    private boolean isCurrent(ClipWaveClient.Clip clip, int generation) {
        return !destroyed
                && clip != null
                && generation == playbackGeneration
                && currentClip != null
                && clip.clipId.equals(currentClip.clipId);
    }

    private void postToMain(Runnable action) {
        mainHandler.post(() -> {
            if (!destroyed && !isFinishing()) {
                action.run();
            }
        });
    }

    private float currentPlayedSeconds() {
        return player == null ? 0f : Math.max(0f, player.getCurrentPosition() / 1000f);
    }

    private long normalizedDurationMs(long duration) {
        if (duration <= 0L) {
            return 0L;
        }
        return duration < 10_000L ? duration * 1000L : duration;
    }

    private String readableError(Throwable error) {
        if (error == null) {
            return getString(R.string.clip_wave_unknown_error);
        }
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return error.getClass().getSimpleName();
        }
        message = message.replace('\n', ' ').replace('\r', ' ').trim();
        return message.length() > 220 ? message.substring(0, 220) : message;
    }

    private static String shortId(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > 12 ? value.substring(0, 12) + "..." : value;
    }

    private ImageButton roundIconButton(int icon, int size, int background, int tint, String description) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(icon);
        button.setColorFilter(tint);
        button.setBackground(roundBackground(background, dp(999), 0x00000000));
        button.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        button.setPadding(dp(14), dp(14), dp(14), dp(14));
        button.setContentDescription(description);
        button.setMinimumWidth(size);
        button.setMinimumHeight(size);
        return button;
    }

    private GradientDrawable roundBackground(int color, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (Color.alpha(stroke) > 0) {
            drawable.setStroke(dp(1), stroke);
        }
        return drawable;
    }

    private LinearLayout.LayoutParams controlParams(int size) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        params.setMargins(dp(5), 0, dp(5), 0);
        return params;
    }

    private FrameLayout.LayoutParams matchFrame() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class LoadedNext {
        final String sessionId;
        final ClipWaveClient.Clip clip;
        final ClipWaveClient.StreamInfo stream;

        LoadedNext(String sessionId, ClipWaveClient.Clip clip, ClipWaveClient.StreamInfo stream) {
            this.sessionId = sessionId;
            this.clip = clip;
            this.stream = stream;
        }
    }

    private static final class ResolvedClip {
        final ClipWaveClient.Clip clip;
        final ClipWaveClient.StreamInfo stream;

        ResolvedClip(ClipWaveClient.Clip clip, ClipWaveClient.StreamInfo stream) {
            this.clip = clip;
            this.stream = stream;
        }
    }

    private static final class HistoryEntry {
        final ClipWaveClient.Clip clip;
        final String sessionId;

        HistoryEntry(ClipWaveClient.Clip clip, String sessionId) {
            this.clip = clip;
            this.sessionId = sessionId == null ? "" : sessionId;
        }
    }
}
