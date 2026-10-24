package com.antioblivionis.ansit;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AnswerPainterTest {
    @Test public void renderedBackgroundAlphaMatchesSavedTransparency() {
        for (float transparency : new float[]{0, .5f, 1}) {
            Bitmap bitmap = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888);
            OverlayConfig config = OverlayConfig.defaults().withTransparency(transparency);
            AnswerPainter.draw(new Canvas(bitmap), "", 400, 300, 800, 1200, config);
            assertEquals(config.backgroundAlpha(), Color.alpha(bitmap.getPixel(200, 150)));
            bitmap.recycle();
        }
    }
    @Test public void fullyTransparentBackgroundStillDrawsReadableText() {
        Bitmap bitmap = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888);
        AnswerPainter.draw(new Canvas(bitmap), "Answer", 400, 300, 800, 1200, OverlayConfig.defaults().withTransparency(1));
        int[] pixels = new int[400 * 300]; bitmap.getPixels(pixels, 0, 400, 0, 0, 400, 300);
        boolean foundText = false;
        for (int pixel : pixels) if (Color.alpha(pixel) > 0) { foundText = true; break; }
        assertTrue(foundText); bitmap.recycle();
    }
    @Test public void rotatingDisplayDoesNotEnlargeTextAndPadding() {
        Bitmap portrait = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888);
        Bitmap landscape = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888);
        AnswerPainter.draw(new Canvas(portrait), "Same text", 400, 300, 1200, 2608, OverlayConfig.defaults());
        AnswerPainter.draw(new Canvas(landscape), "Same text", 400, 300, 2608, 1200, OverlayConfig.defaults());
        assertTrue(portrait.sameAs(landscape));
        portrait.recycle(); landscape.recycle();
    }
}
