package dev.petrov.yaplay.player;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;

import dev.petrov.yaplay.Diagnostics;

public final class YmpAccessibilityService extends AccessibilityService {
    private static volatile YmpAccessibilityService instance;

    public static boolean isEnabled(Context context) {
        Context appContext = context.getApplicationContext();
        try {
            int enabled = Settings.Secure.getInt(
                    appContext.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED,
                    0
            );
            if (enabled != 1) {
                return false;
            }
            String services = Settings.Secure.getString(
                    appContext.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            );
            if (services == null || services.trim().isEmpty()) {
                return false;
            }
            ComponentName component = new ComponentName(appContext, YmpAccessibilityService.class);
            String full = component.flattenToString();
            String shortName = component.flattenToShortString();
            for (String service : services.split(":")) {
                String value = service == null ? "" : service.trim();
                if (value.equalsIgnoreCase(full) || value.equalsIgnoreCase(shortName)) {
                    return true;
                }
            }
        } catch (Exception ex) {
            Diagnostics.log(appContext, "YMP accessibility status check failed", ex);
        }
        return false;
    }

    public static boolean requestPowerDialog(Context context) {
        YmpAccessibilityService service = instance;
        Context appContext = context.getApplicationContext();
        if (service == null) {
            Diagnostics.log(appContext, "YMP accessibility power dialog unavailable: service is disabled");
            return false;
        }
        boolean ok = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG);
        Diagnostics.log(appContext, "YMP accessibility power dialog requested: " + ok);
        return ok;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Diagnostics.log(this, "YMP accessibility service connected");
    }

    @Override
    public void onDestroy() {
        if (instance == this) {
            instance = null;
        }
        Diagnostics.log(this, "YMP accessibility service destroyed");
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
