package com.antioblivionis.ansit;

import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {31, 34, 35})
public class AndroidCompatibilityTest {
    @Test public void selectsAvailableCaptureBuilderAndRetainsSecureCapturePolicy() throws Exception {
        Class<?> capture = CaptureCompat.captureType();
        assertEquals(Build.VERSION.SDK_INT < 34 ? "android.view.SurfaceControl" : "android.window.ScreenCapture", capture.getName());
        Class<?> builder = Class.forName(capture.getName() + "$DisplayCaptureArgs$Builder");
        Object args = builder.getConstructor(IBinder.class).newInstance(new Binder());
        CaptureCompat.excludeSystemOverlays(args);
        builder.getMethod("setCaptureSecureLayers", boolean.class).invoke(args, true);
        assertNotNull(builder.getMethod("build").invoke(args));
    }
    @Test
    @Config(sdk = 31)
    public void rootUsesLegacyPhysicalDisplayApiOnAndroid12() throws Exception {
        Method control = RootMain.class.getDeclaredMethod("getDisplayControl");
        control.setAccessible(true);
        assertEquals("android.view.SurfaceControl", ((Class<?>)control.invoke(null)).getName());
    }
    @Test
    @Config(sdk = 31)
    public void diagnosticCanOpenAndCloseWithoutNewCaptureCallbacks() {
        try (var activity = Robolectric.buildActivity(DiagnosticActivity.class)) {
            activity.setup().pause().stop().destroy();
        }
    }
}
