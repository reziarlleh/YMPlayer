package dev.petrov.yaplay.player;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import dev.petrov.yaplay.Diagnostics;

public final class YmpPowerMenuHelper {
    private static final String ACTION_REQUEST_SHUTDOWN = "android.intent.action.ACTION_REQUEST_SHUTDOWN";
    private static final String EXTRA_KEY_CONFIRM = "android.intent.extra.KEY_CONFIRM";
    private static final ComponentName TS18_REBOOT_ACTIVITY = new ComponentName(
            "com.android.launcher",
            "com.nwd.tools.reboot.RebootActivity"
    );

    private YmpPowerMenuHelper() {
    }

    public static boolean isAvailable(Context context) {
        return true;
    }

    public static boolean isEnabled(Context context) {
        return YmpAccessibilityService.isEnabled(context);
    }

    public static boolean requestPowerDialog(Context context) {
        Context appContext = context.getApplicationContext();
        if (YmpAccessibilityService.requestPowerDialog(appContext)) {
            return true;
        }
        if (tryStartTs18RebootActivity(appContext)) {
            return true;
        }
        return tryStartAndroidShutdownDialog(appContext);
    }

    private static boolean tryStartTs18RebootActivity(Context context) {
        Intent intent = new Intent()
                .setComponent(TS18_REBOOT_ACTIVITY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return tryStartActivity(context, intent, "TS18 launcher reboot activity");
    }

    private static boolean tryStartAndroidShutdownDialog(Context context) {
        Intent intent = new Intent(ACTION_REQUEST_SHUTDOWN)
                .putExtra(EXTRA_KEY_CONFIRM, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return tryStartActivity(context, intent, "Android shutdown request dialog");
    }

    private static boolean tryStartActivity(Context context, Intent intent, String label) {
        try {
            context.startActivity(intent);
            Diagnostics.log(context, "YMP power menu opened via " + label);
            return true;
        } catch (ActivityNotFoundException ex) {
            Diagnostics.log(context, "YMP power menu path not found: " + label);
        } catch (SecurityException ex) {
            Diagnostics.log(context, "YMP power menu path blocked by security: " + label, ex);
        } catch (Exception ex) {
            Diagnostics.log(context, "YMP power menu path failed: " + label, ex);
        }
        return false;
    }
}
