package dev.petrov.yaplay.player;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import dev.petrov.yaplay.Diagnostics;
import dev.petrov.yaplay.HeadUnitRebootActivity;

public final class HeadUnitRebootHelper {
    private HeadUnitRebootHelper() {
    }

    public static boolean requestReboot(Context context) {
        try {
            context.startActivity(new Intent(context, HeadUnitRebootActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            Diagnostics.log(context, "YMP K4811 reboot confirmation requested");
            return true;
        } catch (RuntimeException ex) {
            Diagnostics.log(context, "YMP K4811 reboot confirmation could not open", ex);
            return false;
        }
    }

    public static boolean openNativeConfirmation(Context context) {
        // K4811 ships the dialog in ToolAllInOne; the launcher target is legacy.
        for (String packageName : new String[]{"com.nwd.toolallinone.app", "com.android.launcher"}) {
            ComponentName component = new ComponentName(packageName, "com.nwd.tools.reboot.RebootActivity");
            Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            try {
                context.startActivity(intent);
                Diagnostics.log(context, "YMP native reboot dialog launch requested: " + component.flattenToShortString());
                return true;
            } catch (RuntimeException ex) {
                Diagnostics.log(context, "YMP native reboot dialog unavailable: " + component.flattenToShortString(), ex);
            }
        }
        return false;
    }
}
