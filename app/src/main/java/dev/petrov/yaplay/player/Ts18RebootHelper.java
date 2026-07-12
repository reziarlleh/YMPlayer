package dev.petrov.yaplay.player;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import dev.petrov.yaplay.Diagnostics;

/** Keeps the TS18 reboot path separate from the SideBar sleep command. */
public final class Ts18RebootHelper {
    private static final String ACTION_REQUEST_START_ACTIVITY = "com.nwd.ACTION_REQUEST_START_ACTIVITY";
    private static final String ACTION_START_ACTIVITY = "com.nwd.action.ACTION_START_ACTIVITY";
    private static final String ACTION_START_NWD_ACTIVITY = "com.nwd.action.ACTION_START_NWD_ACTIVITY";
    private static final String EXTRA_PACKAGE_NAME = "extra_package_name";
    private static final String EXTRA_CLASS_NAME = "extra_class_name";
    private static final String LAUNCHER_PACKAGE = "com.android.launcher";
    private static final String TOOLALLINONE_PACKAGE = "com.nwd.toolallinone.app";
    private static final String REBOOT_ACTIVITY_CLASS = "com.nwd.tools.reboot.RebootActivity";

    private static final ComponentName[] REBOOT_ACTIVITIES = {
            new ComponentName(LAUNCHER_PACKAGE, REBOOT_ACTIVITY_CLASS),
            new ComponentName(TOOLALLINONE_PACKAGE, REBOOT_ACTIVITY_CLASS)
    };
    private static final String[] START_ACTIVITY_ACTIONS = {
            ACTION_REQUEST_START_ACTIVITY,
            ACTION_START_ACTIVITY,
            ACTION_START_NWD_ACTIVITY
    };

    private Ts18RebootHelper() {
    }

    public static boolean requestReboot(Context context) {
        Context appContext = context.getApplicationContext();
        if (tryStartRebootActivity(appContext)) {
            return true;
        }
        return trySendLauncherStartRequest(appContext);
    }

    private static boolean tryStartRebootActivity(Context context) {
        for (ComponentName component : REBOOT_ACTIVITIES) {
            Intent intent = new Intent()
                    .setComponent(component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (tryStartActivity(context, intent, component)) {
                return true;
            }
        }
        return false;
    }

    private static boolean trySendLauncherStartRequest(Context context) {
        boolean sentAny = false;
        for (ComponentName component : REBOOT_ACTIVITIES) {
            for (String action : START_ACTIVITY_ACTIONS) {
                Intent intent = new Intent(action)
                        .setPackage(LAUNCHER_PACKAGE)
                        .putExtra(EXTRA_PACKAGE_NAME, component.getPackageName())
                        .putExtra(EXTRA_CLASS_NAME, component.getClassName())
                        .addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
                sentAny |= trySendBroadcast(context, intent, action, component);
            }
        }
        return sentAny;
    }

    private static boolean tryStartActivity(Context context, Intent intent, ComponentName component) {
        try {
            context.startActivity(intent);
            Diagnostics.log(context, "YMP TS18 reboot activity opened: " + component.flattenToShortString());
            return true;
        } catch (ActivityNotFoundException ex) {
            Diagnostics.log(context, "YMP TS18 reboot activity not found: " + component.flattenToShortString());
        } catch (SecurityException ex) {
            Diagnostics.log(context, "YMP TS18 reboot activity blocked: " + component.flattenToShortString(), ex);
        } catch (Exception ex) {
            Diagnostics.log(context, "YMP TS18 reboot activity failed: " + component.flattenToShortString(), ex);
        }
        return false;
    }

    private static boolean trySendBroadcast(
            Context context,
            Intent intent,
            String action,
            ComponentName component
    ) {
        try {
            context.sendBroadcast(intent);
            Diagnostics.log(context, "YMP TS18 reboot request sent: " + action
                    + " -> " + component.flattenToShortString());
            return true;
        } catch (SecurityException ex) {
            Diagnostics.log(context, "YMP TS18 reboot request blocked: " + action, ex);
        } catch (Exception ex) {
            Diagnostics.log(context, "YMP TS18 reboot request failed: " + action, ex);
        }
        return false;
    }
}
