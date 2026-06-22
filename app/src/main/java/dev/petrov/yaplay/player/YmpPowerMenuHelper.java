package dev.petrov.yaplay.player;

import android.content.Context;

import dev.petrov.yaplay.Diagnostics;
import dev.petrov.yaplay.R;

public final class YmpPowerMenuHelper {
    private YmpPowerMenuHelper() {
    }

    public static boolean isAvailable(Context context) {
        return context.getResources().getBoolean(R.bool.enable_accessibility_power_menu);
    }

    public static boolean isEnabled(Context context) {
        return false;
    }

    public static boolean requestPowerDialog(Context context) {
        if (!isAvailable(context)) {
            Diagnostics.log(context, "YMP power menu request ignored: safe build has no accessibility service");
            return false;
        }
        Diagnostics.log(context, "YMP power menu request ignored: no non-accessibility implementation is available");
        return false;
    }
}
