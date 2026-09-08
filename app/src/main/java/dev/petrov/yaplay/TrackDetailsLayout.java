package dev.petrov.yaplay;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Aligned metadata column and independent track actions, never an indented title. */
final class TrackDetailsLayout extends ViewGroup {
    final LinearLayout labels;
    final LinearLayout actions;
    private boolean sideBySide;
    private int gap;
    private int typography = -1;

    TrackDetailsLayout(Context context) {
        super(context);
        labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        addView(labels);
        addView(actions);
    }

    void setTypography(int level) {
        if (typography == level) return;
        typography = level;
        int[] sizes = level == 2 ? new int[]{40, 26, 18}
                : level == 1 ? new int[]{32, 22, 16} : new int[]{26, 18, 14};
        for (int i = 0; i < labels.getChildCount(); i++) {
            ((TextView) labels.getChildAt(i)).setTextSize(sizes[i]);
        }
        int size = dp(level == 2 ? 64 : level == 1 ? 56 : 48);
        for (int i = 0; i < actions.getChildCount(); i++) {
            ImageView button = (ImageView) actions.getChildAt(i);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMargins(0, 0, i < actions.getChildCount() - 1 ? dp(6) : 0, 0);
            button.setLayoutParams(params);
            int inset = size / 4;
            button.setPadding(inset, inset, inset, inset);
            button.setScaleType(ImageView.ScaleType.FIT_CENTER);
        }
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        gap = dp(12);
        actions.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        sideBySide = width - actions.getMeasuredWidth() - gap >= dp(320);
        int labelWidth = sideBySide ? width - actions.getMeasuredWidth() - gap : width;
        labels.measure(MeasureSpec.makeMeasureSpec(labelWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int height = sideBySide ? Math.max(labels.getMeasuredHeight(), actions.getMeasuredHeight())
                : labels.getMeasuredHeight() + gap + actions.getMeasuredHeight();
        setMeasuredDimension(width, height);
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        labels.layout(0, 0, labels.getMeasuredWidth(), labels.getMeasuredHeight());
        int left = sideBySide ? getWidth() - actions.getMeasuredWidth() : 0;
        int top = sideBySide ? 0 : labels.getBottom() + gap;
        actions.layout(left, top, left + actions.getMeasuredWidth(), top + actions.getMeasuredHeight());
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
