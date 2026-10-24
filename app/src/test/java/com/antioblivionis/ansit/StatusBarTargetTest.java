package com.antioblivionis.ansit;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.view.Display;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDisplayManager;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {31, 35}, qualifiers = "w400dp-h800dp-mdpi")
public class StatusBarTargetTest {
    private static class Bar extends FrameLayout {
        Display display;
        boolean shown = true;
        int touchDispatches;
        Bar(Context context) {
            super(context);
            display = context.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
            setLayoutParams(new WindowManager.LayoutParams(WindowManager.LayoutParams.TYPE_STATUS_BAR));
        }
        @Override public boolean isShown() { return shown; }
        @Override public Display getDisplay() { return display; }
        @Override public boolean dispatchTouchEvent(MotionEvent event) { touchDispatches++; return true; }
    }
    private static class BatteryMeterView extends View { BatteryMeterView(Context context) { super(context); } }
    private static class CustomBattery extends BatteryMeterView { CustomBattery(Context context) { super(context); } }

    private Bar bar(int height) {
        Bar bar = new Bar(RuntimeEnvironment.getApplication());
        bar.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        bar.layout(0, 0, 400, height);
        return bar;
    }
    private View battery(Bar bar, int left, int top) {
        View battery = new CustomBattery(bar.getContext());
        bar.addView(battery, new FrameLayout.LayoutParams(24, 16));
        battery.layout(left, top, left + 24, top + 16);
        return battery;
    }

    @Test public void commonFrameworkEntryForwardsTouchToTheWindowRootOnce() throws Exception {
        var method = View.class.getDeclaredMethod("dispatchPointerEvent", MotionEvent.class);
        assertEquals(boolean.class, method.getReturnType());
        Bar bar = bar(24);
        MotionEvent event = MotionEvent.obtain(100, 100, MotionEvent.ACTION_DOWN, 375, 10, 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try { assertEquals(true, method.invoke(bar, event)); assertEquals(1, bar.touchDispatches); }
        finally { event.recycle(); }
    }
    @Test public void frameworkPointerCallerCanBeLocatedForDeoptimization() throws Exception {
        Class<?> stage = Class.forName("android.view.ViewRootImpl$ViewPostImeInputStage");
        boolean pointer = false, process = false;
        for (var method : stage.getDeclaredMethods()) {
            pointer |= method.getName().equals("processPointerEvent");
            process |= method.getName().equals("onProcess");
        }
        assertTrue(pointer); assertTrue(process);
    }
    @Test public void acceptsStatusBarWithNoVendorClassesOrBatteryIdentifiers() {
        Bar bar = bar(24);
        assertTrue(StatusBarTarget.isStatusBarWindow(bar));
        assertTrue(StatusBarTarget.hit(bar, 375, 10));
        assertFalse(StatusBarTarget.hit(bar, 250, 10));
    }
    @Test public void doesNotObserveApplicationShadeNavigationOrOverlayWindows() {
        Bar bar = bar(24);
        for (int type : new int[]{WindowManager.LayoutParams.TYPE_BASE_APPLICATION,
                2019 /* navigation bar */,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, 2040 /* notification shade */}) {
            ((WindowManager.LayoutParams)bar.getLayoutParams()).type = type;
            assertFalse(StatusBarTarget.isStatusBarWindow(bar));
            assertFalse(StatusBarTarget.hit(bar, 375, 10));
        }
    }
    @Test public void batteryAtLeftIsDetectedEvenWhenItsSubclassAndResourceNameAreDifferent() {
        Bar bar = bar(24);
        battery(bar, 20, 4);
        assertTrue(StatusBarTarget.hit(bar, 10, 10));
        assertFalse(StatusBarTarget.hit(bar, 375, 10));
        assertFalse(StatusBarTarget.hit(bar, 90, 10));
    }
    @Test public void batteryAtRightUsesActualBoundsAndDoesNotExpandToTheWholeFallbackArea() {
        Bar bar = bar(24);
        battery(bar, 368, 4);
        assertTrue(StatusBarTarget.hit(bar, 375, 10));
        assertTrue(StatusBarTarget.hit(bar, 352, 10));
        assertFalse(StatusBarTarget.hit(bar, 335, 10));
    }
    @Test public void hiddenBatteryDoesNotPreventTheVisibleBatteryFromBeingUsed() {
        Bar bar = bar(24);
        battery(bar, 20, 4).setVisibility(View.GONE);
        battery(bar, 368, 4);
        assertFalse(StatusBarTarget.hit(bar, 25, 10));
        assertTrue(StatusBarTarget.hit(bar, 375, 10));
    }
    @Test public void expandedStatusBarWindowCannotTriggerFromPanelContents() {
        Bar bar = bar(800);
        battery(bar, 368, 300);
        assertTrue(StatusBarTarget.hit(bar, 375, 10));
        assertFalse(StatusBarTarget.hit(bar, 375, 305));
        assertFalse(StatusBarTarget.hit(bar, 375, 100));
    }
    @Test public void hiddenWindowAndOutOfBoundsCoordinatesDoNotArmAGesture() {
        Bar bar = bar(24);
        assertFalse(StatusBarTarget.hit(bar, 400, 10));
        assertFalse(StatusBarTarget.hit(bar, 375, -1));
        assertFalse(StatusBarTarget.hit(bar, Float.NaN, 10));
        bar.shown = false;
        assertFalse(StatusBarTarget.hit(bar, 375, 10));
        bar.shown = true;
        bar.setAlpha(0);
        assertFalse(StatusBarTarget.hit(bar, 375, 10));
    }
    @Test public void ignoresOtherDisplaysBecauseCaptureTargetsTheDefaultDisplay() {
        Bar bar = bar(24);
        int id = ShadowDisplayManager.addDisplay("w800dp-h600dp");
        bar.display = bar.getContext().getSystemService(DisplayManager.class).getDisplay(id);
        assertFalse(StatusBarTarget.isStatusBarWindow(bar));
        assertFalse(StatusBarTarget.hit(bar, 375, 10));
    }
    @Test public void eventHitCoordinatesStayRootLocalWhenWindowPositionChanges() {
        Bar bar = bar(24);
        battery(bar, 368, 4);
        bar.offsetLeftAndRight(100);
        bar.offsetTopAndBottom(60);
        assertTrue(StatusBarTarget.hit(bar, 375, 10));
        assertFalse(StatusBarTarget.hit(bar, 475, 70));
    }
}
