package dev.petrov.yaplay.player;

import android.content.ComponentName;
import android.content.Intent;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

import java.util.function.BooleanSupplier;

/** The reboot-only subset of the K4811 SettingFeature contract. */
public final class NwdRebootProtocol {
    private static final String DESCRIPTOR = "com.nwd.setting.service.SettingFeature";
    private static final int REBOOT_TRANSACTION = 0x1c;

    private NwdRebootProtocol() {
    }

    public static Intent serviceIntent() {
        return new Intent("com.nwd.setting.service.ACTION_SETTING_SERVICE")
                .setComponent(new ComponentName("com.nwd.setting.service", "com.nwd.setting.service.SettingService"));
    }

    public static void verify(IBinder service) throws RemoteException {
        if (service == null || !DESCRIPTOR.equals(service.getInterfaceDescriptor())) {
            throw new RemoteException("Unexpected K4811 setting service interface");
        }
    }

    public static boolean reboot(IBinder service, BooleanSupplier cancelled) throws RemoteException {
        verify(service);
        if (cancelled.getAsBoolean()) {
            return false;
        }
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            // Native RebootActivity uses requestOSFactoryReset((byte) 2).
            // Other values can erase settings: never accept a caller-supplied type.
            data.writeByte((byte) 2);
            if (!service.transact(REBOOT_TRANSACTION, data, reply, 0)) {
                throw new RemoteException("K4811 reboot transaction is unsupported");
            }
            reply.readException();
            return true;
        } finally {
            reply.recycle();
            data.recycle();
        }
    }
}
