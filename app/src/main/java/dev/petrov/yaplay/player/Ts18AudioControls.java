package dev.petrov.yaplay.player;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

import dev.petrov.yaplay.Diagnostics;

public final class Ts18AudioControls {
    private static final String ACTION_KEY_VALUE = "com.nwd.action.ACTION_KEY_VALUE";
    private static final String ACTION_REQUEST_VOLUME_DISPLAY = "com.nwd.ACTION_REQUEST_VLOUME_DISPLAY";
    private static final String ACTION_PLATFORM_SEND_CAN_VOLUME = "com.nwd.can.action.ACTION_PLATFORM_SEND_CAN_VOLUME";

    private static final String EXTRA_KEY_VALUE = "extra_key_value";
    private static final String EXTRA_SET_VOLUME = "extra_set_volume";
    private static final String EXTRA_SET_VOLUME_VALUE = "extra_set_volume_value";

    private static final String SETTING_CAN_USE_AMP_VOLUME = "can_use_amp_volume_key";
    private static final String SETTING_MUTE_STATE = "mcu_mute_state";

    private static final int KEY_SLEEP = 0;
    private static final int KEY_MUTE = 2;
    private static final int KEY_VOLUME_UP = 14;
    private static final int KEY_VOLUME_DOWN = 15;
    private static final int KEY_BACK = 18;
    private static final int KEY_HOME = 20;

    private Ts18AudioControls() {
    }

    public static boolean adjustVolume(Context context, boolean up) {
        Context appContext = context.getApplicationContext();
        if (!isLikelyTs18(appContext)) {
            return false;
        }
        int delta = up ? 1 : -1;
        if (isCanVolumeEnabled(appContext)) {
            sendCanVolume(appContext, delta, 0);
        } else {
            sendKeyValue(appContext, up ? KEY_VOLUME_UP : KEY_VOLUME_DOWN);
        }
        requestVolumeDisplay(appContext);
        Diagnostics.log(appContext, "YMP TS18 volume " + (up ? "up" : "down") + " requested");
        return true;
    }

    public static boolean toggleMute(Context context) {
        Context appContext = context.getApplicationContext();
        if (!isLikelyTs18(appContext)) {
            return false;
        }
        if (isCanVolumeEnabled(appContext)) {
            sendCanVolume(appContext, 0, 0);
        } else {
            sendKeyValue(appContext, KEY_MUTE);
        }
        requestVolumeDisplay(appContext);
        Diagnostics.log(appContext, "YMP TS18 mute toggle requested");
        return true;
    }

    public static boolean back(Context context) {
        return sendTs18Key(context, KEY_BACK, "back");
    }

    public static boolean sleep(Context context) {
        return sendTs18Key(context, KEY_SLEEP, "sleep");
    }

    public static boolean home(Context context) {
        Context appContext = context.getApplicationContext();
        boolean sent = sendTs18Key(appContext, KEY_HOME, "home");
        Intent intent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            appContext.startActivity(intent);
        } catch (Exception ignored) {
        }
        return sent;
    }

    private static boolean sendTs18Key(Context context, int key, String label) {
        Context appContext = context.getApplicationContext();
        if (!isLikelyTs18(appContext)) {
            Diagnostics.log(appContext, "YMP TS18 " + label + " skipped: TS18 environment not detected");
            return false;
        }
        sendKeyValue(appContext, key);
        Diagnostics.log(appContext, "YMP TS18 " + label + " requested");
        return true;
    }

    private static boolean isLikelyTs18(Context context) {
        return SideBarHelper.isInstalled(context)
                || hasSystemSetting(context, SETTING_CAN_USE_AMP_VOLUME)
                || hasSystemSetting(context, SETTING_MUTE_STATE)
                || isPackageInstalled(context, "com.launcher");
    }

    private static boolean isCanVolumeEnabled(Context context) {
        return readSystemInt(context, SETTING_CAN_USE_AMP_VOLUME, 0) == 1;
    }

    private static boolean hasSystemSetting(Context context, String key) {
        try {
            Settings.System.getInt(context.getContentResolver(), key);
            return true;
        } catch (Settings.SettingNotFoundException ex) {
            return false;
        } catch (Exception ex) {
            return false;
        }
    }

    private static int readSystemInt(Context context, String key, int defaultValue) {
        try {
            return Settings.System.getInt(context.getContentResolver(), key, defaultValue);
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private static boolean isPackageInstalled(Context context, String packageName) {
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private static void sendKeyValue(Context context, int keyValue) {
        Intent intent = new Intent(ACTION_KEY_VALUE)
                .putExtra(EXTRA_KEY_VALUE, (byte) keyValue)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        context.sendBroadcast(intent);
    }

    private static void sendCanVolume(Context context, int type, int volume) {
        Intent intent = new Intent(ACTION_PLATFORM_SEND_CAN_VOLUME)
                .putExtra(EXTRA_SET_VOLUME, type)
                .putExtra(EXTRA_SET_VOLUME_VALUE, volume)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        context.sendBroadcast(intent);
    }

    private static void requestVolumeDisplay(Context context) {
        Intent intent = new Intent(ACTION_REQUEST_VOLUME_DISPLAY)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        context.sendBroadcast(intent);
    }
}
