package dev.petrov.yaplay;

import android.content.Context;
import android.content.SharedPreferences;

public final class CacheSettings {
    private static final String PREFS = "cache_settings";
    private static final String KEY_WIFI_ONLY = "wifi_only";
    private static final String KEY_CHARGING_ONLY = "charging_only";

    private CacheSettings() {
    }

    public static boolean isWifiOnly(Context context) {
        return prefs(context).getBoolean(KEY_WIFI_ONLY, true);
    }

    public static boolean isChargingOnly(Context context) {
        return prefs(context).getBoolean(KEY_CHARGING_ONLY, false);
    }

    public static void save(Context context, boolean wifiOnly, boolean chargingOnly) {
        prefs(context).edit()
                .putBoolean(KEY_WIFI_ONLY, wifiOnly)
                .putBoolean(KEY_CHARGING_ONLY, chargingOnly)
                .apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
