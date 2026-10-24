package com.antioblivionis.ansit;

/** Platform capture moved from SurfaceControl to ScreenCapture, then ScreenCaptureInternal. */
final class CaptureCompat {
    static Class<?> captureType() throws ClassNotFoundException {
        for (String name : new String[]{"android.window.ScreenCaptureInternal",
                "android.window.ScreenCapture", "android.view.SurfaceControl"}) {
            try { return Class.forName(name); }
            catch (ClassNotFoundException absent) { }
        }
        throw new ClassNotFoundException("No platform screen capture API");
    }

    static void excludeSystemOverlays(Object builder) throws ReflectiveOperationException {
        try { builder.getClass().getMethod("setIncludeSystemOverlays", boolean.class).invoke(builder, false); }
        catch (NoSuchMethodException olderPlatform) {
            // Older capture APIs always exclude SKIP_SCREENSHOT layers and have no opt-in switch.
        }
    }
    private CaptureCompat() { }
}
