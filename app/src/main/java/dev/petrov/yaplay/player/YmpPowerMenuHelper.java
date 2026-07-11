package dev.petrov.yaplay.player;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import dev.petrov.yaplay.Diagnostics;

public final class YmpPowerMenuHelper {
    private static final String ACTION_REQUEST_SHUTDOWN = "android.intent.action.ACTION_REQUEST_SHUTDOWN";
    private static final String ACTION_TS18_REQUEST_START_ACTIVITY = "com.nwd.ACTION_REQUEST_START_ACTIVITY";
    private static final String ACTION_TS18_START_ACTIVITY = "com.nwd.action.ACTION_START_ACTIVITY";
    private static final String ACTION_TS18_START_NWD_ACTIVITY = "com.nwd.action.ACTION_START_NWD_ACTIVITY";
    private static final String ACTION_TS18_KEY_VALUE = "com.nwd.action.ACTION_KEY_VALUE";
    private static final String ACTION_TS18_SET_SYSTEM_PROP = "com.nwd.action.ACTION_SET_SYSTEM_PROP";
    private static final String EXTRA_KEY_CONFIRM = "android.intent.extra.KEY_CONFIRM";
    private static final String EXTRA_USER_REQUESTED_SHUTDOWN =
            "android.intent.extra.USER_REQUESTED_SHUTDOWN";
    private static final String EXTRA_TS18_KEY_TYPE = "extra_key_type";
    private static final String EXTRA_TS18_KEY_VALUE = "extra_key_value";
    private static final String EXTRA_TS18_PROP_NAME = "extra_prop_name";
    private static final String EXTRA_TS18_PROP_VALUE = "extra_prop_value";
    private static final String EXTRA_TS18_PACKAGE_NAME = "extra_package_name";
    private static final String EXTRA_TS18_CLASS_NAME = "extra_class_name";
    private static final String NWD_SYSTEM_PROPERTY_SETTING = "nwd_system_prop";
    private static final String POWER_CONTROL_PROPERTY = "sys.powerctl";
    private static final String POWER_CONTROL_SHUTDOWN = "shutdown,userrequested";
    private static final String STATUS_BAR_SERVICE = "statusbar";
    private static final String STATUS_BAR_SERVICE_MANAGER_CLASS = "android.os.ServiceManager";
    private static final String STATUS_BAR_SERVICE_STUB_CLASS =
            "com.android.internal.statusbar.IStatusBarService$Stub";
    private static final String METHOD_GET_SERVICE = "getService";
    private static final String METHOD_AS_INTERFACE = "asInterface";
    private static final String METHOD_SHOW_GLOBAL_ACTIONS = "showGlobalActions";
    private static final String METHOD_SHOW_GLOBAL_ACTIONS_MENU = "showGlobalActionsMenu";
    private static final String TS18_LAUNCHER_PACKAGE = "com.android.launcher";
    private static final String TS18_TOOLALLINONE_PACKAGE = "com.nwd.toolallinone.app";
    private static final String TS18_REBOOT_ACTIVITY_CLASS = "com.nwd.tools.reboot.RebootActivity";
    private static final String ANDROID_PACKAGE = "android";
    private static final String ANDROID_SHUTDOWN_ACTIVITY_CLASS =
            "com.android.internal.app.ShutdownActivity";
    private static final byte TS18_POWER_KEY = 0;
    private static final byte TS18_KEY_EVENT_UP = 0;
    private static final byte TS18_KEY_EVENT_DOWN = 1;
    private static final byte TS18_KEY_EVENT_LONG_PRESS = 2;
    private static final long TS18_POWER_LONG_PRESS_MS = 1_050L;
    private static final long TS18_POWER_KEY_UP_DELAY_MS = 120L;
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
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
        if (tryShowGlobalActionsViaStatusBarManager(appContext)) {
            return true;
        }
        if (tryShowGlobalActionsViaStatusBarService(appContext)) {
            return true;
        }
        return tryStartAndroidShutdownDialog(appContext);
    }

    /**
     * Runs only after YMPlayer has shown its own shutdown confirmation dialog.
     */
    public static boolean executeConfirmedShutdown(Context context) {
        Context appContext = context.getApplicationContext();
        Diagnostics.log(appContext, "YMP confirmed shutdown execution started");

        if (tryInvokePowerManagerShutdown(appContext)) {
            return true;
        }
        if (trySetSystemPowerPropertyDirect(appContext)) {
            return true;
        }
        if (tryStartAndroidShutdown(appContext, false)) {
            return true;
        }

        boolean vendorPropertySent = trySendNwdSystemPropertyShutdown(appContext);
        boolean launcherRequestSent = trySendTs18LauncherShutdownRequest(appContext);
        boolean longPressSent = trySendNwdPowerLongPress(appContext);
        boolean attempted = vendorPropertySent || launcherRequestSent || longPressSent;
        if (!attempted) {
            Diagnostics.log(appContext, "YMP confirmed shutdown failed: no usable path");
        }
        return attempted;
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
        return tryStartAndroidShutdown(context, true);
    }

    private static boolean tryStartAndroidShutdown(Context context, boolean confirm) {
        Intent intent = new Intent(ACTION_REQUEST_SHUTDOWN)
                .putExtra(EXTRA_KEY_CONFIRM, confirm)
                .putExtra(EXTRA_USER_REQUESTED_SHUTDOWN, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return tryStartActivity(
                context,
                intent,
                confirm ? "Android shutdown request dialog" : "Android confirmed shutdown request"
        );
    }

    private static boolean tryInvokePowerManagerShutdown(Context context) {
        try {
            Object powerManager = context.getSystemService(Context.POWER_SERVICE);
            if (powerManager == null) {
                Diagnostics.log(context, "YMP shutdown path not found: PowerManager");
                return false;
            }
            Method method = findMethod(
                    powerManager,
                    "shutdown",
                    boolean.class,
                    String.class,
                    boolean.class
            );
            method.invoke(powerManager, false, "userrequested", false);
            Diagnostics.log(context, "YMP shutdown requested via PowerManager.shutdown");
            return true;
        } catch (NoSuchMethodException ex) {
            Diagnostics.log(context, "YMP shutdown path not found: PowerManager.shutdown", ex);
        } catch (InvocationTargetException ex) {
            logInvocationFailure(context, "PowerManager.shutdown", ex);
        } catch (SecurityException ex) {
            Diagnostics.log(context, "YMP shutdown path blocked by security: PowerManager.shutdown", ex);
        } catch (Exception ex) {
            Diagnostics.log(context, "YMP shutdown path failed: PowerManager.shutdown", ex);
        }
        return false;
    }

    private static boolean trySetSystemPowerPropertyDirect(Context context) {
        try {
            Class<?> systemProperties = Class.forName("android.os.SystemProperties");
            Method set = systemProperties.getDeclaredMethod("set", String.class, String.class);
            set.setAccessible(true);
            set.invoke(null, POWER_CONTROL_PROPERTY, POWER_CONTROL_SHUTDOWN);
            Diagnostics.log(context, "YMP shutdown requested via SystemProperties sys.powerctl");
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException ex) {
            Diagnostics.log(context, "YMP shutdown path not found: SystemProperties.set", ex);
        } catch (InvocationTargetException ex) {
            logInvocationFailure(context, "SystemProperties sys.powerctl", ex);
        } catch (SecurityException ex) {
            Diagnostics.log(context, "YMP shutdown path blocked by security: SystemProperties.set", ex);
        } catch (Exception ex) {
            Diagnostics.log(context, "YMP shutdown path failed: SystemProperties.set", ex);
        }
        return false;
    }

    @SuppressLint("WrongConstant")
    private static boolean tryShowGlobalActionsViaStatusBarManager(Context context) {
        try {
            Object statusBarManager = context.getSystemService(STATUS_BAR_SERVICE);
            if (statusBarManager == null) {
                Diagnostics.log(context, "YMP power menu path not found: StatusBarManager service");
                return false;
            }
            if (tryInvokeNoArgMethods(
                    context,
                    statusBarManager,
                    new String[]{METHOD_SHOW_GLOBAL_ACTIONS, METHOD_SHOW_GLOBAL_ACTIONS_MENU},
                    "StatusBarManager global actions menu")) {
                return true;
            }
        } catch (Exception ex) {
            Diagnostics.log(context, "YMP power menu StatusBarManager path failed", ex);
        }
        return false;
    }

    private static boolean tryShowGlobalActionsViaStatusBarService(Context context) {
        try {
            Class<?> serviceManagerClass = Class.forName(STATUS_BAR_SERVICE_MANAGER_CLASS);
            Method getService = serviceManagerClass.getDeclaredMethod(METHOD_GET_SERVICE, String.class);
            Object binder = getService.invoke(null, STATUS_BAR_SERVICE);
            if (binder == null) {
                Diagnostics.log(context, "YMP power menu path not found: statusbar binder");
                return false;
            }

            Class<?> stubClass = Class.forName(STATUS_BAR_SERVICE_STUB_CLASS);
            Method asInterface = stubClass.getDeclaredMethod(METHOD_AS_INTERFACE, Class.forName("android.os.IBinder"));
            Object statusBarService = asInterface.invoke(null, binder);
            if (statusBarService == null) {
                Diagnostics.log(context, "YMP power menu path not found: IStatusBarService");
                return false;
            }

            return tryInvokeNoArgMethod(
                    context,
                    statusBarService,
                    METHOD_SHOW_GLOBAL_ACTIONS_MENU,
                    "IStatusBarService global actions menu");
        } catch (ClassNotFoundException | NoSuchMethodException ex) {
            Diagnostics.log(context, "YMP power menu path not found: IStatusBarService global actions", ex);
        } catch (InvocationTargetException ex) {
            logInvocationFailure(context, "IStatusBarService global actions menu", ex);
        } catch (SecurityException ex) {
            Diagnostics.log(context, "YMP power menu path blocked by security: IStatusBarService global actions", ex);
        } catch (Exception ex) {
            Diagnostics.log(context, "YMP power menu path failed: IStatusBarService global actions", ex);
        }
        return false;
    }

    private static boolean tryInvokeNoArgMethod(Context context, Object target, String methodName, String label) {
        try {
            Method method = findMethod(target, methodName);
            method.invoke(target);
            Diagnostics.log(context, "YMP power menu opened via " + label);
            return true;
        } catch (NoSuchMethodException ex) {
            Diagnostics.log(context, "YMP power menu path not found: " + label, ex);
        } catch (InvocationTargetException ex) {
            logInvocationFailure(context, label, ex);
        } catch (SecurityException ex) {
            Diagnostics.log(context, "YMP power menu path blocked by security: " + label, ex);
        } catch (Exception ex) {
            Diagnostics.log(context, "YMP power menu path failed: " + label, ex);
        }
        return false;
    }

    private static boolean tryInvokeNoArgMethods(
            Context context,
            Object target,
            String[] methodNames,
            String label
    ) {
        for (String methodName : methodNames) {
            if (tryInvokeNoArgMethod(context, target, methodName, label + " " + methodName)) {
                return true;
            }
        }
        return false;
    }

    private static Method findMethod(Object target, String methodName, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Class<?> targetClass = target.getClass();
        try {
            return targetClass.getMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException ex) {
            Method method = targetClass.getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method;
        }
    }

    private static void logInvocationFailure(Context context, String label, InvocationTargetException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof SecurityException) {
            Diagnostics.log(context, "YMP power menu path blocked by security: " + label, cause);
        } else if (cause != null) {
            Diagnostics.log(context, "YMP power menu path failed: " + label, cause);
        } else {
            Diagnostics.log(context, "YMP power menu path failed: " + label, ex);
        }
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

    private static boolean trySendTs18LauncherShutdownRequest(Context context) {
        boolean sentAny = false;
        ComponentName shutdownActivity = new ComponentName(
                ANDROID_PACKAGE,
                ANDROID_SHUTDOWN_ACTIVITY_CLASS
        );
        String[] receiverPackages = {TS18_LAUNCHER_PACKAGE, TS18_TOOLALLINONE_PACKAGE};
        for (String receiverPackage : receiverPackages) {
            for (String action : TS18_START_ACTIVITY_ACTIONS) {
                Intent intent = new Intent(action)
                        .setPackage(receiverPackage)
                        .putExtra(EXTRA_TS18_PACKAGE_NAME, shutdownActivity.getPackageName())
                        .putExtra(EXTRA_TS18_CLASS_NAME, shutdownActivity.getClassName())
                        .addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
                sentAny |= trySendBroadcast(
                        context,
                        intent,
                        "TS18 privileged shutdown activity request " + action + " via " + receiverPackage
                );
            }
        }
        return sentAny;
    }

    private static boolean trySendNwdSystemPropertyShutdown(Context context) {
        tryRecordNwdSystemPropertyRequest(context);
        Intent intent = new Intent(ACTION_TS18_SET_SYSTEM_PROP)
                .putExtra(EXTRA_TS18_PROP_NAME, POWER_CONTROL_PROPERTY)
                .putExtra(EXTRA_TS18_PROP_VALUE, POWER_CONTROL_SHUTDOWN)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        return trySendBroadcast(context, intent, "TS18/NWD sys.powerctl shutdown bridge");
    }

    private static void tryRecordNwdSystemPropertyRequest(Context context) {
        String request = POWER_CONTROL_PROPERTY + "=" + POWER_CONTROL_SHUTDOWN;
        try {
            boolean stored = Settings.System.putString(
                    context.getContentResolver(),
                    NWD_SYSTEM_PROPERTY_SETTING,
                    request
            );
            Diagnostics.log(
                    context,
                    stored
                            ? "YMP recorded NWD shutdown property request"
                            : "YMP could not record NWD shutdown property request"
            );
        } catch (SecurityException ex) {
            Diagnostics.log(
                    context,
                    "YMP NWD property table is protected; continuing with vendor broadcast",
                    ex
            );
        } catch (Exception ex) {
            Diagnostics.log(
                    context,
                    "YMP NWD property table write failed; continuing with vendor broadcast",
                    ex
            );
        }
    }

    private static boolean trySendNwdPowerLongPress(Context context) {
        Context appContext = context.getApplicationContext();
        boolean sentDown = sendNwdPowerEvent(appContext, TS18_KEY_EVENT_DOWN, "down");
        MAIN_HANDLER.postDelayed(
                () -> {
                    sendNwdPowerEvent(appContext, TS18_KEY_EVENT_LONG_PRESS, "long press");
                    MAIN_HANDLER.postDelayed(
                            () -> sendNwdPowerEvent(appContext, TS18_KEY_EVENT_UP, "up"),
                            TS18_POWER_KEY_UP_DELAY_MS
                    );
                },
                TS18_POWER_LONG_PRESS_MS
        );
        Diagnostics.log(context, "YMP TS18 power long-press sequence scheduled");
        return sentDown;
    }

    private static boolean sendNwdPowerEvent(Context context, byte eventType, String label) {
        Intent intent = new Intent(ACTION_TS18_KEY_VALUE)
                .putExtra(EXTRA_TS18_KEY_VALUE, TS18_POWER_KEY)
                .putExtra(EXTRA_TS18_KEY_TYPE, eventType)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        return trySendBroadcast(context, intent, "TS18 power key " + label);
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
