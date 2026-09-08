package dev.petrov.yaplay;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.DisplayCutoutCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {29, 35})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AdaptiveLayoutTest {
    @Test public void playerFitsPhoneHeadUnitAndTv() throws Exception {
        int[][] sizes = {{320,568,160}, {360,800,160}, {1080,2400,480}, {800,360,160},
                {1024,600,160}, {1280,720,240}, {1920,1080,320}, {800,1280,160}};
        for (int[] size : sizes) {
            configure(size[0], size[1], size[2]);
            MainActivity activity = activity();
            View root = (View) invoke(activity, "buildContent");
            sampleTrack(activity);
            measure(root, size[0], size[1]);
            snapshot(root, "player-" + size[0] + "x" + size[1] + "-" + size[2]);
            ScrollView scroll = field(activity, "playerPageView");
            if (size[0] > 320) assertTrue("Unexpected player scroll at " + size[0] + "x" + size[1],
                    scroll.getChildAt(0).getHeight() <= scroll.getHeight());
            AppStatusBar status = field(activity, "statusView");
            assertEquals(root.getHeight(), status.getBottom());
            assertEquals(2, status.getChildCount());
            checkButtons(root);
            View previous = findDescription(root, activity.getString(R.string.previous_track));
            View next = findDescription(root, activity.getString(R.string.next_track));
            View play = field(activity, "playPauseButton");
            assertEquals(previous.getParent(), next.getParent());
            assertTrue(previous.getLeft() < play.getLeft() && play.getLeft() < next.getLeft());
        }
    }

    @Test public void statusStaysFixedAcrossPagesSettingsAndSearch() throws Exception {
        configure(1024, 600, 160);
        MainActivity activity = activity();
        View root = (View) invoke(activity, "buildContent");
        measure(root, 1024, 600);
        AppStatusBar status = field(activity, "statusView");
        int top = status.getTop();
        invoke(activity, "updateStatus", new Class<?>[]{String.class}, "Message\n\nCache details");
        invoke(activity, "showLibraryPage");
        measure(root, 1024, 600);
        assertEquals(top, status.getTop());
        snapshot(root, "library-1024x600");
        for (String method : new String[]{"showSettingsDialog", "showSearchEntry"}) {
            invoke(activity, method);
            shadowOf(Looper.getMainLooper()).idle();
            Dialog dialog = ShadowDialog.getLatestDialog();
            View shell = ((ViewGroup) dialog.findViewById(android.R.id.content)).getChildAt(0);
            measure(shell, 1024, 600);
            snapshot(shell, method + "-1024x600");
            AppStatusBar bar = findStatus(shell);
            assertNotNull(bar);
            assertEquals(600, bar.getBottom());
            invoke(activity, "updateStatus", new Class<?>[]{String.class}, "Operation complete\n\nCache ready");
            assertEquals("Operation complete", ((TextView) bar.getChildAt(0)).getText().toString());
            dialog.dismiss();
        }
    }

    @Test public void safeInsetsDoNotAccumulateAndIncludeKeyboardAndCutout() {
        configure(1080, 2400, 480);
        MainActivity activity = activity();
        View root = new View(activity);
        root.setPadding(3, 4, 5, 6);
        SafeWindow.install(activity.getWindow(), root);
        WindowInsetsCompat insets = new WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 72, 0, 96))
                .setDisplayCutout(new DisplayCutoutCompat(new Rect(110, 0, 0, 0),
                        java.util.Collections.singletonList(new Rect(0, 50, 110, 150))))
                .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(110, 0, 0, 0))
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 500)).build();
        for (int i = 0; i < 3; i++) SafeWindow.applyInsets(root, insets, Insets.of(3,4,5,6));
        assertEquals(113, root.getPaddingLeft());
        assertEquals(76, root.getPaddingTop());
        assertEquals(5, root.getPaddingRight());
        assertEquals(506, root.getPaddingBottom());
        SafeWindow.applyInsets(root, new WindowInsetsCompat.Builder().build(), Insets.of(3,4,5,6));
        assertEquals(6, root.getPaddingBottom());
    }

    @Test public void longStatusAndLargeFontNeverResizeOrEscapeFooter() throws Exception {
        configure(360, 800, 160);
        RuntimeEnvironment.setFontScale(1.5f);
        MainActivity activity = activity();
        View root = (View) invoke(activity, "buildContent");
        measure(root, 360, 800);
        AppStatusBar bar = field(activity, "statusView");
        int height = bar.getHeight();
        invoke(activity, "updateStatus", new Class<?>[]{String.class}, "Long message ".repeat(100) + "\n\nCache ".repeat(80));
        measure(root, 360, 800);
        assertEquals(height, bar.getHeight());
        assertEquals(800, bar.getBottom());
        snapshot(root, "phone-large-font");
    }

    @Test public void clipControlsAndStatusStayInsideViewport() throws Exception {
        for (int[] size : new int[][]{{360,800}, {800,360}, {1024,600}, {1920,1080}}) {
            configure(size[0], size[1], 160);
            ClipWaveActivity activity = Robolectric.buildActivity(ClipWaveActivity.class).get();
            Method build = ClipWaveActivity.class.getDeclaredMethod("buildContent");
            build.setAccessible(true);
            View root = (View) build.invoke(activity);
            for (String name : new String[]{"loadingPanel", "overlayView"}) {
                Field field = ClipWaveActivity.class.getDeclaredField(name);
                field.setAccessible(true);
                ((View) field.get(activity)).setVisibility(name.equals("loadingPanel") ? View.GONE : View.VISIBLE);
            }
            for (String name : new String[]{"titleView", "artistView"}) {
                Field field = ClipWaveActivity.class.getDeclaredField(name);
                field.setAccessible(true);
                ((TextView) field.get(activity)).setText(name.equals("titleView") ? "Название клипа" : "Исполнитель");
            }
            measure(root, size[0], size[1]);
            snapshot(root, "clips-" + size[0] + "x" + size[1]);
            checkButtons(root);
            assertEquals(size[1], findStatus(root).getBottom());
        }
    }

    private static MainActivity activity() {
        MainActivity activity = Robolectric.buildActivity(TestActivity.class).create().get();
        activity.setTheme(R.style.AppTheme);
        return activity;
    }

    private static void configure(int width, int height, int dpi) {
        int w = width * 160 / dpi;
        int h = height * 160 / dpi;
        RuntimeEnvironment.setQualifiers("ru-rRU-w" + w + "dp-h" + h + "dp-"
                + (width > height ? "land" : "port") + "-" + dpi + "dpi");
        RuntimeEnvironment.setFontScale(1f);
    }

    private static void sampleTrack(MainActivity activity) throws Exception {
        ((TextView) field(activity, "nowTitleView")).setText("Ветер перемен");
        ((TextView) field(activity, "nowArtistView")).setText("Исполнитель и приглашённый артист");
        ((TextView) field(activity, "nowAlbumView")).setText("Название альбома · 2026");
        ((TextView) field(activity, "queueView")).setText("Трек 12 · Следующий трек готов");
        invoke(activity, "updateStatus", new Class<?>[]{String.class}, "Воспроизведение из избранного\n\nКэш: 985 треков · Обложки сохранены");
    }

    private static void checkButtons(View view) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (view instanceof ImageButton && view.getParent() instanceof View) {
            View parent = (View) view.getParent();
            assertTrue(view.getContentDescription() + " left", view.getLeft() >= 0);
            assertTrue(view.getContentDescription() + " right", view.getRight() <= parent.getWidth());
            assertTrue(view.getContentDescription() + " bottom", view.getBottom() <= parent.getHeight());
            if (parent instanceof PlayerButtonLayout) assertEquals(view.getWidth(), view.getHeight());
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) checkButtons(group.getChildAt(i));
        }
    }

    private static View findDescription(View root, String description) {
        if (description.contentEquals(root.getContentDescription() == null ? "" : root.getContentDescription())) return root;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
            View found = findDescription(((ViewGroup) root).getChildAt(i), description);
            if (found != null) return found;
        }
        return null;
    }

    private static AppStatusBar findStatus(View root) {
        if (root instanceof AppStatusBar) return (AppStatusBar) root;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
            AppStatusBar found = findStatus(((ViewGroup) root).getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private static void measure(View root, int width, int height) {
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
    }

    private static void snapshot(View root, String name) throws Exception {
        File directory = new File("build/reports/layout");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        Bitmap bitmap = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bitmap));
        try (FileOutputStream stream = new FileOutputStream(new File(directory, name + "-api" + android.os.Build.VERSION.SDK_INT + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
        }
        bitmap.recycle();
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(MainActivity activity, String name) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(activity);
    }

    private static Object invoke(MainActivity activity, String name) throws Exception {
        return invoke(activity, name, new Class<?>[0]);
    }

    private static Object invoke(MainActivity activity, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = MainActivity.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(activity, args);
    }

    public static class TestActivity extends MainActivity {
        @Override protected void onCreate(Bundle state) { /* Layout tests do not start services or network requests. */ }
    }

}
