package dev.petrov.yaplay;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Two reserved lines, outside scrolling content and independent of message length. */
final class AppStatusBar extends LinearLayout {
    private final TextView message;
    private final TextView detail;

    AppStatusBar(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackgroundColor(0xff101820);
        int padding = Math.round(12 * getResources().getDisplayMetrics().density);
        setPadding(padding, padding / 2, padding, padding / 2);
        message = line(context, 0xfff4f8fb);
        detail = line(context, 0xffaebec9);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
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
        int split = text.indexOf('\n');
        message.setText(oneLine(split < 0 ? text : text.substring(0, split)));
        detail.setText(split < 0 ? "" : oneLine(text.substring(split)));
        setContentDescription(text);
        setTooltipText(text);
    }

    void setText(int resource) { setText(getContext().getString(resource)); }

    private static String oneLine(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }
}
