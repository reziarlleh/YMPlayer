package dev.petrov.yaplay;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.core.view.ViewCompat;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/** Compact footer with an on-demand, bounded diagnostics drawer. */
final class AppStatusBar extends LinearLayout {
    private static final Executor LOG_READER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "YMP-StatusLog");
        thread.setDaemon(true);
        return thread;
    });
    private final TextView message;
    private final TextView detail;
    private final ScrollView scroll;
    private final TextView fullText;
    private final Executor logReader;
    private final Supplier<String> logSource;
    private String status = "";
    private String log = "";
    private boolean expanded;
    private boolean readingLog;
    private int generation;
    private final Runnable refresh = this::refreshLog;

    AppStatusBar(Context context) {
        this(context, LOG_READER, () -> Diagnostics.snapshot(context.getApplicationContext()));
    }

    AppStatusBar(Context context, Executor executor, Supplier<String> source) {
        super(context);
        logReader = executor;
        logSource = source;
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackgroundColor(0xff101820);
        int padding = Math.round(12 * getResources().getDisplayMetrics().density);
        setPadding(padding, padding / 2, padding, padding / 2);
        message = line(context, 0xfff4f8fb);
        detail = line(context, 0xffaebec9);
        scroll = new ScrollView(context);
        scroll.setFillViewport(false);
        scroll.setFocusable(false);
        fullText = new TextView(context);
        fullText.setTextSize(14);
        fullText.setTextColor(0xfff4f8fb);
        fullText.setPadding(0, padding / 2, 0, padding / 2);
        fullText.setLineSpacing(padding / 3f, 1f);
        fullText.setOnClickListener(v -> performClick());
        fullText.setFocusable(false);
        scroll.addView(fullText, new ScrollView.LayoutParams(-1, -2));
        setOnClickListener(v -> setExpanded(!expanded));
        setFocusable(DeviceUi.usesRemoteControl(context));
        setFocusableInTouchMode(DeviceUi.usesRemoteControl(context));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        updateDescription();
    }

    private TextView line(Context context, int color) {
        TextView view = new TextView(context);
        view.setTextSize(13);
        view.setTextColor(color);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setIncludeFontPadding(true);
        addView(view, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        return view;
    }

    void setText(String value) {
        String text = value == null ? "" : value.trim();
        status = text;
        int split = text.indexOf('\n');
        message.setText(oneLine(split < 0 ? text : text.substring(0, split)));
        detail.setText(split < 0 ? "" : oneLine(text.substring(split)));
        updateDescription();
        if (expanded) renderExpandedText();
    }

    void setText(int resource) { setText(getContext().getString(resource)); }

    boolean isExpanded() { return expanded; }

    private void setExpanded(boolean value) {
        if (expanded == value) return;
        expanded = value;
        generation++;
        readingLog = false;
        removeCallbacks(refresh);
        message.setVisibility(value ? GONE : VISIBLE);
        detail.setVisibility(value ? GONE : VISIBLE);
        if (value) {
            addView(scroll, new LayoutParams(-1, -2));
            renderExpandedText();
            scroll.scrollTo(0, 0);
            refreshLog();
        } else {
            removeView(scroll);
        }
        updateDescription();
        requestLayout();
    }

    private void updateDescription() {
        String action = getContext().getString(expanded ? R.string.status_collapse : R.string.status_expand);
        setContentDescription(action + "\n" + status);
        setTooltipText(action);
        ViewCompat.setStateDescription(this, action);
    }

    private void renderExpandedText() {
        String value = status + (log.isEmpty() ? "" : "\n\n"
                + getContext().getString(R.string.section_diagnostics) + "\n" + log);
        if (!value.contentEquals(fullText.getText())) fullText.setText(value);
    }

    private void refreshLog() {
        if (!expanded || !isAttachedToWindow() || !isShown() || readingLog) return;
        readingLog = true;
        int request = generation;
        logReader.execute(() -> {
            String result;
            try {
                result = logSource.get();
            } catch (Exception ex) {
                result = getContext().getString(R.string.status_log_unavailable);
            }
            String loaded = result;
            post(() -> {
                if (request != generation || !expanded || !isAttachedToWindow()) return;
                readingLog = false;
                log = loaded == null ? "" : loaded;
                renderExpandedText();
                postDelayed(refresh, 2_000L);
            });
        });
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (expanded) post(refresh);
    }

    @Override protected void onDetachedFromWindow() {
        generation++;
        readingLog = false;
        removeCallbacks(refresh);
        super.onDetachedFromWindow();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (refresh == null) return;
        removeCallbacks(refresh);
        if (visibility == VISIBLE && expanded) post(refresh);
    }

    @Override protected void onMeasure(int width, int height) {
        if (expanded && MeasureSpec.getMode(height) != MeasureSpec.UNSPECIFIED) {
            int minimum = message.getLineHeight() + detail.getLineHeight() + getPaddingTop() + getPaddingBottom();
            int available = MeasureSpec.getSize(height);
            int limit = Math.min(available, Math.max(minimum, Math.round(available * .45f)));
            super.onMeasure(width, MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST));
        } else {
            super.onMeasure(width, height);
        }
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (expanded && (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN)) {
            scroll.smoothScrollBy(0, (keyCode == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1) * fullText.getLineHeight() * 3);
            return true;
        }
        if (expanded && keyCode == KeyEvent.KEYCODE_BACK) {
            setExpanded(false);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private static String oneLine(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }
}
