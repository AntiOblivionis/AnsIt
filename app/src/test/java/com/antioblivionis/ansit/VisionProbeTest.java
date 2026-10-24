package com.antioblivionis.ansit;

import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.util.Base64;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class VisionProbeTest {
    @Test public void generatedChallengeIsARealNonblankPngWithASixDigitCode() {
        VisionProbe probe = VisionProbe.create();
        assertTrue(probe.code.matches("[0-9]{6}"));
        byte[] png = Base64.decode(probe.image, Base64.NO_WRAP);
        assertEquals(0x89, png[0] & 255);
        var bitmap = BitmapFactory.decodeByteArray(png, 0, png.length);
        assertNotNull(bitmap); assertEquals(512, bitmap.getWidth()); assertEquals(192, bitmap.getHeight());
        int dark = 0;
        for (int y = 0; y < bitmap.getHeight(); y++) for (int x = 0; x < bitmap.getWidth(); x++)
            if (Color.red(bitmap.getPixel(x, y)) < 64) dark++;
        assertTrue(dark > 100); bitmap.recycle();
    }
}
