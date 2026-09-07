package dev.petrov.yaplay;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Looper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import dev.petrov.yaplay.player.NwdRebootProtocolTest.RecordingBinder;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {29, 35})
public class HeadUnitRebootActivityTest {
    private ActivityController<TestActivity> controller;
    private TestActivity activity;
    private AlertDialog dialog;
    private RecordingBinder service;

    @Before
    public void start() {
        controller = Robolectric.buildActivity(TestActivity.class).setup();
        activity = controller.get();
        dialog = ShadowAlertDialog.getLatestAlertDialog();
        service = new RecordingBinder();
    }

    @After
    public void destroy() {
        controller.pause().stop().destroy();
    }

    private void connect() {
        activity.connection.onServiceConnected(new ComponentName("com.nwd.setting.service", "SettingService"), service);
        activity.worker.runAll();
        shadowOf(Looper.getMainLooper()).idle();
    }

    @Test
    public void openingAndConnectingNeverReboots() {
        assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());
        connect();
        assertTrue(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());
        assertEquals(0, service.calls);
    }

    @Test
    public void cancelNeverReboots() {
        connect();
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(activity.isFinishing());
        assertEquals(0, service.calls);
    }

    @Test
    public void repeatedConfirmationSendsOnce() {
        connect();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        activity.worker.runAll();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, service.calls);
        assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());
        assertFalse(dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled());
        assertEquals(1, activity.unbinds);
    }

    @Test
    public void timeoutDoesNotAcceptLateConnection() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6));
        connect();
        assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());
        assertTrue(dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled());
        assertEquals(0, service.calls);
        assertEquals(1, activity.unbinds);
    }

    @Test
    public void stoppingDropsQueuedCommand() {
        connect();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        controller.pause().stop();
        activity.worker.runAll();
        assertEquals(0, service.calls);
        controller.restart().start().resume();
    }

    @Test
    public void disconnectBeforeConfirmPreventsReboot() {
        connect();
        activity.connection.onServiceDisconnected(new ComponentName("com.nwd.setting.service", "SettingService"));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        activity.worker.runAll();
        assertEquals(0, service.calls);
        assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());
    }

    @Test
    public void failedReplyNeverRetriesOrAutomaticallyOpensFallback() {
        connect();
        service.supported = false;
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        activity.worker.runAll();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10));
        assertEquals(1, service.calls);
        assertFalse(dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled());
        assertNull(shadowOf(activity).getNextStartedActivity());
    }

    public static class TestActivity extends HeadUnitRebootActivity {
        final QueueWorker worker = new QueueWorker();
        ServiceConnection connection;
        int unbinds;

        @Override
        protected QueueWorker createWorker() {
            return worker;
        }

        @Override
        public boolean bindService(Intent intent, ServiceConnection connection, int flags) {
            this.connection = connection;
            return true;
        }

        @Override
        public void unbindService(ServiceConnection connection) {
            unbinds++;
        }
    }

    static class QueueWorker extends AbstractExecutorService {
        final List<Runnable> pending = new ArrayList<>();
        boolean shutdown;

        void runAll() {
            while (!pending.isEmpty()) {
                pending.remove(0).run();
            }
        }

        @Override public void execute(Runnable task) { pending.add(task); }
        @Override public void shutdown() { shutdown = true; }
        @Override public List<Runnable> shutdownNow() {
            shutdown();
            List<Runnable> tasks = new ArrayList<>(pending);
            pending.clear();
            return tasks;
        }
        @Override public boolean isShutdown() { return shutdown; }
        @Override public boolean isTerminated() { return shutdown; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return shutdown; }
    }
}
