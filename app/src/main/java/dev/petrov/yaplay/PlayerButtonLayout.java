package dev.petrov.yaplay;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

/** Keeps transport order intact; only the auxiliary EQ may move to its own row. */
final class PlayerButtonLayout extends ViewGroup {
    private final boolean transport;
    private final int playIndex;
    private int columns;
    private int cell;
    private int gap;
    private int playExtra;
    private int rowHeight;
    private boolean compact;

    void setCompact(boolean value) { compact = value; }

    PlayerButtonLayout(Context context, boolean transport) {
        this(context, transport, 2);
    }

    PlayerButtonLayout(Context context, boolean transport, int playIndex) {
        super(context);
        this.transport = transport;
        this.playIndex = playIndex;
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        gap = dp(transport ? 6 : 8);
        if (transport) {
            columns = Math.min(getChildCount(), width >= dp(296) ? 6 : 5);
            if (width < dp(326)) gap = 0;
            playExtra = dp(8);
            cell = Math.min(dp(compact ? 60 : 88), (width - gap * (columns - 1) - playExtra) / columns);
            rowHeight = cell + playExtra;
        } else {
            columns = width >= dp(540) ? 4 : 2;
            cell = (width - gap * (columns - 1)) / columns;
            rowHeight = dp(48);
        }
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (transport) {
                int size = cell + (i == playIndex ? playExtra : 0);
                child.measure(exact(size), exact(size));
                int padding = Math.round(size * .24f);
                child.setPadding(padding, padding, padding, padding);
                if (child instanceof ImageView) ((ImageView) child).setScaleType(ImageView.ScaleType.FIT_CENTER);
            } else {
                child.measure(exact(cell), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                rowHeight = Math.max(rowHeight, child.getMeasuredHeight());
            }
        }
        if (!transport) for (int i = 0; i < getChildCount(); i++) getChildAt(i).measure(exact(cell), exact(rowHeight));
        int rows = (getChildCount() + columns - 1) / columns;
        setMeasuredDimension(width, rows * rowHeight + (rows - 1) * gap);
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int row = 0; row * columns < getChildCount(); row++) {
            int count = Math.min(columns, getChildCount() - row * columns);
            int total = count * cell + (count - 1) * gap + (transport && row == 0 ? playExtra : 0);
            int x = (getWidth() - total) / 2;
            for (int col = 0; col < count; col++) {
                View child = getChildAt(row * columns + col);
                int y = row * (rowHeight + gap) + (rowHeight - child.getMeasuredHeight()) / 2;
                child.layout(x, y, x + child.getMeasuredWidth(), y + child.getMeasuredHeight());
                x += child.getMeasuredWidth() + gap;
            }
        }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private static int exact(int value) { return MeasureSpec.makeMeasureSpec(Math.max(1, value), MeasureSpec.EXACTLY); }
}
