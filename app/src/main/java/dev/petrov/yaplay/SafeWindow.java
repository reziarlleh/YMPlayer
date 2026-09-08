package dev.petrov.yaplay;

import android.graphics.Color;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

final class SafeWindow {
    private SafeWindow() {}

    @SuppressWarnings("deprecation")
    static void install(Window window, View root) {
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        WindowCompat.getInsetsController(window, root).setAppearanceLightStatusBars(false);
        WindowCompat.getInsetsController(window, root).setAppearanceLightNavigationBars(false);
        int left = root.getPaddingLeft();
        int top = root.getPaddingTop();
        int right = root.getPaddingRight();
        int bottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            applyInsets(view, insets, Insets.of(left, top, right, bottom));
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(root);
    }

    static void applyInsets(View view, WindowInsetsCompat insets, Insets padding) {
        Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
        view.setPadding(padding.left + safe.left, padding.top + safe.top,
                padding.right + safe.right, padding.bottom + safe.bottom);
    }
}
