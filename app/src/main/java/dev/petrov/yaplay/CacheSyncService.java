package dev.petrov.yaplay;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;

import dev.petrov.yaplay.player.YmpRepository;

public class CacheSyncService extends Service {
    public static final String ACTION_SYNC = "dev.petrov.yaplay.action.CACHE_SYNC";
    public static final String ACTION_CANCEL = "dev.petrov.yaplay.action.CACHE_CANCEL";
    public static final String ACTION_STATUS = "dev.petrov.yaplay.action.CACHE_STATUS";
    public static final String EXTRA_INCLUDE_LIKED = "include_liked";
    public static final String EXTRA_INCLUDE_PLAYLISTS = "include_playlists";
    public static final String EXTRA_WIFI_ONLY = "wifi_only";
    public static final String EXTRA_CHARGING_ONLY = "charging_only";
    public static final String EXTRA_STATUS = "status";

    private static final String CHANNEL_ID = "cache_sync";
    private static final int NOTIFICATION_ID = 1001;

    private static volatile boolean running;
    private static volatile boolean cancelRequested;
    private static volatile String lastStatus = "Cache idle";

    private Thread worker;

    @Override
    @SuppressLint("ForegroundServiceType")
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_SYNC : intent.getAction();
        if (ACTION_CANCEL.equals(action)) {
            if (running) {
                cancelRequested = true;
                Diagnostics.log(this, "Cache service cancel accepted");
                updateStatus("Stopping cache sync after current track...");
            } else {
                Diagnostics.log(this, "Cache service cancel ignored: not running");
                broadcastStatus("No cache sync is running");
                stopSelf(startId);
            }
            return START_NOT_STICKY;
        }

        boolean includeLiked = intent == null || intent.getBooleanExtra(EXTRA_INCLUDE_LIKED, true);
        boolean includePlaylists = intent != null && intent.getBooleanExtra(EXTRA_INCLUDE_PLAYLISTS, false);
        boolean wifiOnly = intent != null && intent.getBooleanExtra(EXTRA_WIFI_ONLY, true);
        boolean chargingOnly = intent != null && intent.getBooleanExtra(EXTRA_CHARGING_ONLY, false);

        if (running) {
            Diagnostics.log(this, "Cache service start ignored: already running");
            updateStatus("Cache sync is already running");
            return START_NOT_STICKY;
        }

        Diagnostics.log(this, "Cache service starting: liked=" + includeLiked + ", playlists=" + includePlaylists
                + ", wifiOnly=" + wifiOnly + ", chargingOnly=" + chargingOnly);
        createChannel();
        startForeground(NOTIFICATION_ID, notification("Starting cache sync..."));

        if (wifiOnly && !isOnWifi()) {
            Diagnostics.log(this, "Cache service skipped: Wi-Fi required");
            finishWithoutWork("Cache sync skipped: Wi-Fi is required");
            return START_NOT_STICKY;
        }
        if (chargingOnly && !isCharging()) {
            Diagnostics.log(this, "Cache service skipped: charger required");
            finishWithoutWork("Cache sync skipped: charger is required");
            return START_NOT_STICKY;
        }

        running = true;
        cancelRequested = false;
        worker = new Thread(() -> runSync(includeLiked, includePlaylists), "YMP-CacheSyncService");
        worker.start();
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static boolean isRunning() {
        return running;
    }

    public static String lastStatus() {
        return lastStatus;
    }

    private void runSync(boolean includeLiked, boolean includePlaylists) {
        YmpRepository repository = new YmpRepository(this);
        try {
            if (!includeLiked) {
                updateStatus("Favorite cache sync skipped: liked tracks are disabled");
                return;
            }
            if (includePlaylists) {
                Diagnostics.log(this, "Cache service ignored playlist caching request: permanent cache is liked-only");
            }
            YmpRepository.CacheSyncResult result = repository.syncFavoriteCache(new YmpRepository.CacheProgress() {
                @Override
                public void onProgress(String message) {
                    updateStatus(message);
                }

                @Override
                public boolean isCancelled() {
                    return cancelRequested;
                }
            });
            Diagnostics.log(this, result.summaryText());
            updateStatus(result.summaryText() + "\n" + repository.cacheStatusText() + "\nOpen downloaded favorites in YMPlayer to play offline.");
        } catch (Exception ex) {
            Diagnostics.log(this, "Cache sync failed", ex);
            updateStatus("Cache sync failed: " + ex.getMessage());
        } finally {
            running = false;
            cancelRequested = false;
            worker = null;
            stopForegroundCompat();
            stopSelf();
        }
    }

    private void finishWithoutWork(String message) {
        updateStatus(message);
        running = false;
        cancelRequested = false;
        stopForegroundCompat();
        stopSelf();
    }

    private void updateStatus(String message) {
        lastStatus = message == null ? "" : message;
        Diagnostics.log(this, "Cache status: " + firstLine(lastStatus));
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null && canPostNotifications()) {
            manager.notify(NOTIFICATION_ID, notification(firstLine(lastStatus)));
        }
        broadcastStatus(lastStatus);
    }

    private void broadcastStatus(String message) {
        lastStatus = message == null ? "" : message;
        Intent status = new Intent(ACTION_STATUS);
        status.setPackage(getPackageName());
        status.putExtra(EXTRA_STATUS, lastStatus);
        sendBroadcast(status);
    }

    private Notification notification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent content = PendingIntent.getActivity(
                this,
                0,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag()
        );

        Intent cancel = new Intent(this, CacheSyncService.class);
        cancel.setAction(ACTION_CANCEL);
        PendingIntent cancelIntent = PendingIntent.getService(
                this,
                1,
                cancel,
                PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag()
        );

        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID);
        builder.setSmallIcon(R.drawable.ic_stat_yaplay)
                .setContentTitle(getString(R.string.cache_notification_title))
                .setContentText(text)
                .setContentIntent(content)
                .setOngoing(running)
                .setOnlyAlertOnce(true)
                .addAction(R.drawable.ic_stat_yaplay, getString(R.string.cancel_cache_sync), cancelIntent);
        builder.setCategory(Notification.CATEGORY_PROGRESS);
        return builder.build();
    }

    private void createChannel() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.cache_notification_channel),
                NotificationManager.IMPORTANCE_LOW
        );
        manager.createNotificationChannel(channel);
    }

    private boolean isOnWifi() {
        ConnectivityManager manager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) {
            return false;
        }
        Network network = manager.getActiveNetwork();
        if (network == null) {
            return false;
        }
        NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
        return capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    private boolean isCharging() {
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery == null) {
            return false;
        }
        int plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        return plugged == BatteryManager.BATTERY_PLUGGED_AC
                || plugged == BatteryManager.BATTERY_PLUGGED_USB
                || plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS;
    }

    private void stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE);
    }

    private static int immutableFlag() {
        return PendingIntent.FLAG_IMMUTABLE;
    }

    private boolean canPostNotifications() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private static String firstLine(String text) {
        if (text == null) {
            return "";
        }
        int newline = text.indexOf('\n');
        return newline >= 0 ? text.substring(0, newline) : text;
    }
}
