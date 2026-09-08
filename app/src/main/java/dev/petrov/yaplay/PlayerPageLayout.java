package dev.petrov.yaplay;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;

/** Measures against the usable viewport, not physical display pixels or orientation alone. */
final class PlayerPageLayout extends ViewGroup {
    private int viewportHeight;
    private int gap;
    private int coverSize;
    private int bodyTop;
    private boolean wide;

    PlayerPageLayout(Context context) {
        super(context);
    }

    static ScrollView scroll(Context context, PlayerPageLayout page) {
        ScrollView scroll = new ScrollView(context) {
            @Override protected void onMeasure(int width, int height) {
                page.viewportHeight = MeasureSpec.getSize(height);
                super.onMeasure(width, height);
            }
        };
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(OVER_SCROLL_NEVER);
        scroll.addView(page, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int height = Math.max(1, viewportHeight);
        wide = width >= dp(600) && width > height;
        boolean compact = wide && height < dp(420);
        gap = dp(wide && !compact ? 20 : 12);
        int inner = Math.max(1, width - gap * 2);
        View header = getChildAt(0);
        View cover = getChildAt(1);
        View sources = getChildAt(2);
        View info = getChildAt(3);
        header.measure(exact(inner), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        sources.measure(exact(inner), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        bodyTop = gap + header.getMeasuredHeight() + dp(8) + sources.getMeasuredHeight() + gap;
        info.findViewWithTag("player-mode").setVisibility(compact ? GONE : VISIBLE);
        info.findViewWithTag("player-queue").setVisibility(compact ? GONE : VISIBLE);
        ((PlayerButtonLayout) info.findViewWithTag("player-transport")).setCompact(compact);
        int bodyHeight = Math.max(1, height - bodyTop - gap);
        int infoWidth;
        if (wide) {
            coverSize = Math.max(dp(112), Math.min(bodyHeight,
                    Math.min(Math.round(inner * .40f), inner - gap - dp(328))));
            infoWidth = Math.max(1, inner - coverSize - gap);
        } else {
            infoWidth = inner;
        }
        info.measure(exact(infoWidth), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        if (!wide) {
            coverSize = Math.max(dp(112), Math.min(Math.min(inner, dp(420)),
                    bodyHeight - info.getMeasuredHeight() - gap));
        }
        cover.measure(exact(coverSize), exact(coverSize));
        int naturalBody = wide ? Math.max(coverSize, info.getMeasuredHeight())
                : coverSize + gap + info.getMeasuredHeight();
        setMeasuredDimension(width, Math.max(height, bodyTop + naturalBody + gap));
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        View header = getChildAt(0);
        View cover = getChildAt(1);
        View sources = getChildAt(2);
        View info = getChildAt(3);
        header.layout(gap, gap, getWidth() - gap, gap + header.getMeasuredHeight());
        int sourcesTop = header.getBottom() + dp(8);
        sources.layout(gap, sourcesTop, getWidth() - gap, sourcesTop + sources.getMeasuredHeight());
        int bodyHeight = getHeight() - bodyTop - gap;
        if (wide) {
            int artTop = bodyTop + (bodyHeight - coverSize) / 2;
            cover.layout(gap, artTop, gap + coverSize, artTop + coverSize);
            int infoTop = bodyTop + (bodyHeight - info.getMeasuredHeight()) / 2;
            info.layout(gap * 2 + coverSize, infoTop, getWidth() - gap, infoTop + info.getMeasuredHeight());
        } else {
            int contentHeight = coverSize + gap + info.getMeasuredHeight();
            int artTop = bodyTop + Math.max(0, (bodyHeight - contentHeight) / 2);
            int artLeft = (getWidth() - coverSize) / 2;
            cover.layout(artLeft, artTop, artLeft + coverSize, artTop + coverSize);
            int infoTop = artTop + coverSize + gap;
            info.layout(gap, infoTop, getWidth() - gap, infoTop + info.getMeasuredHeight());
        }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private static int exact(int value) { return MeasureSpec.makeMeasureSpec(value, MeasureSpec.EXACTLY); }
}
