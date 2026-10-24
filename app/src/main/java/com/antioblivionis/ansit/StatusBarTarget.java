package com.antioblivionis.ansit;

import android.content.res.Resources;
import android.graphics.Rect;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

/** Uses Android window types and root-local geometry; no vendor SystemUI class is required. */
final class StatusBarTarget {
    static boolean isStatusBarWindow(View root) {
        if (root != root.getRootView()) return false;
        if (!(root.getLayoutParams() instanceof WindowManager.LayoutParams params)
                || params.type != WindowManager.LayoutParams.TYPE_STATUS_BAR) return false;
        Display display = root.getDisplay();
        // RootMain captures the default display; never trigger it from a different display's bar.
        return display != null && display.getDisplayId() == Display.DEFAULT_DISPLAY;
    }

    static boolean hit(View root, float x, float y) {
        if (!isStatusBarWindow(root) || !root.isShown() || root.getAlpha() <= 0
                || !Float.isFinite(x) || !Float.isFinite(y)) return false;
        Rect band = new Rect();
        if (!root.getGlobalVisibleRect(band)) return false;
        int heightId = root.getResources().getIdentifier("status_bar_height", "dimen", "android");
        int height = heightId == 0 ? dp(root, 24) : root.getResources().getDimensionPixelSize(heightId);
        // Some SystemUI implementations expand this window while opening a panel.
        band.bottom = Math.min(band.bottom, height);
        if (band.isEmpty()) return false;
        Rect target = batteryBounds(root, band);
        if (target == null) {
            target = new Rect(band);
            target.left = target.right - Math.min(dp(root, 72), Math.max(1, target.width() / 3));
        } else {
            target.inset(-dp(root, 18), 0);
            target.intersect(band);
        }
        // dispatchPointerEvent's x/y and getGlobalVisibleRect are both relative to this root.
        return x >= target.left && x < target.right && y >= target.top && y < target.bottom;
    }

    private static Rect batteryBounds(View view, Rect band) {
        if (view.getVisibility() != View.VISIBLE || view.getAlpha() <= 0) return null;
        if (isBattery(view)) {
            Rect bounds = new Rect();
            if (view.getGlobalVisibleRect(bounds) && bounds.intersect(band)) return bounds;
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                Rect found = batteryBounds(group.getChildAt(i), band);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static boolean isBattery(View view) {
        if (view.getId() != View.NO_ID) {
            try {
                String name = view.getResources().getResourceEntryName(view.getId());
                if (name.equals("battery") || name.equals("battery_view")
                        || name.equals("battery_meter") || name.equals("battery_meter_view")) return true;
            } catch (Resources.NotFoundException ignored) { }
        }
        for (Class<?> type = view.getClass(); type != null && type != View.class; type = type.getSuperclass()) {
            String name = type.getSimpleName();
            if (name.endsWith("BatteryMeterView") || name.endsWith("BatteryView")) return true;
        }
        return false;
    }

    private static int dp(View root, int value) {
        return Math.max(1, Math.round(value * root.getResources().getDisplayMetrics().density));
    }
    private StatusBarTarget() { }
}
