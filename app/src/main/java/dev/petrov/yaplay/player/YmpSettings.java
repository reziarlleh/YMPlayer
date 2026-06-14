package dev.petrov.yaplay.player;

import android.content.Context;
import android.content.SharedPreferences;

public final class YmpSettings {
    private static final String PREFS = "ymplayer_settings";
    private static final String KEY_SIDEBAR_WATCHDOG = "sidebar_watchdog";
    private static final String KEY_AUTO_CACHE_LIKED = "auto_cache_liked";
    private static final String KEY_EQUALIZER_PACKAGE = "equalizer_package";

    private YmpSettings() {
    }

    public static boolean isSidebarWatchdogEnabled(Context context) {
        return prefs(context).getBoolean(KEY_SIDEBAR_WATCHDOG, false);
    }

    public static boolean isEmbeddedSideBarEnabled(Context context) {
        return isSidebarWatchdogEnabled(context);
    }

    public static void setSidebarWatchdogEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_SIDEBAR_WATCHDOG, enabled).apply();
    }

    public static void setEmbeddedSideBarEnabled(Context context, boolean enabled) {
        setSidebarWatchdogEnabled(context, enabled);
    }

    public static boolean isAutoCacheLikedEnabled(Context context) {
        return prefs(context).getBoolean(KEY_AUTO_CACHE_LIKED, true);
    }

    public static void setAutoCacheLikedEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_AUTO_CACHE_LIKED, enabled).apply();
    }

    public static String equalizerPackage(Context context) {
        return prefs(context).getString(KEY_EQUALIZER_PACKAGE, "");
    }

    public static void setEqualizerPackage(Context context, String packageName) {
        prefs(context).edit().putString(KEY_EQUALIZER_PACKAGE, packageName == null ? "" : packageName.trim()).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
