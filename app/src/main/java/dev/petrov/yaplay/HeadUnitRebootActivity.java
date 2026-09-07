package dev.petrov.yaplay;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.Button;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.petrov.yaplay.player.HeadUnitRebootHelper;
import dev.petrov.yaplay.player.NwdRebootProtocol;

/** A foreground, user-confirmed reboot request, independent of the selected launcher. */
public class HeadUnitRebootActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean closed = new AtomicBoolean();
    private ExecutorService worker;
    private AlertDialog dialog;
    private IBinder readyService;
    private boolean bound;
    private boolean attempted;
    private boolean waiting;

    private final Runnable timeout = () -> {
        if (!closed.get()) {
            waiting = false;
            readyService = null;
            unbind();
            showStatus(attempted ? R.string.reboot_result_unknown : R.string.reboot_service_unavailable);
            Diagnostics.log(this, attempted ? "YMP K4811 reboot reply timeout; no automatic retry"
                    : "YMP K4811 setting service connection timeout");
        }
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (closed.get() || !waiting || attempted) {
                return;
            }
            worker.execute(() -> {
                try {
                    NwdRebootProtocol.verify(service);
                    main.post(() -> {
                        if (!closed.get() && waiting && !attempted) {
                            main.removeCallbacks(timeout);
                            waiting = false;
                            readyService = service;
                            showStatus(R.string.reboot_confirmation_message);
                            Diagnostics.log(HeadUnitRebootActivity.this, "YMP K4811 setting service ready");
                        }
                    });
                } catch (Exception ex) {
                    main.post(() -> unavailable(ex));
                }
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            unavailable(null);
        }

        @Override
        public void onNullBinding(ComponentName name) {
            unavailable(null);
        }

        @Override
        public void onBindingDied(ComponentName name) {
            unavailable(null);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        worker = createWorker();
        attempted = state != null && state.getBoolean("rebootAttempted");
        dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.sidebar_reboot)
                .setMessage(attempted ? R.string.reboot_result_unknown : R.string.reboot_connecting)
                .setNegativeButton(android.R.string.cancel, (d, which) -> cancelRequest())
                .setPositiveButton(R.string.sidebar_reboot, null)
                .setNeutralButton(R.string.reboot_native_dialog, null)
                .create();
        dialog.setOnCancelListener(d -> cancelRequest());
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        for (int which : new int[]{AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEUTRAL}) {
            Button button = dialog.getButton(which);
            float density = getResources().getDisplayMetrics().density;
            button.setForeground(new FocusHighlightDrawable(null, 6 * density, density, 2 * density,
                    Color.WHITE, 0x30ffffff, 0x60ffffff));
            button.setFocusable(true);
            button.setFocusableInTouchMode(DeviceUi.usesRemoteControl(this));
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> confirmReboot());
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            if (!attempted && HeadUnitRebootHelper.openNativeConfirmation(this)) {
                finish();
            } else if (!attempted) {
                showStatus(R.string.reboot_menu_unavailable);
            }
        });
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus();
        showStatus(attempted ? R.string.reboot_result_unknown : R.string.reboot_connecting);
        if (!attempted) {
            connect();
        }
    }

    protected ExecutorService createWorker() {
        return Executors.newSingleThreadExecutor();
    }

    private void connect() {
        waiting = true;
        main.postDelayed(timeout, 5000);
        try {
            bound = bindService(NwdRebootProtocol.serviceIntent(), connection, BIND_AUTO_CREATE);
            Diagnostics.log(this, "YMP K4811 setting service bind requested: " + bound);
            if (!bound) {
                unavailable(null);
            }
        } catch (RuntimeException ex) {
            unavailable(ex);
        }
    }

    private void confirmReboot() {
        IBinder service = readyService;
        if (closed.get() || attempted || service == null) {
            return;
        }
        attempted = true;
        showStatus(R.string.reboot_sending);
        main.postDelayed(timeout, 5000);
        worker.execute(() -> {
            if (closed.get()) {
                return;
            }
            try {
                Diagnostics.log(this, "YMP K4811 reboot confirmed by user; sending type=2 once");
                if (!NwdRebootProtocol.reboot(service, closed::get)) {
                    return;
                }
                main.post(() -> {
                    if (!closed.get()) {
                        main.removeCallbacks(timeout);
                        unbind();
                        showStatus(R.string.reboot_sent);
                        Diagnostics.log(this, "YMP K4811 reboot transaction acknowledged; device restart not verified");
                    }
                });
            } catch (Exception ex) {
                main.post(() -> unavailable(ex));
            }
        });
    }

    private void unavailable(Exception ex) {
        if (closed.get()) {
            return;
        }
        main.removeCallbacks(timeout);
        waiting = false;
        readyService = null;
        unbind();
        showStatus(attempted ? R.string.reboot_result_unknown : R.string.reboot_service_unavailable);
        Diagnostics.log(this, attempted ? "YMP K4811 reboot result unknown; no automatic retry"
                : "YMP K4811 setting service unavailable", ex);
    }

    private void showStatus(int message) {
        dialog.setMessage(getString(message));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(readyService != null && !attempted);
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(!attempted && !waiting && readyService == null);
    }

    private void unbind() {
        if (bound) {
            bound = false;
            try {
                unbindService(connection);
            } catch (IllegalArgumentException ex) {
                Diagnostics.log(this, "YMP K4811 setting service already unbound", ex);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("rebootAttempted", attempted);
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onStop() {
        closeRequest();
        super.onStop();
        finish();
    }

    @Override
    protected void onDestroy() {
        closeRequest();
        super.onDestroy();
    }

    private void closeRequest() {
        closed.set(true);
        main.removeCallbacksAndMessages(null);
        unbind();
        if (worker != null) {
            worker.shutdownNow();
        }
    }

    private void cancelRequest() {
        closeRequest();
        finish();
    }
}
