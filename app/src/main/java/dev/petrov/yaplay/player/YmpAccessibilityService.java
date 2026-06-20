package dev.petrov.yaplay.player;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import dev.petrov.yaplay.Diagnostics;
import dev.petrov.yaplay.R;

public final class YmpAccessibilityService extends AccessibilityService {
    private static volatile YmpAccessibilityService instance;

    public static boolean requestPowerDialog(Context context) {
        YmpAccessibilityService service = instance;
        Context appContext = context.getApplicationContext();
        if (service != null) {
            boolean ok = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG);
            Diagnostics.log(appContext, "YMP accessibility power dialog requested: " + ok);
            return ok;
        }
        Diagnostics.log(appContext, "YMP accessibility power dialog unavailable: service is disabled");
        Toast.makeText(appContext, R.string.accessibility_power_required, Toast.LENGTH_LONG).show();
        try {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            appContext.startActivity(intent);
        } catch (Exception ignored) {
        }
        return false;
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
