package dev.petrov.yaplay;

import android.app.UiModeManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;

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
}
