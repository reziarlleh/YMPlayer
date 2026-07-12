package dev.petrov.yaplay.player;

import android.content.Context;
import android.content.SharedPreferences;

public final class YmpSettings {
    private static final String PREFS = "ymplayer_settings";
    private static final String KEY_SIDEBAR_WATCHDOG = "sidebar_watchdog";
    private static final String KEY_AUTO_CACHE_LIKED = "auto_cache_liked";
    private static final String KEY_EQUALIZER_PACKAGE = "equalizer_package";
    private static final String KEY_STREAM_QUALITY = "stream_quality";
    private static final String KEY_CACHE_QUALITY = "cache_quality";
    private static final String KEY_SIDEBAR_AUTO_HIDE = "sidebar_auto_hide";
    private static final String KEY_CLIP_SYSTEM_BARS_AUTO_HIDE = "clip_system_bars_auto_hide";

    public static final String QUALITY_AUTO = "auto";
    public static final String QUALITY_ECONOMY = "economy";
    public static final String QUALITY_STANDARD = "standard";
    public static final String QUALITY_HIGH = "high";
    public static final String QUALITY_MAX = "max";

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

    public static boolean isEmbeddedSideBarAutoHideEnabled(Context context) {
        return prefs(context).getBoolean(KEY_SIDEBAR_AUTO_HIDE, true);
    }

    public static void setEmbeddedSideBarAutoHideEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_SIDEBAR_AUTO_HIDE, enabled).apply();
    }

    public static boolean isClipSystemBarsAutoHideEnabled(Context context) {
        return prefs(context).getBoolean(KEY_CLIP_SYSTEM_BARS_AUTO_HIDE, true);
    }

    public static void setClipSystemBarsAutoHideEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_CLIP_SYSTEM_BARS_AUTO_HIDE, enabled).apply();
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

    public static String streamQuality(Context context) {
        return normalizeQuality(prefs(context).getString(KEY_STREAM_QUALITY, QUALITY_AUTO));
    }

    public static void setStreamQuality(Context context, String quality) {
        prefs(context).edit().putString(KEY_STREAM_QUALITY, normalizeQuality(quality)).apply();
    }

    public static String cacheQuality(Context context) {
        return normalizeQuality(prefs(context).getString(KEY_CACHE_QUALITY, QUALITY_AUTO));
    }

    public static void setCacheQuality(Context context, String quality) {
        prefs(context).edit().putString(KEY_CACHE_QUALITY, normalizeQuality(quality)).apply();
    }

    public static String normalizeQuality(String quality) {
        String value = quality == null ? "" : quality.trim().toLowerCase(java.util.Locale.US);
        if (QUALITY_ECONOMY.equals(value)
                || QUALITY_STANDARD.equals(value)
                || QUALITY_HIGH.equals(value)
                || QUALITY_MAX.equals(value)) {
            return value;
        }
        return QUALITY_AUTO;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
