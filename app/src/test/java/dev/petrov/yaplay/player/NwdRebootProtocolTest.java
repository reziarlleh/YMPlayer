package dev.petrov.yaplay.player;

import android.os.Binder;
import android.os.Parcel;
import android.os.RemoteException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {29, 35})
public class NwdRebootProtocolTest {
    @Test
    public void sendsOnlyNativeRebootTypeWithSynchronousReply() throws Exception {
        RecordingBinder service = new RecordingBinder();
        assertTrue(NwdRebootProtocol.reboot(service, () -> false));
        assertEquals(1, service.calls);
        assertEquals(0x1c, service.code);
        assertEquals(2, service.type);
        assertEquals(0, service.flags);
    }

    @Test
    public void rejectsUnknownInterfaceWithoutSending() {
        RecordingBinder service = new RecordingBinder();
        service.attachInterface(null, "different.service");
        assertThrows(RemoteException.class, () -> NwdRebootProtocol.reboot(service, () -> false));
        assertEquals(0, service.calls);
    }

    @Test
    public void cancelledRequestNeverSendsCommand() throws Exception {
        RecordingBinder service = new RecordingBinder();
        assertFalse(NwdRebootProtocol.reboot(service, () -> true));
        assertEquals(0, service.calls);
    }

    @Test
    public void unsupportedTransactionIsNotReportedAsSuccess() {
        RecordingBinder service = new RecordingBinder();
        service.supported = false;
        assertThrows(RemoteException.class, () -> NwdRebootProtocol.reboot(service, () -> false));
        assertEquals(1, service.calls);
    }

    @Test
    public void serviceRejectionIsNotReportedAsSuccess() {
        RecordingBinder service = new RecordingBinder();
        service.reject = true;
        assertThrows(SecurityException.class, () -> NwdRebootProtocol.reboot(service, () -> false));
        assertEquals(1, service.calls);
    }

    @Test
    public void serviceIntentIsExplicitAndMatchesFirmware() {
        assertEquals("com.nwd.setting.service.ACTION_SETTING_SERVICE", NwdRebootProtocol.serviceIntent().getAction());
        assertEquals("com.nwd.setting.service/com.nwd.setting.service.SettingService",
                NwdRebootProtocol.serviceIntent().getComponent().flattenToString());
    }

    public static final class RecordingBinder extends Binder {
        public int calls;
        int code;
        int type;
        int flags;
        public boolean supported = true;
        boolean reject;

        public RecordingBinder() {
            attachInterface(null, "com.nwd.setting.service.SettingFeature");
        }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            calls++;
            this.code = code;
            this.flags = flags;
            data.enforceInterface("com.nwd.setting.service.SettingFeature");
            type = data.readByte();
            assertEquals(0, data.dataAvail());
            if (!supported) {
                return false;
            }
            if (reject) {
                reply.writeException(new SecurityException("Test service rejected request"));
            } else {
                reply.writeNoException();
            }
            return true;
        }
    }
}
