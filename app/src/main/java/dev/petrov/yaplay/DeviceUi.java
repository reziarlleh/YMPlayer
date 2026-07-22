package dev.petrov.yaplay;

import android.app.UiModeManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;

import dev.petrov.yaplay.player.YmpSettings;

/** Device-form-factor checks shared by the touch and television player UIs. */
public final class DeviceUi {
    private DeviceUi() {
    }

    public static boolean isTelevision(Context context) {
        if (context == null) {
            return false;
        }
        UiModeManager modeManager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
        if (modeManager != null
                && modeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
            return true;
        }
        PackageManager packages = context.getPackageManager();
        return packages != null
                && (packages.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                || packages.hasSystemFeature(PackageManager.FEATURE_TELEVISION));
    }

    /** Returns the effective interaction model after applying the user override. */
    public static boolean usesRemoteControl(Context context) {
        String mode = YmpSettings.controlMode(context);
        if (YmpSettings.CONTROL_MODE_REMOTE.equals(mode)) {
            return true;
        }
        if (YmpSettings.CONTROL_MODE_TOUCH.equals(mode)) {
            return false;
        }
        return isTelevision(context);
    }
}
