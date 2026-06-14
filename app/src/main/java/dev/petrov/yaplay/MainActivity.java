package dev.petrov.yaplay;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.audiofx.AudioEffect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.Selection;
import android.text.TextUtils;
import android.text.method.PasswordTransformationMethod;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import dev.petrov.yaplay.player.SideBarHelper;
import dev.petrov.yaplay.player.Ts18AudioControls;
import dev.petrov.yaplay.player.EmbeddedSideBarService;
import dev.petrov.yaplay.player.LocalArtworkEnricher;
import dev.petrov.yaplay.player.LocalPlaylistStore;
import dev.petrov.yaplay.player.YmpPlaybackService;
import dev.petrov.yaplay.player.YmpRepository;
import dev.petrov.yaplay.player.YmpSettings;
import dev.petrov.yaplay.ymusic.TokenStore;
import dev.petrov.yaplay.ymusic.YandexMusicClient;

public class MainActivity extends Activity {
    private static final int COLOR_BG = 0xff070b10;
    private static final int COLOR_SURFACE = 0xff111820;
    private static final int COLOR_SURFACE_2 = 0xff17212b;
    private static final int COLOR_STROKE = 0xff263747;
    private static final int COLOR_TEXT = 0xfff4f8fb;
    private static final int COLOR_MUTED = 0xff9fb0bd;
    private static final int COLOR_ACCENT = 0xffffd21f;
    private static final int COLOR_ACCENT_2 = 0xff32d6c2;
    private static final int COLOR_DANGER = 0xffe84a5f;
    private static final int REQUEST_LOCAL_FILES = 6101;
    private static final int REQUEST_LOCAL_FOLDER = 6102;

