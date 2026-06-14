package dev.petrov.yaplay.ymusic;

import android.content.Context;
import android.content.SharedPreferences;

public final class TokenStore {
    private static final String PREFS = "yaplay_auth";
    private static final String ACCESS_TOKEN = "access_token";
    private static final String REFRESH_TOKEN = "refresh_token";

    private TokenStore() {
    }

    public static String getAccessToken(Context context) {
        return prefs(context).getString(ACCESS_TOKEN, "");
    }

    public static String getRefreshToken(Context context) {
        return prefs(context).getString(REFRESH_TOKEN, "");
    }

    public static void save(Context context, String accessToken, String refreshToken) {
        SharedPreferences.Editor editor = prefs(context).edit().putString(ACCESS_TOKEN, accessToken == null ? "" : accessToken);
        if (refreshToken != null && !refreshToken.isEmpty()) {
            editor.putString(REFRESH_TOKEN, refreshToken);
        }
        editor.apply();
    }

    public static void clear(Context context) {
        prefs(context).edit().clear().apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
