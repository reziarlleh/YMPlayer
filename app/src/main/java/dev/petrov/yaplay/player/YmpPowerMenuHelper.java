package dev.petrov.yaplay.player;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import dev.petrov.yaplay.Diagnostics;

public final class YmpPowerMenuHelper {
    private static final String ACTION_REQUEST_SHUTDOWN = "android.intent.action.ACTION_REQUEST_SHUTDOWN";
    private static final String ACTION_TS18_REQUEST_START_ACTIVITY = "com.nwd.ACTION_REQUEST_START_ACTIVITY";
    private static final String ACTION_TS18_START_ACTIVITY = "com.nwd.action.ACTION_START_ACTIVITY";
    private static final String ACTION_TS18_START_NWD_ACTIVITY = "com.nwd.action.ACTION_START_NWD_ACTIVITY";
    private static final String EXTRA_KEY_CONFIRM = "android.intent.extra.KEY_CONFIRM";
    private static final String EXTRA_TS18_PACKAGE_NAME = "extra_package_name";
    private static final String EXTRA_TS18_CLASS_NAME = "extra_class_name";
    private static final String TS18_LAUNCHER_PACKAGE = "com.android.launcher";
    private static final String TS18_TOOLALLINONE_PACKAGE = "com.nwd.toolallinone.app";
    private static final String TS18_REBOOT_ACTIVITY_CLASS = "com.nwd.tools.reboot.RebootActivity";
    private static final ComponentName[] TS18_REBOOT_ACTIVITIES = {
            new ComponentName(TS18_LAUNCHER_PACKAGE, TS18_REBOOT_ACTIVITY_CLASS),
            new ComponentName(TS18_TOOLALLINONE_PACKAGE, TS18_REBOOT_ACTIVITY_CLASS)
    };
    private static final String[] TS18_START_ACTIVITY_ACTIONS = {
            ACTION_TS18_REQUEST_START_ACTIVITY,
            ACTION_TS18_START_ACTIVITY,
            ACTION_TS18_START_NWD_ACTIVITY
    };

    private YmpPowerMenuHelper() {
    }

    public static boolean isAvailable(Context context) {
        return true;
    }

    public static boolean requestShutdown(Context context) {
        Context appContext = context.getApplicationContext();
        return tryStartAndroidShutdownDialog(appContext);
    }

    public static boolean requestReboot(Context context) {
        Context appContext = context.getApplicationContext();
        if (tryStartTs18RebootActivity(appContext)) {
            return true;
        }
        return trySendTs18LauncherStartRequest(appContext);
    }

    private static boolean tryStartTs18RebootActivity(Context context) {
        for (ComponentName component : TS18_REBOOT_ACTIVITIES) {
            Intent intent = new Intent()
                    .setComponent(component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (tryStartActivity(context, intent, "TS18 reboot activity " + component.flattenToShortString())) {
                return true;
            }
        }
        return false;
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

    private static boolean trySendTs18LauncherStartRequest(Context context) {
        boolean sentAny = false;
        for (ComponentName component : TS18_REBOOT_ACTIVITIES) {
            for (String action : TS18_START_ACTIVITY_ACTIONS) {
                Intent intent = new Intent(action)
                        .setPackage(TS18_LAUNCHER_PACKAGE)
                        .putExtra(EXTRA_TS18_PACKAGE_NAME, component.getPackageName())
                        .putExtra(EXTRA_TS18_CLASS_NAME, component.getClassName())
                        .addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
                sentAny |= trySendBroadcast(
                        context,
                        intent,
                        "TS18 launcher start request " + action + " -> " + component.flattenToShortString()
                );
            }
        }
        return sentAny;
    }

    private static boolean trySendBroadcast(Context context, Intent intent, String label) {
        try {
            context.sendBroadcast(intent);
            Diagnostics.log(context, "YMP power menu request sent via " + label);
            return true;
        } catch (SecurityException ex) {
            Diagnostics.log(context, "YMP power menu broadcast blocked by security: " + label, ex);
        } catch (Exception ex) {
            Diagnostics.log(context, "YMP power menu broadcast failed: " + label, ex);
        }
        return false;
    }
}