    private TextView statusView;
    private ImageView coverView;
    private TextView nowTitleView;
    private TextView nowArtistView;
    private TextView nowAlbumView;
    private TextView queueView;
    private TextView modeView;
    private ScrollView playerPageView;
    private ScrollView libraryPageView;
    private LinearLayout libraryPlaylistsView;
    private LinearLayout localPlaylistsView;
    private TextView libraryStatusView;
    private Button waveSourceButton;
    private Button offlineSourceButton;
    private Button playlistSourceButton;
    private ImageButton playPauseButton;
    private ImageButton queueModeButton;
    private ImageButton equalizerButton;
    private ImageButton likeButton;
    private ImageButton dislikeButton;
    private ImageButton addToPlaylistButton;
    private ImageButton sidebarToggleButton;
    private TextView loginCodeView;
    private EditText tokenEdit;
    private CheckBox wifiOnlyBox;
    private CheckBox chargingOnlyBox;
    private CheckBox showTokenBox;
    private CheckBox sidebarWatchdogBox;
    private CheckBox autoCacheLikedBox;
    private EditText equalizerPackageEdit;
    private BroadcastReceiver cacheStatusReceiver;
    private BroadcastReceiver playerStatusReceiver;
    private volatile boolean polling;
    private volatile boolean libraryLoading;
    private boolean libraryLoaded;
    private final List<YandexMusicClient.PlaylistSummary> cachedPlaylists = new ArrayList<>();
    private final List<LocalPlaylistStore.LocalPlaylist> cachedLocalPlaylists = new ArrayList<>();
    private volatile String cachedCacheStatus = "Cache status loading...";
    private volatile boolean cacheStatusLoading;
    private String latestDeviceCode = "";
    private String latestCoverUrl = "";
    private int selectedSourceType = YmpPlaybackService.SOURCE_WAVE;
    private int selectedPlaylistKind = -1;
    private String selectedPlaylistTitle = "";
    private String selectedLocalPlaylistId = "";
    private String selectedLocalPlaylistTitle = "";
    private String pendingLocalPlaylistId = "";
    private boolean selectedWaveMode = true;
    private boolean currentWaveMode = true;
    private int currentSourceType = YmpPlaybackService.SOURCE_WAVE;
    private boolean currentPlaying;
    private boolean currentPrepared;
    private boolean currentLiked;
    private int currentPlayMode;
    private int currentAudioSessionId;
    private float swipeStartX;
    private float swipeStartY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildContent());
        requestNotificationPermissionIfNeeded();
        Diagnostics.log(this, "YMPlayer MainActivity opened");
        updateStatus(statusWithCache("Ready"));
    }

    @Override
    @SuppressLint({"InlinedApi", "UnspecifiedRegisterReceiverFlag"})
    protected void onStart() {
        super.onStart();
        cacheStatusReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateStatus(statusWithCache(intent.getStringExtra(CacheSyncService.EXTRA_STATUS)));
            }
        };
        playerStatusReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updatePlayerStatus(intent);
            }
        };

        IntentFilter cacheFilter = new IntentFilter(CacheSyncService.ACTION_STATUS);
        IntentFilter playerFilter = new IntentFilter(YmpPlaybackService.ACTION_STATUS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(cacheStatusReceiver, cacheFilter, null, null, Context.RECEIVER_NOT_EXPORTED);
            registerReceiver(playerStatusReceiver, playerFilter, null, null, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(cacheStatusReceiver, cacheFilter);
            registerReceiver(playerStatusReceiver, playerFilter);
        }
        if (CacheSyncService.isRunning()) {
            updateStatus(statusWithCache(CacheSyncService.lastStatus()));
        }
        restoreLastPlayerStatus();
        ensureEmbeddedSideBar(false);
    }

    @Override
    protected void onStop() {
        if (cacheStatusReceiver != null) {
            unregisterReceiver(cacheStatusReceiver);
            cacheStatusReceiver = null;
        }
        if (playerStatusReceiver != null) {
            unregisterReceiver(playerStatusReceiver);
            playerStatusReceiver = null;
        }
        super.onStop();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            return;
        }
        if (requestCode == REQUEST_LOCAL_FILES) {
            handleLocalFilesResult(data);
        } else if (requestCode == REQUEST_LOCAL_FOLDER) {
            handleLocalFolderResult(data);
        }
    }

    private View buildContent() {
        boolean wide = isWideLayout();
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(COLOR_BG);

        playerPageView = new ScrollView(this);
        playerPageView.setFillViewport(true);
        playerPageView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        attachSwipeNavigation(playerPageView);
        page.addView(playerPageView, matchFrame());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(wide ? 22 : 16), dp(wide ? 14 : 16), dp(wide ? 22 : 16), dp(16));
        playerPageView.addView(root, matchScroll());

        addTopBar(root);

        LinearLayout playerSurface = new LinearLayout(this);
        playerSurface.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        playerSurface.setGravity(Gravity.CENTER_VERTICAL);
        playerSurface.setPadding(dp(wide ? 18 : 14), dp(wide ? 14 : 16), dp(wide ? 18 : 14), dp(wide ? 14 : 18));
        playerSurface.setBackground(panelBg(COLOR_SURFACE, dp(18), COLOR_STROKE));
        LinearLayout.LayoutParams playerParams = matchWrap();
        playerParams.setMargins(0, dp(8), 0, dp(12));
        root.addView(playerSurface, playerParams);

        addCoverPanel(playerSurface, wide);
        addPlayerInfoPanel(playerSurface, wide);

        statusView = new TextView(this);
        statusView.setTextColor(COLOR_TEXT);
        statusView.setTextSize(wide ? 12 : 13);
        statusView.setMaxLines(wide ? 3 : 5);
        statusView.setEllipsize(TextUtils.TruncateAt.END);
        statusView.setTextIsSelectable(true);
        statusView.setPadding(dp(14), dp(10), dp(14), dp(10));
        statusView.setBackground(panelBg(0xff0d141b, dp(12), 0xff1d2b36));
        root.addView(statusView, matchWrap());

        libraryPageView = buildLibraryPage(wide);
        libraryPageView.setVisibility(View.GONE);
        attachSwipeNavigation(libraryPageView);
        page.addView(libraryPageView, matchFrame());

        return page;
    }

    private ScrollView buildLibraryPage(boolean wide) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(wide ? 22 : 16), dp(wide ? 14 : 16), dp(wide ? 22 : 16), dp(16));
        scroll.addView(root, matchScroll());

        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(top, matchWrap());

        TextView title = new TextView(this);
        title.setText(R.string.library_title);
        title.setTextColor(COLOR_TEXT);
        title.setTextSize(isWideLayout() ? 28 : 30);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        top.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button player = smallButton(getString(R.string.player_page), COLOR_SURFACE_2, COLOR_TEXT);
        player.setOnClickListener(v -> showPlayerPage());
        top.addView(player, compactButtonParams(dp(118)));

        libraryStatusView = new TextView(this);
        libraryStatusView.setText(R.string.library_status_ready);
        libraryStatusView.setTextColor(COLOR_TEXT);
        libraryStatusView.setTextSize(13);
        libraryStatusView.setPadding(dp(14), dp(10), dp(14), dp(10));
        libraryStatusView.setBackground(panelBg(0xff0d141b, dp(12), 0xff1d2b36));
        root.addView(libraryStatusView, matchWrap());

        Button search = controlButton(getString(R.string.search_music), COLOR_ACCENT, COLOR_BG, 52);
        search.setOnClickListener(v -> showSearchEntry());
        LinearLayout.LayoutParams searchParams = matchWrap();
        searchParams.setMargins(0, dp(10), 0, dp(4));
        root.addView(search, searchParams);

        addSection(root, R.string.section_my_playlists);
        libraryPlaylistsView = new LinearLayout(this);
        libraryPlaylistsView.setOrientation(LinearLayout.VERTICAL);
        root.addView(libraryPlaylistsView, matchWrap());
        addLibraryStatusRow(libraryPlaylistsView, getString(R.string.library_playlists_not_loaded));

        addSection(root, R.string.section_local_playlists);
        localPlaylistsView = new LinearLayout(this);
        localPlaylistsView.setOrientation(LinearLayout.VERTICAL);
        root.addView(localPlaylistsView, matchWrap());
        loadLocalPlaylists();

        return scroll;
    }

    private void addLibraryAction(LinearLayout root, String title, String subtitle, Runnable action) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(dp(14), dp(11), dp(14), dp(11));
        item.setBackground(panelBg(COLOR_SURFACE_2, dp(12), COLOR_STROKE));
        item.setClickable(true);
        item.setOnClickListener(v -> action.run());

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(COLOR_TEXT);
        titleView.setTextSize(16);
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        item.addView(titleView, matchWrap());

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView subtitleView = new TextView(this);
            subtitleView.setText(subtitle);
            subtitleView.setTextColor(COLOR_MUTED);
            subtitleView.setTextSize(12);
            subtitleView.setSingleLine(true);
            subtitleView.setEllipsize(TextUtils.TruncateAt.END);
            item.addView(subtitleView, matchWrap());
        }
        root.addView(item, spaced());
    }

    private void addLibraryActionButtons(
            LinearLayout root,
            String title,
            String subtitle,
            Runnable primaryAction,
            String secondaryLabel,
            Runnable secondaryAction
    ) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(dp(14), dp(11), dp(14), dp(11));
        item.setBackground(panelBg(COLOR_SURFACE_2, dp(12), COLOR_STROKE));

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(COLOR_TEXT);
        titleView.setTextSize(16);
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        item.addView(titleView, matchWrap());

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView subtitleView = new TextView(this);
            subtitleView.setText(subtitle);
            subtitleView.setTextColor(COLOR_MUTED);
            subtitleView.setTextSize(12);
            subtitleView.setSingleLine(true);
            subtitleView.setEllipsize(TextUtils.TruncateAt.END);
            item.addView(subtitleView, matchWrap());
        }

        LinearLayout actions = row();
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, dp(10), 0, 0);
        item.addView(actions, params);

        Button select = smallButton(getString(R.string.select_playlist), COLOR_ACCENT, COLOR_BG);
        select.setOnClickListener(v -> primaryAction.run());
        actions.addView(select, rowButtonParams(1f));

        if (secondaryAction != null && secondaryLabel != null && !secondaryLabel.isEmpty()) {
            Button secondary = smallButton(secondaryLabel, COLOR_DANGER, COLOR_TEXT);
            secondary.setOnClickListener(v -> secondaryAction.run());
            actions.addView(secondary, rowButtonParams(1f));
        }

        root.addView(item, spaced());
    }

    private void addLibraryStatusRow(LinearLayout root, String text) {
        TextView row = new TextView(this);
        row.setText(text);
        row.setTextColor(COLOR_TEXT);
        row.setTextSize(14);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(panelBg(0xff0d141b, dp(12), 0xff1d2b36));
        root.addView(row, spaced());
    }

    private void showLibraryPage() {
        if (playerPageView != null) {
            playerPageView.setVisibility(View.GONE);
        }
        if (libraryPageView != null) {
            libraryPageView.setVisibility(View.VISIBLE);
        }
        loadLibraryIfNeeded();
    }

    private void showPlayerPage() {
        if (libraryPageView != null) {
            libraryPageView.setVisibility(View.GONE);
        }
        if (playerPageView != null) {
            playerPageView.setVisibility(View.VISIBLE);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void attachSwipeNavigation(View view) {
        view.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                swipeStartX = event.getX();
                swipeStartY = event.getY();
                return false;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                float dx = event.getX() - swipeStartX;
                float dy = event.getY() - swipeStartY;
                if (Math.abs(dx) > dp(92) && Math.abs(dx) > Math.abs(dy) * 1.45f) {
                    if (dx < 0) {
                        showLibraryPage();
                    } else {
                        showPlayerPage();
                    }
                    return true;
                }
            }
            return false;
        });
    }

    private void loadLibraryIfNeeded() {
        if (libraryLoaded || libraryLoading) {
            return;
        }
        loadLibrary(false);
    }

    private void loadLibrary(boolean force) {
        if (libraryLoading) {
            return;
        }
        if (!force && libraryLoaded) {
            return;
        }
        YmpRepository repository = new YmpRepository(this);
        if (!repository.hasToken()) {
            updateLibraryStatus(getString(R.string.library_login_required));
            return;
        }
        libraryLoading = true;
        updateLibraryStatus(getString(R.string.library_loading));
        new Thread(() -> {
            try {
                List<YandexMusicClient.PlaylistSummary> playlists = repository.playlists();
                runOnUiThread(() -> {
                    cachedPlaylists.clear();
                    cachedPlaylists.addAll(playlists);
                    libraryLoaded = true;
                    libraryLoading = false;
                    renderPlaylists();
                    updateLibraryStatus(getString(R.string.library_loaded, playlists.size()));
                });
            } catch (Exception ex) {
                Diagnostics.log(this, "YMP library load failed", ex);
                runOnUiThread(() -> {
                    libraryLoading = false;
                    updateLibraryStatus(getString(R.string.library_load_failed, ex.getMessage()));
                });
            }
        }, "YMP-Library").start();
    }

    private void renderPlaylists() {
        if (libraryPlaylistsView == null) {
            return;
        }
        libraryPlaylistsView.removeAllViews();
        if (cachedPlaylists.isEmpty()) {
            addLibraryStatusRow(libraryPlaylistsView, getString(R.string.library_playlists_empty));
            return;
        }
        for (YandexMusicClient.PlaylistSummary playlist : cachedPlaylists) {
            addPlaylistRow(libraryPlaylistsView, playlist);
        }
    }

    private void loadLocalPlaylists() {
        cachedLocalPlaylists.clear();
        cachedLocalPlaylists.addAll(new LocalPlaylistStore(this).list());
        renderLocalPlaylists();
    }

    private void renderLocalPlaylists() {
        if (localPlaylistsView == null) {
            return;
        }
        localPlaylistsView.removeAllViews();
        Button create = smallButton(getString(R.string.create_local_playlist), COLOR_ACCENT, COLOR_BG);
        create.setOnClickListener(v -> showCreateLocalPlaylistDialog());
        localPlaylistsView.addView(create, spaced());
        if (cachedLocalPlaylists.isEmpty()) {
            addLibraryStatusRow(localPlaylistsView, getString(R.string.local_playlists_empty));
            return;
        }
        for (LocalPlaylistStore.LocalPlaylist playlist : cachedLocalPlaylists) {
            addLocalPlaylistRow(localPlaylistsView, playlist);
        }
    }

    private void addLocalPlaylistRow(LinearLayout root, LocalPlaylistStore.LocalPlaylist playlist) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(dp(14), dp(11), dp(14), dp(11));
        item.setBackground(panelBg(COLOR_SURFACE, dp(12), COLOR_STROKE));

        TextView title = new TextView(this);
        title.setText(playlist.title);
        title.setTextColor(COLOR_TEXT);
        title.setTextSize(17);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        item.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText(getString(R.string.playlist_track_count, playlist.trackCount()));
        subtitle.setTextColor(COLOR_MUTED);
        subtitle.setTextSize(13);
        item.addView(subtitle, matchWrap());

        LinearLayout actions = row();
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionParams = matchWrap();
        actionParams.setMargins(0, dp(10), 0, 0);
        item.addView(actions, actionParams);

        Button play = smallButton(getString(R.string.select_playlist), COLOR_ACCENT, COLOR_BG);
        play.setOnClickListener(v -> {
            selectLocalPlaylistSource(playlist.id, playlist.title);
            showPlayerPage();
        });
        actions.addView(play, rowButtonParams(1f));

        if (LocalPlaylistStore.isLocalFavoritesId(playlist.id)) {
            Button clear = smallButton(getString(R.string.clear_playlist), COLOR_DANGER, COLOR_TEXT);
            clear.setOnClickListener(v -> confirmClearLocalPlaylist(playlist, null));
            actions.addView(clear, rowButtonParams(1f));
        } else {
            Button files = smallButton(getString(R.string.add_local_files), COLOR_SURFACE_2, COLOR_TEXT);
            files.setOnClickListener(v -> openLocalFilesPicker(playlist.id));
            actions.addView(files, rowButtonParams(1f));

            Button folder = smallButton(getString(R.string.add_local_folder), COLOR_SURFACE_2, COLOR_TEXT);
            folder.setOnClickListener(v -> openLocalFolderPicker(playlist.id));
            actions.addView(folder, rowButtonParams(1f));

            Button delete = smallButton(getString(R.string.delete_playlist), COLOR_DANGER, COLOR_TEXT);
            delete.setOnClickListener(v -> confirmDeleteLocalPlaylist(playlist, null));
            LinearLayout.LayoutParams deleteParams = matchWrap();
            deleteParams.setMargins(0, dp(8), 0, 0);
            item.addView(delete, deleteParams);
        }

        root.addView(item, spaced());
    }

    private void addPlaylistRow(LinearLayout root, YandexMusicClient.PlaylistSummary playlist) {
        addLibraryActionButtons(
                root,
                playlist.title,
                getString(R.string.playlist_track_count, playlist.trackCount),
                () -> {
                    selectPlaylistSource(playlist.kind, playlist.title);
                    showPlayerPage();
                },
                getString(R.string.delete_playlist),
                () -> confirmDeleteYandexPlaylist(playlist, null)
        );
    }

    private void updateLibraryStatus(String text) {
        if (libraryStatusView != null) {
            libraryStatusView.setText(text == null ? "" : text);
        }
        updateStatus(text == null ? "" : text);
    }

    private void confirmDeleteYandexPlaylist(YandexMusicClient.PlaylistSummary playlist, Runnable afterStarted) {
        if (playlist == null) {
            return;
        }
        showConfirmDialog(
                getString(R.string.delete_playlist),
                getString(R.string.confirm_delete_yandex_playlist, playlist.title),
                getString(R.string.delete_playlist),
                () -> deleteYandexPlaylist(playlist, afterStarted)
        );
    }

    private void deleteYandexPlaylist(YandexMusicClient.PlaylistSummary playlist, Runnable afterStarted) {
        persistTypedToken();
        updateLibraryStatus(getString(R.string.yandex_playlist_delete_started, playlist.title));
        Context appContext = getApplicationContext();
        new Thread(() -> {
            try {
                new YmpRepository(appContext).deletePlaylist(playlist.kind);
                runOnUiThread(() -> {
                    cachedPlaylists.removeIf(item -> item.kind == playlist.kind);
                    if (selectedSourceType == YmpPlaybackService.SOURCE_PLAYLIST
                            && selectedPlaylistKind == playlist.kind) {
                        selectPlaybackSource(true);
                    }
                    renderPlaylists();
                    updateLibraryStatus(getString(R.string.yandex_playlist_deleted, playlist.title));
                    libraryLoaded = false;
                    loadLibrary(true);
                });
            } catch (Exception ex) {
                Diagnostics.log(appContext, "YMP Yandex playlist delete failed: " + playlist.kind, ex);
                runOnUiThread(() -> updateLibraryStatus(getString(R.string.yandex_playlist_delete_failed, ex.getMessage())));
            }
        }, "YMP-DeleteYandexPlaylist").start();
        if (afterStarted != null) {
            afterStarted.run();
        }
    }

    private void confirmDeleteLocalPlaylist(LocalPlaylistStore.LocalPlaylist playlist, Runnable afterStarted) {
        if (playlist == null || LocalPlaylistStore.isLocalFavoritesId(playlist.id)) {
            return;
        }
        showConfirmDialog(
                getString(R.string.delete_playlist),
                getString(R.string.confirm_delete_local_playlist, playlist.title),
                getString(R.string.delete_playlist),
                () -> deleteLocalPlaylist(playlist, afterStarted)
        );
    }

    private void deleteLocalPlaylist(LocalPlaylistStore.LocalPlaylist playlist, Runnable afterStarted) {
        boolean deleted = new YmpRepository(this).deleteLocalPlaylist(playlist.id);
        loadLocalPlaylists();
        if (deleted && selectedSourceType == YmpPlaybackService.SOURCE_LOCAL_PLAYLIST
                && selectedLocalPlaylistId.equals(playlist.id)) {
            selectPlaybackSource(true);
        }
        updateLibraryStatus(getString(deleted
                ? R.string.local_playlist_deleted
                : R.string.local_playlist_delete_failed, playlist.title));
        if (afterStarted != null) {
            afterStarted.run();
        }
    }

    private void confirmClearLocalPlaylist(LocalPlaylistStore.LocalPlaylist playlist, Runnable afterStarted) {
        if (playlist == null) {
            return;
        }
        showConfirmDialog(
                getString(R.string.clear_playlist),
                getString(R.string.confirm_clear_local_playlist, playlist.title),
                getString(R.string.clear_playlist),
                () -> clearLocalPlaylist(playlist, afterStarted)
        );
    }

    private void clearLocalPlaylist(LocalPlaylistStore.LocalPlaylist playlist, Runnable afterStarted) {
        boolean cleared = new YmpRepository(this).clearLocalPlaylist(playlist.id);
        loadLocalPlaylists();
        updateLibraryStatus(getString(cleared
                ? R.string.local_playlist_cleared
                : R.string.local_playlist_clear_failed, playlist.title));
        if (afterStarted != null) {
            afterStarted.run();
        }
    }

    private void showConfirmDialog(String title, String message, String positiveLabel, Runnable confirmed) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(16), dp(18), dp(18));
        root.setBackgroundColor(COLOR_BG);

        root.addView(sectionTitle(title), matchWrap());

        TextView text = new TextView(this);
        text.setText(message);
        text.setTextColor(COLOR_TEXT);
        text.setTextSize(14);
        text.setPadding(0, dp(10), 0, dp(12));
        root.addView(text, matchWrap());

        LinearLayout actions = row();
        actions.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(actions, matchWrap());

        Button positive = smallButton(positiveLabel, COLOR_DANGER, COLOR_TEXT);
        positive.setOnClickListener(v -> {
            dialog.dismiss();
            confirmed.run();
        });
        actions.addView(positive, rowButtonParams(1f));

        Button cancel = smallButton(getString(R.string.cancel_action), COLOR_SURFACE_2, COLOR_TEXT);
        cancel.setOnClickListener(v -> dialog.dismiss());
        actions.addView(cancel, rowButtonParams(1f));

        dialog.setContentView(root);
        prepareDialogWindow(dialog, 520);
        dialog.show();
    }

    private void showCreateLocalPlaylistDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(16), dp(18), dp(18));
        root.setBackgroundColor(COLOR_BG);
        root.addView(sectionTitle(getString(R.string.create_local_playlist)), matchWrap());

        EditText title = new EditText(this);
        title.setHint(R.string.local_playlist_name_hint);
        title.setSingleLine(true);
        title.setTextColor(COLOR_TEXT);
        title.setHintTextColor(COLOR_MUTED);
        title.setBackground(panelBg(COLOR_SURFACE_2, dp(10), COLOR_STROKE));
        title.setPadding(dp(12), 0, dp(12), 0);
        root.addView(title, spaced());

        Button create = controlButton(getString(R.string.create_local_playlist), COLOR_ACCENT, COLOR_BG, 52);
        create.setOnClickListener(v -> {
            LocalPlaylistStore.LocalPlaylist playlist = new LocalPlaylistStore(this)
                    .create(title.getText().toString());
            loadLocalPlaylists();
            selectLocalPlaylistSource(playlist.id, playlist.title);
            updateStatus(getString(R.string.local_playlist_created, playlist.title));
            dialog.dismiss();
        });
        root.addView(create, spaced());

        dialog.setContentView(root);
        prepareDialogWindow(dialog, 520);
        dialog.show();
    }

    private void showAddCurrentToYandexPlaylistDialog() {
        if (!libraryLoaded) {
            loadLibrary(true);
        }
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(COLOR_BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(16), dp(18), dp(18));
        scroll.addView(root, matchScroll());

        root.addView(sectionTitle(getString(R.string.add_current_to_yandex_playlist)), matchWrap());

        EditText title = new EditText(this);
        title.setHint(R.string.new_yandex_playlist_hint);
        title.setSingleLine(true);
        title.setTextColor(COLOR_TEXT);
        title.setHintTextColor(COLOR_MUTED);
        title.setBackground(panelBg(COLOR_SURFACE_2, dp(10), COLOR_STROKE));
        title.setPadding(dp(12), 0, dp(12), 0);
        root.addView(title, spaced());

        Button create = controlButton(getString(R.string.create_yandex_playlist_and_add), COLOR_ACCENT, COLOR_BG, 52);
        create.setOnClickListener(v -> {
            String playlistTitle = title.getText().toString().trim();
            if (playlistTitle.isEmpty()) {
                updateStatus(getString(R.string.playlist_name_empty));
                return;
            }
            sendCreateYandexPlaylistAndAddAction(playlistTitle);
            libraryLoaded = false;
            dialog.dismiss();
        });
        root.addView(create, spaced());

        root.addView(sectionTitle(getString(R.string.section_my_playlists)), spaced());
        if (cachedPlaylists.isEmpty()) {
            addLibraryStatusRow(root, getString(libraryLoading
                    ? R.string.library_loading
                    : R.string.library_playlists_empty));
        } else {
            for (YandexMusicClient.PlaylistSummary playlist : cachedPlaylists) {
                addLibraryAction(
                        root,
                        playlist.title,
                        getString(R.string.playlist_track_count, playlist.trackCount),
                        () -> {
                            sendAddCurrentToYandexPlaylistAction(playlist.kind, playlist.title);
                            dialog.dismiss();
                        }
                );
            }
        }

        dialog.setContentView(scroll);
        prepareDialogWindow(dialog, 720);
        dialog.show();
    }

    private void openLocalFilesPicker(String playlistId) {
        pendingLocalPlaylistId = playlistId == null ? "" : playlistId;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_LOCAL_FILES);
        } catch (Exception ex) {
            updateStatus(getString(R.string.local_picker_failed, ex.getMessage()));
        }
    }

    private void openLocalFolderPicker(String playlistId) {
        pendingLocalPlaylistId = playlistId == null ? "" : playlistId;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_LOCAL_FOLDER);
        } catch (Exception ex) {
            updateStatus(getString(R.string.local_picker_failed, ex.getMessage()));
        }
    }

    private void handleLocalFilesResult(Intent data) {
        String playlistId = pendingLocalPlaylistId;
        pendingLocalPlaylistId = "";
        if (playlistId.isEmpty()) {
            return;
        }
        persistReadPermissions(data);
        List<LocalPlaylistStore.LocalTrack> tracks = LocalPlaylistStore.tracksFromFileIntent(this, data);
        addLocalTracksAsync(playlistId, tracks, getString(R.string.local_files_added));
    }

    private void handleLocalFolderResult(Intent data) {
        String playlistId = pendingLocalPlaylistId;
        pendingLocalPlaylistId = "";
        if (playlistId.isEmpty() || data == null || data.getData() == null) {
            return;
        }
        Uri treeUri = data.getData();
        persistReadPermission(treeUri);
        updateStatus(getString(R.string.local_folder_scanning));
        Context appContext = getApplicationContext();
        new Thread(() -> {
            try {
                List<LocalPlaylistStore.LocalTrack> tracks = LocalPlaylistStore.tracksFromTree(appContext, treeUri);
                runOnUiThread(() -> addLocalTracksAsync(playlistId, tracks, getString(R.string.local_folder_added)));
            } catch (Exception ex) {
                Diagnostics.log(appContext, "YMP local folder import failed", ex);
                runOnUiThread(() -> updateStatus(getString(R.string.local_import_failed, ex.getMessage())));
            }
        }, "YMP-ImportLocalFolder").start();
    }

    private void addLocalTracksAsync(String playlistId, List<LocalPlaylistStore.LocalTrack> tracks, String messageTemplate) {
        if (tracks == null || tracks.isEmpty()) {
            updateStatus(getString(R.string.local_no_audio_found));
            return;
        }
        Context appContext = getApplicationContext();
        new Thread(() -> {
            LocalPlaylistStore.LocalPlaylist updated = new LocalPlaylistStore(appContext).addTracks(playlistId, tracks);
            int artworkUpdated = LocalArtworkEnricher.enrichMissingArtwork(appContext, tracks);
            runOnUiThread(() -> {
                loadLocalPlaylists();
                String title = updated == null ? "" : updated.title;
                updateStatus(String.format(messageTemplate, tracks.size(), title));
                if (artworkUpdated > 0) {
                    updateStatus(getString(R.string.local_artwork_updated, artworkUpdated));
                }
            });
        }, "YMP-AddLocalTracks").start();
    }

    private void persistReadPermissions(Intent data) {
        if (data == null) {
            return;
        }
        ClipData clipData = data.getClipData();
        if (clipData != null) {
            for (int i = 0; i < clipData.getItemCount(); i++) {
                persistReadPermission(clipData.getItemAt(i).getUri());
            }
        }
        persistReadPermission(data.getData());
    }

    private void persistReadPermission(Uri uri) {
        if (uri == null) {
            return;
        }
        try {
            getContentResolver().takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            );
        } catch (Exception ex) {
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception readOnlyEx) {
                Diagnostics.log(this, "YMP persist local URI permission ignored: " + uri, readOnlyEx);
            }
        }
    }

    private void showPlaylistSelector() {
        loadLocalPlaylists();
        if (!libraryLoaded && new YmpRepository(this).hasToken()) {
            loadLibrary(true);
            Toast.makeText(this, R.string.library_loading, Toast.LENGTH_SHORT).show();
        }
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(COLOR_BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(16), dp(18), dp(18));
        scroll.addView(root, matchScroll());

        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(top, matchWrap());
        TextView title = sectionTitle(getString(R.string.playlist_selector_title));
        top.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button refresh = smallButton(getString(R.string.refresh_library), COLOR_SURFACE_2, COLOR_TEXT);
        refresh.setOnClickListener(v -> {
            dialog.dismiss();
            libraryLoaded = false;
            loadLibrary(true);
            showLibraryPage();
        });
        top.addView(refresh, compactButtonParams(dp(118)));

        root.addView(sectionTitle(getString(R.string.section_my_playlists)), spaced());
        if (cachedPlaylists.isEmpty()) {
            addLibraryStatusRow(root, getString(R.string.library_playlists_empty));
        } else {
            for (YandexMusicClient.PlaylistSummary playlist : cachedPlaylists) {
                addLibraryActionButtons(
                        root,
                        playlist.title,
                        getString(R.string.playlist_track_count, playlist.trackCount),
                        () -> {
                            selectPlaylistSource(playlist.kind, playlist.title);
                            dialog.dismiss();
                        },
                        getString(R.string.delete_playlist),
                        () -> confirmDeleteYandexPlaylist(playlist, () -> dialog.dismiss())
                );
            }
        }

        root.addView(sectionTitle(getString(R.string.section_local_playlists)), spaced());
        if (cachedLocalPlaylists.isEmpty()) {
            addLibraryStatusRow(root, getString(R.string.local_playlists_empty));
        } else {
            for (LocalPlaylistStore.LocalPlaylist playlist : cachedLocalPlaylists) {
                addLibraryActionButtons(
                        root,
                        playlist.title,
                        getString(R.string.playlist_track_count, playlist.trackCount()),
                        () -> {
                            selectLocalPlaylistSource(playlist.id, playlist.title);
                            dialog.dismiss();
                        },
                        getString(LocalPlaylistStore.isLocalFavoritesId(playlist.id)
                                ? R.string.clear_playlist
                                : R.string.delete_playlist),
                        () -> {
                            if (LocalPlaylistStore.isLocalFavoritesId(playlist.id)) {
                                confirmClearLocalPlaylist(playlist, () -> dialog.dismiss());
                            } else {
                                confirmDeleteLocalPlaylist(playlist, () -> dialog.dismiss());
                            }
                        }
                );
            }
        }

        dialog.setContentView(scroll);
        prepareDialogWindow(dialog, 720);
        dialog.show();
    }

    private void showSearchEntry() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(COLOR_BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(16), dp(18), dp(18));
        root.setBackgroundColor(COLOR_BG);
        scroll.addView(root, matchScroll());
        root.addView(sectionTitle(getString(R.string.search_music)), matchWrap());

        EditText query = new EditText(this);
        query.setHint(R.string.search_hint);
        query.setSingleLine(true);
        query.setTextColor(COLOR_TEXT);
        query.setHintTextColor(COLOR_MUTED);
        query.setBackground(panelBg(COLOR_SURFACE_2, dp(10), COLOR_STROKE));
        query.setPadding(dp(12), 0, dp(12), 0);
        root.addView(query, spaced());

        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);

        Button search = controlButton(getString(R.string.search_music), COLOR_ACCENT, COLOR_BG, 52);
        search.setOnClickListener(v -> runSearch(query.getText().toString(), results, dialog));
        root.addView(search, spaced());

        root.addView(results, matchWrap());

        dialog.setContentView(scroll);
        prepareDialogWindow(dialog, 760);
        dialog.show();
    }

    private void runSearch(String rawQuery, LinearLayout results, Dialog dialog) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.isEmpty()) {
            updateStatus(getString(R.string.search_query_empty));
            return;
        }
        persistTypedToken();
        if (TokenStore.getAccessToken(this).trim().isEmpty()) {
            updateStatus(getString(R.string.library_login_required));
            Toast.makeText(this, R.string.library_login_required, Toast.LENGTH_SHORT).show();
            return;
        }
        results.removeAllViews();
        addLibraryStatusRow(results, getString(R.string.search_loading));
        updateStatus(getString(R.string.search_loading));
        Context appContext = getApplicationContext();
        new Thread(() -> {
            try {
                YandexMusicClient.SearchResults searchResults = new YmpRepository(appContext).search(query);
                runOnUiThread(() -> renderSearchResults(searchResults, results, dialog));
            } catch (Exception ex) {
                Diagnostics.log(appContext, "YMP search failed: " + query, ex);
                runOnUiThread(() -> {
                    results.removeAllViews();
                    addLibraryStatusRow(results, getString(R.string.search_failed, ex.getMessage()));
                    updateStatus(getString(R.string.search_failed, ex.getMessage()));
                });
            }
        }, "YMP-Search").start();
    }

    private void renderSearchResults(
            YandexMusicClient.SearchResults searchResults,
            LinearLayout results,
            Dialog dialog
    ) {
        results.removeAllViews();
        if (searchResults == null || searchResults.isEmpty()) {
            addLibraryStatusRow(results, getString(R.string.search_empty));
            updateStatus(getString(R.string.search_empty));
            return;
        }
        updateStatus(getString(
                R.string.search_loaded,
                searchResults.tracks.size(),
                searchResults.albums.size(),
                searchResults.artists.size()
        ));

        if (!searchResults.tracks.isEmpty()) {
            results.addView(sectionTitle(getString(R.string.search_section_tracks)), spaced());
            int limit = Math.min(12, searchResults.tracks.size());
            for (int i = 0; i < limit; i++) {
                YandexMusicClient.Track track = searchResults.tracks.get(i);
                addLibraryAction(
                        results,
                        track.title,
                        firstNonEmpty(track.artist, track.album, track.key),
                        () -> {
                            sendSearchTrackAction(track);
                            dialog.dismiss();
                            showPlayerPage();
                        }
                );
            }
        }

        if (!searchResults.albums.isEmpty()) {
            results.addView(sectionTitle(getString(R.string.search_section_albums)), spaced());
            int limit = Math.min(8, searchResults.albums.size());
            for (int i = 0; i < limit; i++) {
                YandexMusicClient.AlbumInfo album = searchResults.albums.get(i);
                String subtitle = album.artist
                        + (album.trackCount > 0 ? " - " + getString(R.string.playlist_track_count, album.trackCount) : "");
                addLibraryAction(
                        results,
                        album.title,
                        subtitle.trim(),
                        () -> {
                            sendAlbumAction(album);
                            dialog.dismiss();
                            showPlayerPage();
                        }
                );
            }
        }

        if (!searchResults.artists.isEmpty()) {
            results.addView(sectionTitle(getString(R.string.search_section_artists)), spaced());
            int limit = Math.min(8, searchResults.artists.size());
            for (int i = 0; i < limit; i++) {
                YandexMusicClient.ArtistInfo artist = searchResults.artists.get(i);
                addLibraryAction(
                        results,
                        artist.name,
                        getString(R.string.search_artist_tracks_hint),
                        () -> {
                            sendArtistAction(artist);
                            dialog.dismiss();
                            showPlayerPage();
                        }
                );
            }
        }
    }

    private void prepareDialogWindow(Dialog dialog, int maxWidthDp) {
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.setOnShowListener(d -> {
            Window shown = dialog.getWindow();
            if (shown != null) {
                shown.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                shown.setLayout(
                        isWideLayout() ? Math.min(dp(maxWidthDp), getResources().getDisplayMetrics().widthPixels - dp(48)) : WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.WRAP_CONTENT
                );
            }
        });
    }

    private void addTopBar(LinearLayout root) {
        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(top, matchWrap());

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        top.addView(titleBox, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(this);
        title.setText(R.string.main_title);
        title.setTextColor(COLOR_TEXT);
        title.setTextSize(isWideLayout() ? 28 : 30);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleBox.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText(R.string.main_subtitle);
        subtitle.setTextColor(COLOR_MUTED);
        subtitle.setTextSize(13);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        titleBox.addView(subtitle, matchWrap());

        sidebarToggleButton = smallIconButton(R.drawable.ic_player_sidebar, COLOR_SURFACE_2, COLOR_TEXT, dp(46), getString(R.string.sidebar_quick_toggle));
        sidebarToggleButton.setOnClickListener(v -> toggleEmbeddedSideBar());
        top.addView(sidebarToggleButton, compactButtonParams(dp(52)));

        Button settings = smallButton(getString(R.string.settings_menu), COLOR_SURFACE_2, COLOR_TEXT);
        settings.setOnClickListener(v -> showSettingsDialog());
        top.addView(settings, compactButtonParams(dp(118)));
    }

    private void addCoverPanel(LinearLayout playerSurface, boolean wide) {
        FrameLayout coverPanel = new FrameLayout(this);
        coverPanel.setPadding(dp(10), dp(10), dp(10), dp(10));
        coverPanel.setBackground(panelBg(0xff0b1118, dp(16), 0xff25384a));

        int artSize = coverSize(wide);
        LinearLayout.LayoutParams panelParams = new LinearLayout.LayoutParams(artSize, artSize);
        panelParams.gravity = Gravity.CENTER;
        panelParams.setMargins(0, 0, wide ? dp(22) : 0, wide ? 0 : dp(18));
        playerSurface.addView(coverPanel, panelParams);

        coverView = new ImageView(this);
        coverView.setImageResource(R.mipmap.ic_launcher);
        coverView.setBackgroundColor(0xff20242b);
        coverView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        coverPanel.addView(coverView, matchFrame());
    }

    private void addPlayerInfoPanel(LinearLayout playerSurface, boolean wide) {
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setGravity(Gravity.CENTER_VERTICAL);
        playerSurface.addView(info, wide
                ? new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                : matchWrap());

        LinearLayout sources = row();
        sources.setGravity(Gravity.CENTER_VERTICAL);
        info.addView(sources, matchWrap());
        waveSourceButton = pillButton(getString(R.string.play_my_wave_compact), COLOR_ACCENT, 0xff151100);
        waveSourceButton.setOnClickListener(v -> selectPlaybackSource(true));
        sources.addView(waveSourceButton, rowButtonParams(1.15f));
        offlineSourceButton = pillButton(getString(R.string.play_liked_cache_compact), COLOR_SURFACE_2, COLOR_TEXT);
        offlineSourceButton.setOnClickListener(v -> selectPlaybackSource(false));
        sources.addView(offlineSourceButton, rowButtonParams(1.25f));
        playlistSourceButton = pillButton(getString(R.string.playlist_source_empty), COLOR_SURFACE_2, COLOR_TEXT);
        playlistSourceButton.setOnClickListener(v -> showPlaylistSelector());
        sources.addView(playlistSourceButton, rowButtonParams(1.2f));

        modeView = new TextView(this);
        modeView.setText(R.string.source_my_wave);
        modeView.setTextColor(COLOR_ACCENT);
        modeView.setTextSize(12);
        modeView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        modeView.setSingleLine(true);
        modeView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams modeParams = matchWrap();
        modeParams.setMargins(0, dp(10), 0, dp(2));
        info.addView(modeView, modeParams);

        LinearLayout titleRow = row();
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        info.addView(titleRow, matchWrap());
        likeButton = smallIconButton(R.drawable.ic_player_like, 0xff1f3b32, 0xffbcffe8, dp(44), getString(R.string.like_track));
        likeButton.setOnClickListener(v -> sendPlayerAction(YmpPlaybackService.ACTION_LIKE));
        titleRow.addView(likeButton, compactButtonParams(dp(48)));
        dislikeButton = smallIconButton(R.drawable.ic_player_dislike, 0xff3a1d27, 0xffffbec9, dp(44), getString(R.string.dislike_track));
        dislikeButton.setOnClickListener(v -> sendPlayerAction(YmpPlaybackService.ACTION_DISLIKE));
        titleRow.addView(dislikeButton, compactButtonParams(dp(48)));
        addToPlaylistButton = smallIconButton(R.drawable.ic_player_add_playlist, 0xff24334a, 0xffd6e5ff, dp(44), getString(R.string.add_current_to_yandex_playlist));
        addToPlaylistButton.setOnClickListener(v -> showAddCurrentToYandexPlaylistDialog());
        titleRow.addView(addToPlaylistButton, compactButtonParams(dp(48)));

        nowTitleView = new TextView(this);
        nowTitleView.setText(R.string.now_playing_empty);
        nowTitleView.setTextColor(COLOR_TEXT);
        nowTitleView.setTextSize(wide ? 30 : 25);
        nowTitleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        nowTitleView.setMaxLines(wide ? 2 : 3);
        nowTitleView.setEllipsize(TextUtils.TruncateAt.END);
        titleRow.addView(nowTitleView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        nowArtistView = new TextView(this);
        nowArtistView.setText("");
        nowArtistView.setTextColor(COLOR_MUTED);
        nowArtistView.setTextSize(wide ? 18 : 17);
        nowArtistView.setSingleLine(true);
        nowArtistView.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(nowArtistView, matchWrap());

        nowAlbumView = new TextView(this);
        nowAlbumView.setText("");
        nowAlbumView.setTextColor(0xff718695);
        nowAlbumView.setTextSize(13);
        nowAlbumView.setSingleLine(true);
        nowAlbumView.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(nowAlbumView, matchWrap());

        queueView = new TextView(this);
        queueView.setText(R.string.queue_empty);
        queueView.setTextColor(COLOR_MUTED);
        queueView.setTextSize(13);
        queueView.setSingleLine(true);
        queueView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams queueParams = matchWrap();
        queueParams.setMargins(0, dp(8), 0, dp(10));
        info.addView(queueView, queueParams);

        LinearLayout transport = row();
        transport.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams transportParams = matchWrap();
        transportParams.setMargins(0, dp(14), 0, 0);
        info.addView(transport, transportParams);
        int sideSize = wide ? dp(64) : dp(58);
        int playSize = wide ? dp(82) : dp(74);
        ImageButton prev = transportButton(R.drawable.ic_player_previous, COLOR_SURFACE_2, COLOR_TEXT, sideSize, getString(R.string.previous_track));
        prev.setOnClickListener(v -> sendPlayerAction(YmpPlaybackService.ACTION_PREVIOUS));
        transport.addView(prev, compactButtonParams(sideSize + dp(8)));
        ImageButton stop = transportButton(R.drawable.ic_player_stop, COLOR_SURFACE_2, COLOR_TEXT, sideSize, getString(R.string.stop_playback));
        stop.setOnClickListener(v -> sendPlayerAction(YmpPlaybackService.ACTION_STOP));
        transport.addView(stop, compactButtonParams(sideSize + dp(8)));
        playPauseButton = transportButton(R.drawable.ic_player_play, COLOR_SURFACE_2, COLOR_TEXT, playSize, getString(R.string.play_pause));
        playPauseButton.setOnClickListener(v -> handlePlayPause());
        transport.addView(playPauseButton, compactButtonParams(playSize + dp(10)));
        ImageButton next = transportButton(R.drawable.ic_player_next, COLOR_SURFACE_2, COLOR_TEXT, sideSize, getString(R.string.next_track));
        next.setOnClickListener(v -> sendPlayerAction(YmpPlaybackService.ACTION_NEXT));
        transport.addView(next, compactButtonParams(sideSize + dp(8)));
        queueModeButton = transportButton(R.drawable.ic_player_order, COLOR_SURFACE_2, COLOR_TEXT, sideSize, getString(R.string.queue_mode_order));
        queueModeButton.setOnClickListener(v -> sendPlayerAction(YmpPlaybackService.ACTION_TOGGLE_SHUFFLE));
        transport.addView(queueModeButton, compactButtonParams(sideSize + dp(8)));
        equalizerButton = transportButton(R.drawable.ic_player_equalizer, COLOR_SURFACE_2, COLOR_TEXT, sideSize, getString(R.string.open_equalizer));
        equalizerButton.setOnClickListener(v -> openEqualizer());
        if (wide) {
            transport.addView(equalizerButton, compactButtonParams(sideSize + dp(8)));
        } else {
            LinearLayout tools = row();
            tools.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams toolsParams = matchWrap();
            toolsParams.setMargins(0, dp(10), 0, 0);
            info.addView(tools, toolsParams);
            tools.addView(equalizerButton, compactButtonParams(sideSize + dp(8)));
        }
        updateTransportVisuals();
    }

    private void showSettingsDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setBackgroundColor(COLOR_BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(16), dp(18), dp(18));
        scroll.addView(root, matchScroll());

        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(top, matchWrap());

        TextView title = sectionTitle(getString(R.string.settings_title));
        top.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button close = smallButton(getString(R.string.settings_close), COLOR_SURFACE_2, COLOR_TEXT);
        close.setOnClickListener(v -> dialog.dismiss());
        top.addView(close, compactButtonParams(dp(110)));

        addAccountSettings(root);
        addCacheSettings(root);
        addIntegrationSettings(root);
        addDiagnosticsSettings(root);

        dialog.setContentView(scroll);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.setOnShowListener(d -> {
            Window shown = dialog.getWindow();
            if (shown != null) {
                shown.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                shown.setLayout(
                        isWideLayout() ? Math.min(dp(720), getResources().getDisplayMetrics().widthPixels - dp(48)) : WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.WRAP_CONTENT
                );
            }
        });
        dialog.show();
    }

    private void addAccountSettings(LinearLayout root) {
        addSection(root, R.string.section_account);
        addButton(root, R.string.start_device_login, v -> startDeviceLogin());

        loginCodeView = new TextView(this);
        loginCodeView.setText(latestDeviceCode.isEmpty()
                ? getString(R.string.login_code_empty)
                : getString(R.string.login_code_template, latestDeviceCode));
        loginCodeView.setTextColor(COLOR_ACCENT);
        loginCodeView.setTextSize(22);
        loginCodeView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        loginCodeView.setGravity(Gravity.CENTER_HORIZONTAL);
        loginCodeView.setTextIsSelectable(true);
        loginCodeView.setPadding(0, dp(6), 0, dp(8));
        root.addView(loginCodeView, matchWrap());
        addButton(root, R.string.copy_login_code, v -> copyLoginCode());

        tokenEdit = new EditText(this);
        tokenEdit.setHint(R.string.oauth_token_hint);
        tokenEdit.setText(TokenStore.getAccessToken(this));
        tokenEdit.setTextColor(COLOR_TEXT);
        tokenEdit.setHintTextColor(COLOR_MUTED);
        tokenEdit.setSingleLine(false);
        tokenEdit.setMinLines(2);
        tokenEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        tokenEdit.setTransformationMethod(PasswordTransformationMethod.getInstance());
        tokenEdit.setBackground(panelBg(COLOR_SURFACE_2, dp(10), COLOR_STROKE));
        tokenEdit.setPadding(dp(12), dp(10), dp(12), dp(10));
        root.addView(tokenEdit, spaced());

        showTokenBox = checkbox(R.string.show_oauth_token);
        showTokenBox.setOnCheckedChangeListener((buttonView, isChecked) -> setTokenVisible(isChecked));
        root.addView(showTokenBox, spaced());

        addButton(root, R.string.save_token, v -> {
            TokenStore.save(this, tokenEdit.getText().toString().trim(), null);
            new YmpRepository(this).invalidateAccount();
            Diagnostics.log(this, "YMP token saved manually");
            updateStatus(statusWithCache("Token saved"));
        });
        addButton(root, R.string.test_account, v -> testAccount());
        addButton(root, R.string.clear_token, v -> {
            TokenStore.clear(this);
            tokenEdit.setText("");
            Diagnostics.log(this, "YMP token cleared");
            updateStatus(statusWithCache("Token cleared"));
        });
    }

    private void addCacheSettings(LinearLayout root) {
        addSection(root, R.string.section_cache);
        wifiOnlyBox = checkbox(R.string.wifi_only);
        wifiOnlyBox.setChecked(CacheSettings.isWifiOnly(this));
        wifiOnlyBox.setOnCheckedChangeListener((buttonView, isChecked) -> saveCacheSettings());
        root.addView(wifiOnlyBox, spaced());

        chargingOnlyBox = checkbox(R.string.charging_only);
        chargingOnlyBox.setChecked(CacheSettings.isChargingOnly(this));
        chargingOnlyBox.setOnCheckedChangeListener((buttonView, isChecked) -> saveCacheSettings());
        root.addView(chargingOnlyBox, spaced());

        autoCacheLikedBox = checkbox(R.string.auto_cache_liked_tracks);
        autoCacheLikedBox.setChecked(YmpSettings.isAutoCacheLikedEnabled(this));
        autoCacheLikedBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            YmpSettings.setAutoCacheLikedEnabled(this, isChecked);
            Diagnostics.log(this, "YMP auto-cache liked setting saved: enabled=" + isChecked);
            updateStatus(statusWithCache(isChecked ? "Auto-cache liked tracks enabled" : "Auto-cache liked tracks disabled"));
        });
        root.addView(autoCacheLikedBox, spaced());

        addButton(root, R.string.sync_favorite_tracks, v -> startFavoritesCacheSync());
        addButton(root, R.string.cancel_cache_sync, v -> cancelCacheSync());
        addButton(root, R.string.show_cache_status, v -> updateStatus(statusWithCache(CacheSyncService.lastStatus())));
        addButton(root, R.string.clear_local_cache, v -> clearLocalCache());
    }

    private void addIntegrationSettings(LinearLayout root) {
        addSection(root, R.string.section_integrations);
        sidebarWatchdogBox = checkbox(R.string.enable_embedded_sidebar);
        sidebarWatchdogBox.setChecked(YmpSettings.isEmbeddedSideBarEnabled(this));
        sidebarWatchdogBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && !EmbeddedSideBarService.hasOverlayPermission(this)) {
                sidebarWatchdogBox.setChecked(false);
                updateStatus(statusWithCache(getString(R.string.sidebar_overlay_permission_required)));
                try {
                    startActivity(EmbeddedSideBarService.overlayPermissionIntent(this));
                } catch (Exception ex) {
                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                }
                return;
            }
            YmpSettings.setEmbeddedSideBarEnabled(this, isChecked);
            Diagnostics.log(this, "YMP embedded SideBar setting saved: enabled=" + isChecked);
            if (isChecked) {
                ensureEmbeddedSideBar(false);
            } else {
                EmbeddedSideBarService.stop(this);
            }
            updateStatus(statusWithCache(isChecked ? "Embedded SideBar enabled" : "Embedded SideBar disabled"));
        });
        root.addView(sidebarWatchdogBox, spaced());
        addButton(root, R.string.show_hide_sidebar, v -> toggleEmbeddedSideBar());
        addButton(root, R.string.open_battery_settings, v -> openBatterySettings());
        addButton(root, R.string.open_autostart_settings, v -> openAutostartSettings());

        equalizerPackageEdit = new EditText(this);
        equalizerPackageEdit.setHint(R.string.equalizer_package_hint);
        equalizerPackageEdit.setText(YmpSettings.equalizerPackage(this));
        equalizerPackageEdit.setTextColor(COLOR_TEXT);
        equalizerPackageEdit.setHintTextColor(0xffd7e2ea);
        equalizerPackageEdit.setSingleLine(true);
        equalizerPackageEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        equalizerPackageEdit.setBackground(panelBg(COLOR_SURFACE_2, dp(10), COLOR_STROKE));
        equalizerPackageEdit.setPadding(dp(12), 0, dp(12), 0);
        root.addView(equalizerPackageEdit, spaced());
        addButton(root, R.string.save_equalizer_app, v -> {
            YmpSettings.setEqualizerPackage(this, equalizerPackageEdit.getText().toString());
            updateStatus(statusWithCache(getString(R.string.equalizer_app_saved)));
        });
        addButton(root, R.string.choose_equalizer_app, v -> showEqualizerChooser());
        addButton(root, R.string.open_equalizer, v -> openEqualizer());
    }

    private void addDiagnosticsSettings(LinearLayout root) {
        addSection(root, R.string.section_diagnostics);
        addButton(root, R.string.copy_diagnostics, v -> copyDiagnostics());
        addButton(root, R.string.clear_diagnostics, v -> clearDiagnostics());
    }

    private void sendPlayerAction(String action) {
        persistTypedToken();
        Intent intent = new Intent(this, YmpPlaybackService.class);
        intent.setAction(action);
        startForegroundService(intent);
    }

    private void selectPlaybackSource(boolean wave) {
        selectedSourceType = wave ? YmpPlaybackService.SOURCE_WAVE : YmpPlaybackService.SOURCE_OFFLINE;
        selectedPlaylistKind = -1;
        selectedPlaylistTitle = "";
        selectedLocalPlaylistId = "";
        selectedLocalPlaylistTitle = "";
        selectedWaveMode = wave;
        updateSourceButtons();
        updateTransportVisuals();
        updateStatus(getString(wave ? R.string.source_selected_my_wave : R.string.source_selected_offline));
    }

    private void selectPlaylistSource(int kind, String title) {
        selectedSourceType = YmpPlaybackService.SOURCE_PLAYLIST;
        selectedPlaylistKind = kind;
        selectedPlaylistTitle = title == null ? "" : title;
        selectedLocalPlaylistId = "";
        selectedLocalPlaylistTitle = "";
        selectedWaveMode = false;
        updateSourceButtons();
        updateTransportVisuals();
        updateStatus(getString(R.string.source_selected_playlist, selectedPlaylistTitle));
    }

    private void selectLocalPlaylistSource(String id, String title) {
        selectedSourceType = YmpPlaybackService.SOURCE_LOCAL_PLAYLIST;
        selectedPlaylistKind = -1;
        selectedPlaylistTitle = "";
        selectedLocalPlaylistId = id == null ? "" : id;
        selectedLocalPlaylistTitle = title == null ? "" : title;
        selectedWaveMode = false;
        updateSourceButtons();
        updateTransportVisuals();
        updateStatus(getString(R.string.source_selected_playlist, selectedLocalPlaylistTitle));
    }

    private void handlePlayPause() {
        if (currentPlaying || currentPrepared) {
            sendPlayerAction(YmpPlaybackService.ACTION_PLAY_PAUSE);
            return;
        }
        if (selectedSourceType == YmpPlaybackService.SOURCE_PLAYLIST) {
            if (selectedPlaylistKind < 0) {
                showPlaylistSelector();
                return;
            }
            sendPlaylistAction(selectedPlaylistKind, selectedPlaylistTitle);
            return;
        }
        if (selectedSourceType == YmpPlaybackService.SOURCE_LOCAL_PLAYLIST) {
            if (selectedLocalPlaylistId.isEmpty()) {
                showPlaylistSelector();
                return;
            }
            sendLocalPlaylistAction(selectedLocalPlaylistId, selectedLocalPlaylistTitle);
            return;
        }
        if (selectedSourceType == YmpPlaybackService.SOURCE_SEARCH) {
            showSearchEntry();
            return;
        }
        sendPlayerAction(selectedSourceType == YmpPlaybackService.SOURCE_WAVE
                ? YmpPlaybackService.ACTION_PLAY_WAVE
                : YmpPlaybackService.ACTION_PLAY_LIKED_CACHE);
    }

    private void sendPlaylistAction(int kind, String title) {
        persistTypedToken();
        Intent intent = new Intent(this, YmpPlaybackService.class);
        intent.setAction(YmpPlaybackService.ACTION_PLAY_PLAYLIST);
        intent.putExtra(YmpPlaybackService.EXTRA_PLAYLIST_KIND, kind);
        intent.putExtra(YmpPlaybackService.EXTRA_PLAYLIST_TITLE, title == null ? "" : title);
        startForegroundService(intent);
    }

    private void sendLocalPlaylistAction(String id, String title) {
        Intent intent = new Intent(this, YmpPlaybackService.class);
        intent.setAction(YmpPlaybackService.ACTION_PLAY_LOCAL_PLAYLIST);
        intent.putExtra(YmpPlaybackService.EXTRA_LOCAL_PLAYLIST_ID, id == null ? "" : id);
        intent.putExtra(YmpPlaybackService.EXTRA_LOCAL_PLAYLIST_TITLE, title == null ? "" : title);
        startForegroundService(intent);
    }

    private void sendSearchTrackAction(YandexMusicClient.Track track) {
        if (track == null) {
            return;
        }
        persistTypedToken();
        Intent intent = new Intent(this, YmpPlaybackService.class);
        intent.setAction(YmpPlaybackService.ACTION_PLAY_SEARCH_TRACK);
        intent.putExtra(YmpPlaybackService.EXTRA_TRACK_KEY, track.key);
        intent.putExtra(YmpPlaybackService.EXTRA_SOURCE_LABEL, getString(R.string.search_source_track));
        startForegroundService(intent);
        updateStatus(getString(R.string.search_playing_track, track.title));
    }

    private void sendAlbumAction(YandexMusicClient.AlbumInfo album) {
        if (album == null) {
            return;
        }
        persistTypedToken();
        Intent intent = new Intent(this, YmpPlaybackService.class);
        intent.setAction(YmpPlaybackService.ACTION_PLAY_ALBUM);
        intent.putExtra(YmpPlaybackService.EXTRA_ALBUM_ID, album.id);
        intent.putExtra(YmpPlaybackService.EXTRA_SOURCE_LABEL, getString(R.string.search_source_album, album.title));
        startForegroundService(intent);
        updateStatus(getString(R.string.search_playing_album, album.title));
    }

    private void sendArtistAction(YandexMusicClient.ArtistInfo artist) {
        if (artist == null) {
            return;
        }
        persistTypedToken();
        Intent intent = new Intent(this, YmpPlaybackService.class);
        intent.setAction(YmpPlaybackService.ACTION_PLAY_ARTIST);
        intent.putExtra(YmpPlaybackService.EXTRA_ARTIST_ID, artist.id);
        intent.putExtra(YmpPlaybackService.EXTRA_SOURCE_LABEL, getString(R.string.search_source_artist, artist.name));
        startForegroundService(intent);
        updateStatus(getString(R.string.search_playing_artist, artist.name));
    }

    private void sendAddCurrentToYandexPlaylistAction(int kind, String title) {
        persistTypedToken();
        Intent intent = new Intent(this, YmpPlaybackService.class);
        intent.setAction(YmpPlaybackService.ACTION_ADD_CURRENT_TO_PLAYLIST);
        intent.putExtra(YmpPlaybackService.EXTRA_PLAYLIST_KIND, kind);
        intent.putExtra(YmpPlaybackService.EXTRA_PLAYLIST_TITLE, title == null ? "" : title);
        startForegroundService(intent);
        updateStatus(getString(R.string.add_current_to_yandex_playlist_started));
    }

    private void sendCreateYandexPlaylistAndAddAction(String title) {
        persistTypedToken();
        Intent intent = new Intent(this, YmpPlaybackService.class);
        intent.setAction(YmpPlaybackService.ACTION_CREATE_PLAYLIST_AND_ADD);
        intent.putExtra(YmpPlaybackService.EXTRA_PLAYLIST_TITLE, title == null ? "" : title);
        startForegroundService(intent);
        updateStatus(getString(R.string.create_yandex_playlist_started));
    }

    private void restoreLastPlayerStatus() {
        Intent snapshot = YmpPlaybackService.latestStatusSnapshot(this);
        if (snapshot != null) {
            updatePlayerStatus(snapshot);
        }
    }

    private void updatePlayerStatus(Intent intent) {
        String title = intent.getStringExtra(YmpPlaybackService.EXTRA_TITLE);
        String artist = intent.getStringExtra(YmpPlaybackService.EXTRA_ARTIST);
        String album = intent.getStringExtra(YmpPlaybackService.EXTRA_ALBUM);
        String coverUrl = intent.getStringExtra(YmpPlaybackService.EXTRA_COVER_URL);
        String status = intent.getStringExtra(YmpPlaybackService.EXTRA_STATUS);
        int queue = intent.getIntExtra(YmpPlaybackService.EXTRA_QUEUE, 0);
        int index = intent.getIntExtra(YmpPlaybackService.EXTRA_INDEX, -1);
        boolean wave = intent.getBooleanExtra(YmpPlaybackService.EXTRA_WAVE, false);
        int sourceType = intent.getIntExtra(
                YmpPlaybackService.EXTRA_SOURCE_TYPE,
                wave ? YmpPlaybackService.SOURCE_WAVE : YmpPlaybackService.SOURCE_OFFLINE
        );
        String sourceTitle = intent.getStringExtra(YmpPlaybackService.EXTRA_SOURCE_TITLE);
        boolean shuffle = intent.getBooleanExtra(YmpPlaybackService.EXTRA_SHUFFLE, false);
        boolean playing = intent.getBooleanExtra(YmpPlaybackService.EXTRA_PLAYING, false);
        boolean prepared = intent.getBooleanExtra(YmpPlaybackService.EXTRA_PREPARED, false);
        int playMode = intent.getIntExtra(YmpPlaybackService.EXTRA_PLAY_MODE, 0);
        boolean liked = intent.getBooleanExtra(YmpPlaybackService.EXTRA_LIKED, false);
        currentAudioSessionId = intent.getIntExtra(YmpPlaybackService.EXTRA_AUDIO_SESSION_ID, currentAudioSessionId);

        currentWaveMode = wave;
        currentSourceType = sourceType;
        currentPlaying = playing;
        currentPrepared = prepared;
        currentLiked = liked;
        currentPlayMode = playMode;
        if (playing || prepared) {
            selectedSourceType = sourceType;
            selectedWaveMode = sourceType == YmpPlaybackService.SOURCE_WAVE;
            if (sourceType == YmpPlaybackService.SOURCE_PLAYLIST) {
                selectedPlaylistKind = intent.getIntExtra(YmpPlaybackService.EXTRA_PLAYLIST_KIND, selectedPlaylistKind);
                selectedPlaylistTitle = sourceTitle == null ? selectedPlaylistTitle : sourceTitle;
                selectedLocalPlaylistId = "";
                selectedLocalPlaylistTitle = "";
            } else if (sourceType == YmpPlaybackService.SOURCE_LOCAL_PLAYLIST) {
                selectedPlaylistKind = -1;
                selectedPlaylistTitle = "";
                selectedLocalPlaylistId = intent.getStringExtra(YmpPlaybackService.EXTRA_LOCAL_PLAYLIST_ID);
                if (selectedLocalPlaylistId == null) {
                    selectedLocalPlaylistId = "";
                }
                selectedLocalPlaylistTitle = sourceTitle == null ? selectedLocalPlaylistTitle : sourceTitle;
            } else if (sourceType == YmpPlaybackService.SOURCE_SEARCH) {
                selectedPlaylistKind = -1;
                selectedPlaylistTitle = sourceTitle == null ? getString(R.string.source_search) : sourceTitle;
                selectedLocalPlaylistId = "";
                selectedLocalPlaylistTitle = "";
            }
        }

        nowTitleView.setText(title == null || title.isEmpty() ? getString(R.string.now_playing_empty) : title);
        nowArtistView.setText(artist == null ? "" : artist);
        nowAlbumView.setText(album == null || album.isEmpty() ? "" : getString(R.string.album_template, album));
        loadCover(coverUrl);
        String source = sourceTitleFor(sourceType, sourceTitle);
        modeView.setText(source);
        queueView.setText(getString(
                R.string.queue_template,
                source,
                index < 0 ? 0 : index + 1,
                queue,
                playbackStateText(playing, prepared),
                queueModeText(sourceType == YmpPlaybackService.SOURCE_WAVE, playMode, shuffle)
        ));
        updateSourceButtons();
        updateTransportVisuals();
        updateStatus(statusWithCache(status == null ? "" : status));
    }

    private String playbackStateText(boolean playing, boolean prepared) {
        if (playing) {
            return getString(R.string.state_playing);
        }
        if (prepared) {
            return getString(R.string.state_paused);
        }
        return getString(R.string.state_stopped);
    }

    private String sourceTitleFor(int sourceType, String sourceTitle) {
        if (sourceType == YmpPlaybackService.SOURCE_PLAYLIST
                || sourceType == YmpPlaybackService.SOURCE_LOCAL_PLAYLIST) {
            return sourceTitle == null || sourceTitle.isEmpty()
                    ? getString(R.string.playlist_source_empty)
                    : sourceTitle;
        }
        if (sourceType == YmpPlaybackService.SOURCE_OFFLINE) {
            return getString(R.string.source_liked_cache);
        }
        if (sourceType == YmpPlaybackService.SOURCE_SEARCH) {
            return sourceTitle == null || sourceTitle.isEmpty()
                    ? getString(R.string.source_search)
                    : sourceTitle;
        }
        return getString(R.string.source_my_wave);
    }

    private String queueModeText(boolean wave, int playMode, boolean shuffle) {
        if (wave) {
            return getString(R.string.queue_mode_wave);
        }
        if (playMode == 1 || shuffle) {
            return getString(R.string.queue_mode_shuffle);
        }
        if (playMode == 2) {
            return getString(R.string.queue_mode_repeat);
        }
        return getString(R.string.queue_mode_order);
    }

    private void updateSourceButtons() {
        if (waveSourceButton != null) {
            stylePill(waveSourceButton, selectedSourceType == YmpPlaybackService.SOURCE_WAVE);
        }
        if (offlineSourceButton != null) {
            stylePill(offlineSourceButton, selectedSourceType == YmpPlaybackService.SOURCE_OFFLINE);
        }
        if (playlistSourceButton != null) {
            boolean playlistSelected = selectedSourceType == YmpPlaybackService.SOURCE_PLAYLIST
                    || selectedSourceType == YmpPlaybackService.SOURCE_LOCAL_PLAYLIST;
            String title = selectedSourceType == YmpPlaybackService.SOURCE_LOCAL_PLAYLIST
                    ? selectedLocalPlaylistTitle
                    : selectedPlaylistTitle;
            stylePill(playlistSourceButton, playlistSelected);
            playlistSourceButton.setText(title.isEmpty() ? getString(R.string.playlist_source_empty) : title);
        }
        if (modeView != null) {
            modeView.setText(sourceTitleFor(
                    selectedSourceType,
                    selectedSourceType == YmpPlaybackService.SOURCE_LOCAL_PLAYLIST
                            ? selectedLocalPlaylistTitle
                            : selectedPlaylistTitle
            ));
        }
    }

    private void updateTransportVisuals() {
        if (playPauseButton != null) {
            playPauseButton.setImageResource(currentPlaying ? R.drawable.ic_player_pause : R.drawable.ic_player_play);
            boolean active = currentPlaying || currentPrepared;
            playPauseButton.setBackground(panelBg(active ? COLOR_ACCENT : COLOR_SURFACE_2, dp(999), 0x00000000));
            playPauseButton.setColorFilter(active ? COLOR_BG : COLOR_TEXT);
        }
        if (queueModeButton != null) {
            boolean sourceIsWave = selectedSourceType == YmpPlaybackService.SOURCE_WAVE;
            queueModeButton.setEnabled(!sourceIsWave);
            queueModeButton.setAlpha(sourceIsWave ? 0.38f : 1f);
            if (sourceIsWave || currentPlayMode == 0) {
                queueModeButton.setImageResource(R.drawable.ic_player_order);
                queueModeButton.setContentDescription(getString(sourceIsWave ? R.string.queue_mode_wave : R.string.queue_mode_order));
                queueModeButton.setBackground(panelBg(COLOR_SURFACE_2, dp(999), 0x00000000));
                queueModeButton.setColorFilter(COLOR_TEXT);
            } else if (currentPlayMode == 1) {
                queueModeButton.setImageResource(R.drawable.ic_player_shuffle);
                queueModeButton.setContentDescription(getString(R.string.queue_mode_shuffle));
                queueModeButton.setBackground(panelBg(COLOR_ACCENT_2, dp(999), 0x00000000));
                queueModeButton.setColorFilter(COLOR_BG);
            } else {
                queueModeButton.setImageResource(R.drawable.ic_player_repeat);
                queueModeButton.setContentDescription(getString(R.string.queue_mode_repeat));
                queueModeButton.setBackground(panelBg(COLOR_ACCENT, dp(999), 0x00000000));
                queueModeButton.setColorFilter(COLOR_BG);
            }
        }
        if (likeButton != null) {
            likeButton.setBackground(panelBg(currentLiked ? COLOR_ACCENT : 0xff1f3b32, dp(999), 0x00000000));
            likeButton.setColorFilter(currentLiked ? COLOR_BG : 0xffbcffe8);
            likeButton.setContentDescription(getString(currentLiked ? R.string.unlike_track : R.string.like_track));
        }
        if (addToPlaylistButton != null) {
            boolean local = currentSourceType == YmpPlaybackService.SOURCE_LOCAL_PLAYLIST;
            addToPlaylistButton.setEnabled(!local);
            addToPlaylistButton.setAlpha(local ? 0.38f : 1f);
        }
    }

    private void loadCover(String coverUrl) {
        String url = coverUrl == null ? "" : coverUrl.trim();
        if (coverView == null || url.equals(latestCoverUrl)) {
            return;
        }
        latestCoverUrl = url;
        if (url.isEmpty()) {
            coverView.setImageResource(R.mipmap.ic_launcher);
            return;
        }
        Context appContext = getApplicationContext();
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                File cached = coverFile(url);
                Bitmap cachedBitmap = cached.exists() && cached.length() > 0L
                        ? BitmapFactory.decodeFile(cached.getAbsolutePath())
                        : null;
                if (cachedBitmap != null && url.equals(latestCoverUrl)) {
                    runOnUiThread(() -> coverView.setImageBitmap(cachedBitmap));
                    return;
                }
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                try (InputStream input = connection.getInputStream()) {
                    byte[] bytes = readBytes(input);
                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    writeCoverCache(cached, bytes);
                    if (bitmap != null && url.equals(latestCoverUrl)) {
                        runOnUiThread(() -> coverView.setImageBitmap(bitmap));
                    }
                }
            } catch (Exception ex) {
                Diagnostics.log(appContext, "YMP cover load failed", ex);
                if (url.equals(latestCoverUrl)) {
                    runOnUiThread(() -> coverView.setImageResource(R.mipmap.ic_launcher));
                }
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }, "YMP-Cover").start();
    }

    private File coverFile(String coverUrl) {
        File root = new File(getCacheDir(), "covers");
        return new File(root, Integer.toHexString(coverUrl.hashCode()) + ".img");
    }

    private static byte[] readBytes(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static void writeCoverCache(File file, byte[] bytes) {
        if (file == null || bytes == null || bytes.length == 0) {
            return;
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return;
        }
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(bytes);
        } catch (Exception ignored) {
        }
    }

    private void adjustMusicVolume(int direction) {
        if (direction == AudioManager.ADJUST_RAISE && Ts18AudioControls.adjustVolume(this, true)) {
            updateStatus(statusWithCache("TS18 volume up requested"));
            return;
        }
        if (direction == AudioManager.ADJUST_LOWER && Ts18AudioControls.adjustVolume(this, false)) {
            updateStatus(statusWithCache("TS18 volume down requested"));
            return;
        }
        AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audio == null) {
            updateStatus(statusWithCache("Audio service is not available"));
            return;
        }
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI);
        updateStatus(statusWithCache("Music volume changed"));
    }

    private void toggleMusicMute() {
        if (Ts18AudioControls.toggleMute(this)) {
            updateStatus(statusWithCache("TS18 mute requested"));
            return;
        }
        AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audio == null) {
            updateStatus(statusWithCache("Audio service is not available"));
            return;
        }
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI);
        updateStatus(statusWithCache("Music mute toggled"));
    }

    private void openEqualizer() {
        String packageName = YmpSettings.equalizerPackage(this).trim();
        if (!packageName.isEmpty() && launchPackage(packageName)) {
            updateStatus(statusWithCache(getString(R.string.equalizer_opened)));
            return;
        }
        showEqualizerChooser();
    }

    private void showEqualizerChooser() {
        List<EqualizerCandidate> candidates = findEqualizerCandidates();
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(COLOR_BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(16), dp(18), dp(18));
        scroll.addView(root, matchScroll());

        root.addView(sectionTitle(getString(R.string.choose_equalizer_app)), matchWrap());

        if (candidates.isEmpty()) {
            addLibraryStatusRow(root, getString(R.string.equalizer_no_apps_found));
        } else {
            for (EqualizerCandidate candidate : candidates) {
                addLibraryAction(
                        root,
                        candidate.label,
                        candidate.packageName,
                        () -> {
                            YmpSettings.setEqualizerPackage(this, candidate.packageName);
                            if (equalizerPackageEdit != null) {
                                equalizerPackageEdit.setText(candidate.packageName);
                            }
                            dialog.dismiss();
                            if (launchPackage(candidate.packageName)) {
                                updateStatus(statusWithCache(getString(R.string.equalizer_app_selected, candidate.label)));
                            } else {
                                updateStatus(statusWithCache(getString(R.string.equalizer_not_found)));
                                Toast.makeText(this, R.string.equalizer_not_found, Toast.LENGTH_SHORT).show();
                            }
                        }
                );
            }
        }

        Intent panel = new Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, currentAudioSessionId)
                .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getPackageName())
                .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC);
        if (getPackageManager().resolveActivity(panel, 0) != null) {
            Button systemPanel = smallButton(getString(R.string.equalizer_system_panel), COLOR_SURFACE_2, COLOR_TEXT);
            systemPanel.setOnClickListener(v -> {
                dialog.dismiss();
                if (tryStartSettings(panel)) {
                    updateStatus(statusWithCache(getString(R.string.equalizer_opened)));
                } else {
                    updateStatus(statusWithCache(getString(R.string.equalizer_not_found)));
                    Toast.makeText(this, R.string.equalizer_not_found, Toast.LENGTH_SHORT).show();
                }
            });
            root.addView(systemPanel, spaced());
        }

        dialog.setContentView(scroll);
        prepareDialogWindow(dialog, 620);
        dialog.show();
    }

    private List<EqualizerCandidate> findEqualizerCandidates() {
        PackageManager manager = getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> infos = manager.queryIntentActivities(launcher, 0);
        List<EqualizerCandidate> candidates = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo info : infos) {
            if (info == null || info.activityInfo == null || info.activityInfo.packageName == null) {
                continue;
            }
            String packageName = info.activityInfo.packageName;
            String label = String.valueOf(info.loadLabel(manager));
            String haystack = (label + " " + packageName).toLowerCase(Locale.US);
            if (!haystack.contains("dsp")
                    && !haystack.contains("equalizer")
                    && !haystack.contains("eq")
                    && !haystack.contains("audio")
                    && !haystack.contains("sound")
                    && !haystack.contains("fx")) {
                continue;
            }
            if (packageName.equals(getPackageName()) || !seen.add(packageName)) {
                continue;
            }
            candidates.add(new EqualizerCandidate(label, packageName));
        }
        candidates.sort((left, right) -> left.label.compareToIgnoreCase(right.label));
        return candidates;
    }

    private boolean launchPackage(String packageName) {
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(packageName);
            if (launch == null) {
                return false;
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launch);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private void toggleEmbeddedSideBar() {
        if (!YmpSettings.isEmbeddedSideBarEnabled(this)) {
            updateStatus(statusWithCache(getString(R.string.sidebar_disabled_status)));
            Toast.makeText(this, R.string.sidebar_disabled_status, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!EmbeddedSideBarService.hasOverlayPermission(this)) {
            updateStatus(statusWithCache(getString(R.string.sidebar_overlay_permission_required)));
            try {
                startActivity(EmbeddedSideBarService.overlayPermissionIntent(this));
            } catch (Exception ex) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
            return;
        }
        EmbeddedSideBarService.toggle(this);
        updateStatus(statusWithCache("SideBar toggled"));
    }

    private void openBatterySettings() {
        Intent appBattery = new Intent("android.settings.APP_BATTERY_SETTINGS")
                .putExtra("android.provider.extra.APP_PACKAGE", getPackageName());
        if (tryStartSettings(appBattery)
                || tryStartSettings(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                || tryStartSettings(new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
                || openAppDetailsSettings()) {
            updateStatus(statusWithCache("Opened battery settings"));
            return;
        }
        updateStatus(statusWithCache(getString(R.string.system_settings_open_failed)));
        Toast.makeText(this, R.string.system_settings_open_failed, Toast.LENGTH_SHORT).show();
    }

    private void openAutostartSettings() {
        Intent[] vendorIntents = new Intent[]{
                componentIntent("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                componentIntent("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity"),
                componentIntent("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                componentIntent("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                componentIntent("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
                componentIntent("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
                componentIntent("com.iqoo.secure", "com.iqoo.secure.safeguard.PurviewTabActivity"),
                componentIntent("com.asus.mobilemanager", "com.asus.mobilemanager.MainActivity"),
                componentIntent("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity")
        };
        for (Intent intent : vendorIntents) {
            if (tryStartSettings(intent)) {
                updateStatus(statusWithCache("Opened autostart settings"));
                return;
            }
        }
        if (openAppDetailsSettings()) {
            updateStatus(statusWithCache("Opened app settings"));
            Toast.makeText(this, R.string.autostart_fallback_hint, Toast.LENGTH_LONG).show();
            return;
        }
        updateStatus(statusWithCache(getString(R.string.system_settings_open_failed)));
        Toast.makeText(this, R.string.system_settings_open_failed, Toast.LENGTH_SHORT).show();
    }

    private Intent componentIntent(String pkg, String cls) {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(pkg, cls));
        intent.putExtra("package_name", getPackageName());
        intent.putExtra("extra_pkgname", getPackageName());
        intent.putExtra("packageName", getPackageName());
        return intent;
    }

    private boolean openAppDetailsSettings() {
        return tryStartSettings(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:" + getPackageName())));
    }

    private boolean tryStartSettings(Intent intent) {
        try {
            startActivity(intent);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void ensureEmbeddedSideBar(boolean showPanel) {
        if (!YmpSettings.isEmbeddedSideBarEnabled(this) || !EmbeddedSideBarService.hasOverlayPermission(this)) {
            return;
        }
        EmbeddedSideBarService.start(this, showPanel);
    }

    private void startDeviceLogin() {
        if (polling) {
            updateStatus(statusWithCache("Login is already running"));
            return;
        }
        polling = true;
        Diagnostics.log(this, "YMP device login started");
        updateStatus(statusWithCache("Requesting device code..."));
        new Thread(() -> {
            try {
                YandexMusicClient client = new YandexMusicClient("");
                YandexMusicClient.DeviceCode code = client.requestDeviceCode();
                runOnUiThread(() -> {
                    showDeviceCode(code.userCode);
                    copyTextToClipboard("YMPlayer login code", code.userCode);
                    Toast.makeText(this, R.string.login_code_copied, Toast.LENGTH_LONG).show();
                    updateStatus(statusWithCache("Login code copied: " + code.userCode + "\nOpen " + code.verificationUrl + " and enter it."));
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(code.verificationUrl)));
                    } catch (Exception ignored) {
                    }
                });

                long deadline = System.currentTimeMillis() + code.expiresInSeconds * 1000L;
                while (System.currentTimeMillis() < deadline && polling) {
                    Thread.sleep(code.intervalSeconds * 1000L);
                    YandexMusicClient.OAuthToken token = client.pollDeviceToken(code.deviceCode);
                    if (token != null) {
                        TokenStore.save(this, token.accessToken, token.refreshToken);
                        runOnUiThread(() -> {
                            if (tokenEdit != null) {
                                tokenEdit.setText(token.accessToken);
                            }
                            Diagnostics.log(this, "YMP device login complete");
                            updateStatus(statusWithCache("Login complete"));
                        });
                        polling = false;
                        return;
                    }
                    runOnUiThread(() -> updateStatus(statusWithCache("Waiting for Yandex approval...")));
                }
                Diagnostics.log(this, "YMP device login timed out");
                runOnUiThread(() -> updateStatus(statusWithCache("Login timed out")));
            } catch (Exception ex) {
                Diagnostics.log(this, "YMP device login failed", ex);
                runOnUiThread(() -> updateStatus(statusWithCache("Login failed: " + ex.getMessage())));
            } finally {
                polling = false;
            }
        }, "YMP-Login").start();
    }

    private void testAccount() {
        Diagnostics.log(this, "YMP account check started");
        updateStatus(statusWithCache("Checking account..."));
        new Thread(() -> {
            try {
                persistTypedToken();
                YandexMusicClient.AccountStatus status = new YandexMusicClient(TokenStore.getAccessToken(this)).getAccountStatus();
                Diagnostics.log(this, "YMP account check complete for uid=" + status.uid);
                runOnUiThread(() -> updateStatus(statusWithCache("Signed in: " + status.name + " (" + status.uid + ")")));
            } catch (Exception ex) {
                Diagnostics.log(this, "YMP account check failed", ex);
                runOnUiThread(() -> updateStatus(statusWithCache("Account check failed: " + ex.getMessage())));
            }
        }, "YMP-AccountTest").start();
    }

    private void startFavoritesCacheSync() {
        persistTypedToken();
        if (TokenStore.getAccessToken(this).trim().isEmpty()) {
            Diagnostics.log(this, "YMP cache sync blocked: no token");
            updateStatus(statusWithCache("Sign in before cache sync"));
            return;
        }
        if (CacheSyncService.isRunning()) {
            Diagnostics.log(this, "YMP cache sync blocked: already running");
            updateStatus(statusWithCache("Cache sync is already running"));
            return;
        }
        saveCacheSettings();
        boolean wifiOnly = wifiOnlyBox != null ? wifiOnlyBox.isChecked() : CacheSettings.isWifiOnly(this);
        boolean chargingOnly = chargingOnlyBox != null ? chargingOnlyBox.isChecked() : CacheSettings.isChargingOnly(this);
        Diagnostics.log(this, "YMP favorite cache sync requested"
                + ", wifiOnly=" + wifiOnly
                + ", chargingOnly=" + chargingOnly);
        Intent intent = new Intent(this, CacheSyncService.class);
        intent.setAction(CacheSyncService.ACTION_SYNC);
        intent.putExtra(CacheSyncService.EXTRA_INCLUDE_LIKED, true);
        intent.putExtra(CacheSyncService.EXTRA_INCLUDE_PLAYLISTS, false);
        intent.putExtra(CacheSyncService.EXTRA_WIFI_ONLY, wifiOnly);
        intent.putExtra(CacheSyncService.EXTRA_CHARGING_ONLY, chargingOnly);
        startForegroundService(intent);
        updateStatus(statusWithCache("Starting cache sync service..."));
    }

    private void cancelCacheSync() {
        Intent intent = new Intent(this, CacheSyncService.class);
        intent.setAction(CacheSyncService.ACTION_CANCEL);
        startService(intent);
        Diagnostics.log(this, "YMP cache sync cancel requested");
        updateStatus(statusWithCache("Stopping cache sync after current track..."));
    }

    private void saveCacheSettings() {
        if (wifiOnlyBox != null && chargingOnlyBox != null) {
            CacheSettings.save(this, wifiOnlyBox.isChecked(), chargingOnlyBox.isChecked());
            Diagnostics.log(this, "YMP cache settings saved: wifiOnly=" + wifiOnlyBox.isChecked()
                    + ", chargingOnly=" + chargingOnlyBox.isChecked());
        }
    }

    private void clearLocalCache() {
        Diagnostics.log(this, "YMP local cache clear requested");
        updateStatus(statusWithCache("Clearing local cache..."));
        Context appContext = getApplicationContext();
        new Thread(() -> {
            String result = new YmpRepository(appContext).clearLocalCache();
            Diagnostics.log(appContext, result);
            runOnUiThread(() -> updateStatus(statusWithCache(result)));
        }, "YMP-ClearLocalCache").start();
    }

    private void copyDiagnostics() {
        String diagnostics = Diagnostics.snapshot(this);
        copyTextToClipboard("YMPlayer diagnostics", diagnostics);
        Toast.makeText(this, R.string.diagnostics_copied, Toast.LENGTH_SHORT).show();
        updateStatus(statusWithCache("Diagnostics copied to clipboard"));
    }

    private void copyLoginCode() {
        if (latestDeviceCode == null || latestDeviceCode.isEmpty()) {
            updateStatus(statusWithCache("Start Yandex login first"));
            return;
        }
        copyTextToClipboard("YMPlayer login code", latestDeviceCode);
        Toast.makeText(this, R.string.login_code_copied, Toast.LENGTH_SHORT).show();
        updateStatus(statusWithCache("Login code copied: " + latestDeviceCode));
    }

    private void copyTextToClipboard(String label, String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text == null ? "" : text));
        }
    }

    private void clearDiagnostics() {
        Diagnostics.clear(this);
        Toast.makeText(this, R.string.diagnostics_cleared, Toast.LENGTH_SHORT).show();
        updateStatus(statusWithCache("Diagnostics cleared"));
    }

    private void persistTypedToken() {
        if (tokenEdit == null) {
            return;
        }
        String token = tokenEdit.getText().toString().trim();
        if (!token.isEmpty()) {
            TokenStore.save(this, token, null);
        }
    }

    private void setTokenVisible(boolean visible) {
        if (tokenEdit == null) {
            return;
        }
        tokenEdit.setTransformationMethod(visible ? null : PasswordTransformationMethod.getInstance());
        Selection.setSelection(tokenEdit.getText(), tokenEdit.getText().length());
    }

    private void showDeviceCode(String code) {
        latestDeviceCode = code == null ? "" : code;
        if (loginCodeView != null) {
            loginCodeView.setText(latestDeviceCode.isEmpty()
                    ? getString(R.string.login_code_empty)
                    : getString(R.string.login_code_template, latestDeviceCode));
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 100);
        }
    }

    private void updateStatus(String text) {
        if (statusView != null) {
            statusView.setText(text);
        }
    }

    private String statusWithCache(String primary) {
        refreshCacheStatusAsync();
        return (primary == null ? "" : primary) + "\n\n" + cachedCacheStatus;
    }

    private void refreshCacheStatusAsync() {
        if (cacheStatusLoading) {
            return;
        }
        cacheStatusLoading = true;
        new Thread(() -> {
            String value;
            try {
                value = new YmpRepository(this).cacheStatusText();
            } catch (Exception ex) {
                value = "Cache status unavailable: " + ex.getMessage();
            }
            cachedCacheStatus = value;
            cacheStatusLoading = false;
        }, "YMP-CacheStatus").start();
    }

    private boolean isWideLayout() {
        Configuration config = getResources().getConfiguration();
        return config.orientation == Configuration.ORIENTATION_LANDSCAPE || config.screenWidthDp >= 720;
    }

    private int coverSize(boolean wide) {
        Configuration config = getResources().getConfiguration();
        int minDp = Math.min(config.screenWidthDp, config.screenHeightDp);
        int sizeDp = wide ? Math.min(320, Math.max(210, minDp - 56)) : Math.min(360, Math.max(220, config.screenWidthDp - 72));
        return dp(sizeDp);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private TextView addSection(LinearLayout root, int titleRes) {
        TextView section = sectionTitle(getString(titleRes));
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, dp(18), 0, dp(8));
        root.addView(section, params);
        return section;
    }

    private TextView sectionTitle(String text) {
        TextView section = new TextView(this);
        section.setText(text);
        section.setTextColor(COLOR_TEXT);
        section.setTextSize(18);
        section.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return section;
    }

    private Button addButton(LinearLayout root, int titleRes, View.OnClickListener listener) {
        Button button = pillButton(getString(titleRes), COLOR_SURFACE_2, COLOR_TEXT);
        button.setOnClickListener(listener);
        root.addView(button, spaced());
        return button;
    }

    private void addActionButton(LinearLayout root, int titleRes, int bgColor, int textColor, View.OnClickListener listener) {
        Button button = pillButton(getString(titleRes), bgColor, textColor);
        button.setOnClickListener(listener);
        root.addView(button, rowButtonParams());
    }

    private ImageButton transportButton(int iconRes, int bgColor, int tintColor, int sizePx, String description) {
        ImageButton button = smallIconButton(iconRes, bgColor, tintColor, sizePx, description);
        button.setPadding(dp(15), dp(15), dp(15), dp(15));
        return button;
    }

    private ImageButton smallIconButton(int iconRes, int bgColor, int tintColor, int sizePx, String description) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(iconRes);
        button.setColorFilter(tintColor);
        button.setContentDescription(description);
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setBackground(panelBg(bgColor, dp(999), 0x00000000));
        button.setPadding(dp(10), dp(10), dp(10), dp(10));
        button.setMinimumWidth(sizePx);
        button.setMinimumHeight(sizePx);
        return button;
    }

    private void stylePill(Button button, boolean selected) {
        if (button == null) {
            return;
        }
        button.setTextColor(selected ? COLOR_BG : COLOR_TEXT);
        button.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        button.setBackground(panelBg(selected ? COLOR_ACCENT : COLOR_SURFACE_2, dp(13), selected ? COLOR_ACCENT : COLOR_STROKE));
    }

    private Button pillButton(String text, int bgColor, int textColor) {
        Button button = smallButton(text, bgColor, textColor);
        button.setMinHeight(dp(48));
        button.setPadding(dp(8), 0, dp(8), 0);
        return button;
    }

    private Button controlButton(String text, int bgColor, int textColor, int heightDp) {
        Button button = smallButton(text, bgColor, textColor);
        button.setTextSize(17);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setMinHeight(dp(heightDp));
        return button;
    }

    private Button smallButton(String text, int bgColor, int textColor) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(textColor);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setBackground(panelBg(bgColor, dp(13), 0x00000000));
        button.setMinHeight(dp(44));
        button.setPadding(dp(10), 0, dp(10), 0);
        return button;
    }

    private CheckBox checkbox(int titleRes) {
        CheckBox box = new CheckBox(this);
        box.setText(titleRes);
        box.setTextColor(COLOR_TEXT);
        box.setTextSize(15);
        box.setButtonTintList(android.content.res.ColorStateList.valueOf(COLOR_ACCENT));
        return box;
    }

    private GradientDrawable panelBg(int color, int radius, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeColor != 0) {
            drawable.setStroke(dp(1), strokeColor);
        }
        return drawable;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private LinearLayout.LayoutParams rowButtonParams() {
        return rowButtonParams(1f);
    }

    private LinearLayout.LayoutParams rowButtonParams(float weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight);
        params.setMargins(dp(3), 0, dp(3), 0);
        return params;
    }

    private LinearLayout.LayoutParams compactButtonParams(int width) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(8), 0, 0, 0);
        return params;
    }

    private static FrameLayout.LayoutParams matchFrame() {
        return new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
    }

    private static ScrollView.LayoutParams matchScroll() {
        return new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        );
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams spaced() {
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, 0, 0, dp(7));
        return params;
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

    private static final class EqualizerCandidate {
        final String label;
        final String packageName;

        EqualizerCandidate(String label, String packageName) {
            this.label = label == null || label.trim().isEmpty() ? packageName : label.trim();
            this.packageName = packageName == null ? "" : packageName.trim();
        }
    }
}
