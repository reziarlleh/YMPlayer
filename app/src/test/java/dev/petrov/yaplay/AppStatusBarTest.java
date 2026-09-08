package dev.petrov.yaplay;

import android.app.Activity;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import java.util.ArrayDeque;
import java.util.Queue;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {29, 35})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AppStatusBarTest {
    private final Queue<Runnable> work = new ArrayDeque<>();
    private LinearLayout root;
    private AppStatusBar bar;

    private void setup(String log) {
        RuntimeEnvironment.setQualifiers("ru-rRU-w800dp-h600dp-land-mdpi");
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(new View(activity), new LinearLayout.LayoutParams(-1, 0, 1));
        bar = new AppStatusBar(activity, work::add, () -> log);
        root.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        activity.setContentView(root);
        measure();
    }

    private void measure() {
        root.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 800, 600);
    }

    private void completeRead() {
        assertFalse(work.isEmpty());
        work.remove().run();
        shadowOf(Looper.getMainLooper()).idle();
        measure();
    }

    @Test public void tapExpandsWrapsAndCapsLogThenCollapses() {
        setup("2026-09-08 Long diagnostic message with details ".repeat(300));
        bar.setText("First line\nSecond line\nThird line");
        int compactHeight = bar.getHeight();
        assertTrue(work.isEmpty());
        bar.performClick();
        completeRead();
        assertTrue(bar.isExpanded());
        assertTrue(bar.getHeight() > compactHeight);
        assertTrue(bar.getHeight() <= 270);
        assertEquals(600, bar.getBottom());
        ScrollView scroll = (ScrollView) bar.getChildAt(2);
        TextView text = (TextView) scroll.getChildAt(0);
        assertTrue(text.getText().toString().startsWith("First line\nSecond line\nThird line"));
        assertNull(text.getEllipsize());
        assertTrue("text=" + text.getHeight() + ", viewport=" + scroll.getHeight(),
                text.getHeight() > scroll.getHeight());
        bar.setText("New state\nMore details");
        assertTrue(text.getText().toString().startsWith("New state\nMore details"));
        text.performClick();
        measure();
        assertFalse(bar.isExpanded());
        assertEquals(compactHeight, bar.getHeight());
    }

    @Test public void shortContentUsesOnlyNecessaryHeight() {
        setup("");
        bar.setText("Small status");
        bar.performClick();
        completeRead();
        assertTrue(bar.getHeight() < 270);
    }

    @Test public void staleBackgroundReadCannotReopenCollapsedPanel() {
        setup("old result");
        bar.performClick();
        bar.performClick();
        completeRead();
        assertFalse(bar.isExpanded());
        assertEquals(2, bar.getChildCount());
        assertTrue(work.isEmpty());
    }

    @Test public void detachedPanelDiscardsBackgroundResult() {
        setup("detached result");
        bar.performClick();
        root.removeView(bar);
        completeRead();
        TextView text = (TextView) ((ScrollView) bar.getChildAt(2)).getChildAt(0);
        assertFalse(text.getText().toString().contains("detached result"));
    }

    @Test public void remoteKeysScrollAndBackCollapses() {
        setup("Diagnostic line\n".repeat(200));
        bar.setFocusable(true);
        bar.setFocusableInTouchMode(true);
        bar.requestFocus();
        bar.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER));
        bar.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER));
        completeRead();
        assertTrue(bar.isExpanded());
        assertTrue(bar.onKeyDown(KeyEvent.KEYCODE_DPAD_DOWN,
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)));
        assertTrue(bar.onKeyDown(KeyEvent.KEYCODE_BACK,
                new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)));
        assertFalse(bar.isExpanded());
    }

    @Test public void draggingLogDoesNotTriggerCollapse() {
        setup("Diagnostic line\n".repeat(200));
        bar.performClick();
        completeRead();
        ScrollView scroll = (ScrollView) bar.getChildAt(2);
        for (int i = 0; i < 4; i++) {
            int action = i == 0 ? MotionEvent.ACTION_DOWN : i < 3 ? MotionEvent.ACTION_MOVE : MotionEvent.ACTION_UP;
            MotionEvent event = MotionEvent.obtain(0, i * 100, action, 100, 200 - i * 40, 0);
            scroll.dispatchTouchEvent(event);
            event.recycle();
        }
        assertTrue(bar.isExpanded());
        assertTrue(scroll.getScrollY() > 0);
    }
}
