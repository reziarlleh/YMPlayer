package dev.petrov.yaplay.player;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import dev.petrov.yaplay.Diagnostics;

public final class SideBarHelper {
    public static final String SIDEBAR_PACKAGE = "com.ts18.sidebar";

    private static final String HEALTH_RECEIVER = "com.ts18.sidebar.SideBarHealthReceiver";
    private static final String RESTART_RECEIVER = "com.ts18.sidebar.SideBarRestartWidgetProvider";
    private static final String ACTION_RESTART = "com.ts18.sidebar.action.RESTART_FROM_WIDGET";
    private static final String ACTION_CONFIG_CHANGED = "com.ts18.sidebar.action.CONFIG_CHANGED";
    private static final String EXTRA_SHOW_PANEL = "show_panel";

    private SideBarHelper() {
    }

    public static String ensureRunning(Context context, boolean launchIfNeeded) {
        Context appContext = context.getApplicationContext();
        if (!isInstalled(appContext)) {
            Diagnostics.log(appContext, "YMP SideBar watchdog skipped: package not installed");
            return "SideBar is not installed";
        }
        if (launchIfNeeded) {
            sendRestartBroadcast(appContext);
            String result = "SideBar restart requested";
            Diagnostics.log(appContext, "YMP " + result);
            return result;
        }
        sendQuietHealthBroadcast(appContext);
        sendConfigBroadcast(appContext);
        String result = "SideBar quiet watchdog ping sent";
        Diagnostics.log(appContext, "YMP " + result);
        return result;
    }

    public static boolean isInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo(SIDEBAR_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ex) {
            return false;
        }
    }

    private static void sendRestartBroadcast(Context context) {
        Intent intent = new Intent(ACTION_RESTART);
        intent.setComponent(new ComponentName(SIDEBAR_PACKAGE, RESTART_RECEIVER));
        intent.putExtra(EXTRA_SHOW_PANEL, true);
        safeBroadcast(context, intent, "restart");
    }

    private static void sendQuietHealthBroadcast(Context context) {
        Intent intent = new Intent(Intent.ACTION_USER_PRESENT);
        intent.setComponent(new ComponentName(SIDEBAR_PACKAGE, HEALTH_RECEIVER));
        safeBroadcast(context, intent, "health");
    }

    private static void sendConfigBroadcast(Context context) {
        Intent intent = new Intent(ACTION_CONFIG_CHANGED);
        intent.setPackage(SIDEBAR_PACKAGE);
        safeBroadcast(context, intent, "config");
    }

    private static void safeBroadcast(Context context, Intent intent, String label) {
        try {
            context.sendBroadcast(intent);
        } catch (Exception ex) {
            Diagnostics.log(context, "YMP SideBar " + label + " broadcast failed", ex);
        }
    }
}
