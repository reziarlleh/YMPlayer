package dev.petrov.yaplay.player;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.EnumMap;
import java.util.Map;

import dev.petrov.yaplay.Diagnostics;
import dev.petrov.yaplay.MainActivity;
import dev.petrov.yaplay.R;

public class EmbeddedSideBarService extends Service {
    public static final String ACTION_SHOW = "dev.petrov.yaplay.action.SIDEBAR_SHOW";
    public static final String ACTION_HIDE = "dev.petrov.yaplay.action.SIDEBAR_HIDE";
    public static final String ACTION_TOGGLE = "dev.petrov.yaplay.action.SIDEBAR_TOGGLE";
    public static final String ACTION_STOP = "dev.petrov.yaplay.action.SIDEBAR_STOP";

    private static final String CHANNEL_ID = "ymplayer_sidebar";
    private static final int NOTIFICATION_ID = 2201;
    private static final long AUTO_HIDE_MS = 8_000L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<Edge, View> collapsedViews = new EnumMap<>(Edge.class);
    private WindowManager windowManager;
    private View panelView;
    private Edge activeEdge = Edge.RIGHT;
    private Runnable autoHideRunnable;

    public static boolean hasOverlayPermission(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context);
    }

    public static Intent overlayPermissionIntent(Context context) {
        return new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + context.getPackageName())
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    public static void start(Context context, boolean showPanel) {
        Intent intent = new Intent(context, EmbeddedSideBarService.class)
                .setAction(showPanel ? ACTION_SHOW : "");
        startServiceCompat(context, intent);
    }

    public static void toggle(Context context) {
        Intent intent = new Intent(context, EmbeddedSideBarService.class).setAction(ACTION_TOGGLE);
        startServiceCompat(context, intent);
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, EmbeddedSideBarService.class).setAction(ACTION_STOP);
        startServiceCompat(context, intent);
    }

    private static void startServiceCompat(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    @Override
    @SuppressLint("ForegroundServiceType")
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        createChannel();
        startForeground(NOTIFICATION_ID, notification());
        Diagnostics.log(this, "YMP embedded SideBar service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!YmpSettings.isEmbeddedSideBarEnabled(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!hasOverlayPermission(this)) {
            Diagnostics.log(this, "YMP embedded SideBar stopped: overlay permission is missing");
            stopSelf();
            return START_NOT_STICKY;
        }
        String action = intent == null ? "" : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_TOGGLE.equals(action)) {
            if (panelView == null) {
                expand(activeEdge);
            } else {
                collapse();
            }
        } else if (ACTION_SHOW.equals(action)) {
            expand(activeEdge);
        } else if (ACTION_HIDE.equals(action)) {
            collapse();
        } else {
            showCollapsed();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        destroyOverlays();
        mainHandler.removeCallbacksAndMessages(null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        Diagnostics.log(this, "YMP embedded SideBar service destroyed");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void showCollapsed() {
        if (panelView != null || !collapsedViews.isEmpty()) {
            return;
        }
        for (Edge edge : Edge.values()) {
            View handle = new EdgeHandleView(this, edge);
            handle.setContentDescription("Open YMPlayer SideBar");
            handle.setOnTouchListener(new HandleTouchListener(edge));
            if (safeAdd(handle, collapsedParams(edge))) {
                collapsedViews.put(edge, handle);
            }
        }
    }

    private void expand(Edge edge) {
        activeEdge = edge;
        removeCollapsed();
        removePanel();
        LinearLayout panel = createPanel(edge);
        if (safeAdd(panel, panelParams(edge))) {
            panelView = panel;
            scheduleAutoHide();
        } else {
            showCollapsed();
        }
    }

    private void collapse() {
        removePanel();
        showCollapsed();
    }

    private LinearLayout createPanel(Edge edge) {
        boolean horizontal = edge == Edge.BOTTOM;
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(horizontal ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(10), dp(10), dp(10), dp(10));
        panel.setBackground(roundRect(Color.argb(188, 8, 13, 20), Color.argb(120, 255, 255, 255), dp(18)));
        panel.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN || event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                resetAutoHide();
            }
            return false;
        });

        addPanelButton(panel, horizontal, R.drawable.ic_side_power, "Power", v -> {
            resetAutoHide();
            if (!YmpPowerMenuHelper.requestPowerDialog(this)) {
                Toast.makeText(this, R.string.power_menu_unavailable, Toast.LENGTH_LONG).show();
            }
        });
        addPanelButton(panel, horizontal, R.drawable.ic_side_volume_up, "Volume up", v -> {
            resetAutoHide();
            Ts18AudioControls.adjustVolume(this, true);
        });
        addPanelButton(panel, horizontal, R.drawable.ic_side_volume_down, "Volume down", v -> {
            resetAutoHide();
            Ts18AudioControls.adjustVolume(this, false);
        });
        addPanelButton(panel, horizontal, R.drawable.ic_side_volume_mute, "Mute", v -> {
            resetAutoHide();
            Ts18AudioControls.toggleMute(this);
        });
        addPanelButton(panel, horizontal, R.drawable.ic_side_home, "Home", v -> {
            resetAutoHide();
            Ts18AudioControls.home(this);
        });
        addPanelButton(panel, horizontal, R.drawable.ic_side_back, "Back", v -> {
            resetAutoHide();
            Ts18AudioControls.back(this);
        });
        addPanelButton(panel, horizontal, hideIcon(edge), "Hide", v -> collapse());
        return panel;
    }

    private ImageButton addPanelButton(LinearLayout panel, boolean horizontal, int icon, String description, View.OnClickListener listener) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(icon);
        button.setColorFilter(Color.WHITE);
        button.setContentDescription(description);
        button.setScaleType(ImageView.ScaleType.FIT_CENTER);
        button.setPadding(dp(15), dp(15), dp(15), dp(15));
        button.setMinimumWidth(dp(82));
        button.setMinimumHeight(dp(82));
        button.setBackground(roundRect(Color.argb(92, 255, 255, 255), Color.argb(120, 255, 255, 255), dp(41)));
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = horizontal
                ? new LinearLayout.LayoutParams(dp(84), dp(84))
                : new LinearLayout.LayoutParams(dp(84), dp(84));
        params.setMargins(dp(4), dp(4), dp(4), dp(4));
        panel.addView(button, params);
        return button;
    }

    private WindowManager.LayoutParams collapsedParams(Edge edge) {
        int width = edge == Edge.BOTTOM ? (int) (getResources().getDisplayMetrics().widthPixels * 0.6f) : dp(52);
        int height = edge == Edge.BOTTOM ? dp(56) : (int) (getResources().getDisplayMetrics().heightPixels * 0.6f);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                width,
                height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                baseWindowFlags(),
                PixelFormat.TRANSLUCENT
        );
        params.gravity = edge == Edge.LEFT
                ? Gravity.START | Gravity.CENTER_VERTICAL
                : edge == Edge.RIGHT
                ? Gravity.END | Gravity.CENTER_VERTICAL
                : Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        return params;
    }

    private WindowManager.LayoutParams panelParams(Edge edge) {
        int width = edge == Edge.BOTTOM ? ViewGroup.LayoutParams.WRAP_CONTENT : dp(108);
        int height = edge == Edge.BOTTOM ? dp(108) : ViewGroup.LayoutParams.WRAP_CONTENT;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                width,
                height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                baseWindowFlags(),
                PixelFormat.TRANSLUCENT
        );
        params.gravity = edge == Edge.LEFT
                ? Gravity.START | Gravity.CENTER_VERTICAL
                : edge == Edge.RIGHT
                ? Gravity.END | Gravity.CENTER_VERTICAL
                : Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        return params;
    }

    private int baseWindowFlags() {
        return WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
    }

    private boolean safeAdd(View view, WindowManager.LayoutParams params) {
        try {
            windowManager.addView(view, params);
            return true;
        } catch (RuntimeException ex) {
            Diagnostics.log(this, "YMP embedded SideBar add view failed", ex);
            return false;
        }
    }

    private void removePanel() {
        cancelAutoHide();
        if (panelView != null) {
            safeRemove(panelView);
            panelView = null;
        }
    }

    private void removeCollapsed() {
        for (View view : collapsedViews.values()) {
            safeRemove(view);
        }
        collapsedViews.clear();
    }

    private void destroyOverlays() {
        removePanel();
        removeCollapsed();
    }

    private void safeRemove(View view) {
        try {
            windowManager.removeView(view);
        } catch (RuntimeException ignored) {
        }
    }

    private void scheduleAutoHide() {
        cancelAutoHide();
        if (!YmpSettings.isEmbeddedSideBarAutoHideEnabled(this)) {
            return;
        }
        View panel = panelView;
        if (panel == null) {
            return;
        }
        autoHideRunnable = () -> {
            if (panelView == panel) {
                collapse();
            }
        };
        panel.postDelayed(autoHideRunnable, AUTO_HIDE_MS);
    }

    private void resetAutoHide() {
        if (panelView != null && YmpSettings.isEmbeddedSideBarAutoHideEnabled(this)) {
            scheduleAutoHide();
        }
    }

    private void cancelAutoHide() {
        if (autoHideRunnable != null && panelView != null) {
            panelView.removeCallbacks(autoHideRunnable);
        }
        autoHideRunnable = null;
    }

    private int hideIcon(Edge edge) {
        if (edge == Edge.LEFT) {
            return R.drawable.ic_side_chevron_left;
        }
        if (edge == Edge.RIGHT) {
            return R.drawable.ic_side_chevron_right;
        }
        return R.drawable.ic_side_chevron_down;
    }

    private GradientDrawable roundRect(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private void createChannel() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.sidebar_notification_channel),
                NotificationManager.IMPORTANCE_MIN
        );
        manager.createNotificationChannel(channel);
    }

    private Notification notification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent content = PendingIntent.getActivity(
                this,
                40,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_yaplay)
                .setContentTitle(getString(R.string.sidebar_notification_title))
                .setContentText(getString(R.string.sidebar_notification_text))
                .setContentIntent(content)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private enum Edge {
        LEFT,
        RIGHT,
        BOTTOM
    }

    private final class EdgeHandleView extends View {
        private final Edge edge;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        EdgeHandleView(Context context, Edge edge) {
            super(context);
            this.edge = edge;
            paint.setColor(Color.argb(38, 255, 255, 255));
            paint.setStrokeWidth(1f);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (edge == Edge.LEFT) {
                canvas.drawLine(1, 0, 1, getHeight(), paint);
            } else if (edge == Edge.RIGHT) {
                canvas.drawLine(getWidth() - 2, 0, getWidth() - 2, getHeight(), paint);
            } else {
                canvas.drawLine(0, getHeight() - 2, getWidth(), getHeight() - 2, paint);
            }
        }
    }

    private final class HandleTouchListener implements View.OnTouchListener {
        private final Edge edge;
        private float startX;
        private float startY;

        HandleTouchListener(Edge edge) {
            this.edge = edge;
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                startX = event.getRawX();
                startY = event.getRawY();
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                if (isExpandGesture(event.getRawX() - startX, event.getRawY() - startY)) {
                    expand(edge);
                }
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                return true;
            }
            return true;
        }

        private boolean isExpandGesture(float dx, float dy) {
            int threshold = dp(32);
            float adx = Math.abs(dx);
            float ady = Math.abs(dy);
            if (edge == Edge.LEFT) {
                return dx > threshold && adx > ady * 1.12f;
            }
            if (edge == Edge.RIGHT) {
                return dx < -threshold && adx > ady * 1.12f;
            }
            return dy < -threshold && ady > adx * 1.12f;
        }
    }
}
