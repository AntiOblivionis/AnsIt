package com.antioblivionis.ansit;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.Locale;

/** A newly generated image challenge; its expected value never appears in the prompt. */
final class VisionProbe {
    final String code, image;
    VisionProbe(String code, String image) { this.code = code; this.image = image; }
    static VisionProbe create() {
        String code = String.format(Locale.ROOT, "%06d", new SecureRandom().nextInt(1_000_000));
        Bitmap bitmap = Bitmap.createBitmap(512, 192, Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(bitmap); canvas.drawColor(Color.WHITE);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.BLACK); paint.setTextSize(96); paint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
            canvas.drawText(code, 40, 130, paint);
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, png);
            return new VisionProbe(code, Base64.encodeToString(png.toByteArray(), Base64.NO_WRAP));
        } finally { bitmap.recycle(); }
    }
}
