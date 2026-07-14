package dev.petrov.yaplay;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

final class FocusHighlightDrawable extends Drawable implements Drawable.Callback {
    private final Drawable base;
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF highlightBounds = new RectF();
    private final float cornerRadius;
    private final float inset;
    private final int focusFillColor;
    private final int pressedFillColor;

    private boolean enabled;
    private boolean focused;
    private boolean hovered;
    private boolean pressed;

    FocusHighlightDrawable(
            Drawable base,
            float cornerRadius,
            float inset,
            float strokeWidth,
            int strokeColor,
            int focusFillColor,
            int pressedFillColor
    ) {
        this.base = base == null ? null : base.mutate();
        this.cornerRadius = cornerRadius;
        this.inset = inset;
        this.focusFillColor = focusFillColor;
        this.pressedFillColor = pressedFillColor;
        fillPaint.setStyle(Paint.Style.FILL);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(strokeWidth);
        strokePaint.setColor(strokeColor);
        if (this.base != null) {
            this.base.setCallback(this);
        }
    }

    @Override
    public void draw(Canvas canvas) {
        if (base != null) {
            base.draw(canvas);
        }
        if (!enabled || (!focused && !hovered && !pressed)) {
            return;
        }
        Rect bounds = getBounds();
        highlightBounds.set(
                bounds.left + inset,
                bounds.top + inset,
                bounds.right - inset,
                bounds.bottom - inset
        );
        if (highlightBounds.width() <= 0f || highlightBounds.height() <= 0f) {
            return;
        }
        float radius = Math.min(cornerRadius, Math.min(highlightBounds.width(), highlightBounds.height()) / 2f);
        fillPaint.setColor(pressed ? pressedFillColor : focusFillColor);
        canvas.drawRoundRect(highlightBounds, radius, radius, fillPaint);
        canvas.drawRoundRect(highlightBounds, radius, radius, strokePaint);
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        if (base != null) {
            base.setBounds(bounds);
        }
    }

    @Override
    protected boolean onStateChange(int[] stateSet) {
        boolean newEnabled = false;
        boolean newFocused = false;
        boolean newHovered = false;
        boolean newPressed = false;
        for (int state : stateSet) {
            if (state == android.R.attr.state_enabled) {
                newEnabled = true;
            } else if (state == android.R.attr.state_focused) {
                newFocused = true;
            } else if (state == android.R.attr.state_hovered) {
                newHovered = true;
            } else if (state == android.R.attr.state_pressed) {
                newPressed = true;
            }
        }
        boolean changed = enabled != newEnabled
                || focused != newFocused
                || hovered != newHovered
                || pressed != newPressed;
        enabled = newEnabled;
        focused = newFocused;
        hovered = newHovered;
        pressed = newPressed;
        boolean baseChanged = base != null && base.isStateful() && base.setState(stateSet);
        if (changed || baseChanged) {
            invalidateSelf();
        }
        return changed || baseChanged;
    }

    @Override
    public boolean isStateful() {
        return true;
    }

    @Override
    public boolean getPadding(Rect padding) {
        return base != null && base.getPadding(padding);
    }

    @Override
    public void setAlpha(int alpha) {
        fillPaint.setAlpha(alpha);
        strokePaint.setAlpha(alpha);
        if (base != null) {
            base.setAlpha(alpha);
        }
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        if (base != null) {
            base.setColorFilter(colorFilter);
        }
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public void invalidateDrawable(Drawable who) {
        invalidateSelf();
    }

    @Override
    public void scheduleDrawable(Drawable who, Runnable what, long when) {
        scheduleSelf(what, when);
    }

    @Override
    public void unscheduleDrawable(Drawable who, Runnable what) {
        unscheduleSelf(what);
    }
}
