package com.antioblivionis.ansit;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

/** The preview and real root surface use the same text fitting, padding and background alpha. */
final class AnswerPainter {
    static final String SAMPLE = "答案预览\n\n这里会显示本次识屏的解答。\n\n拖动框体改变位置，拖动四角改变大小。背景透明度可以在下方调整。\n\n设置会保留，下次解答使用你保存的布局。";

    static void draw(Canvas canvas, String text, int width, int height, int screenWidth, int screenHeight, OverlayConfig config) {
        float scale = Math.min(screenWidth, screenHeight) / 1200f;
        float paddingX = 34 * scale, paddingY = 30 * scale;
        int textWidth = Math.max(1, (int)(width - paddingX * 2));
        int textHeight = Math.max(1, (int)(height - paddingY * 2));
        TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTypeface(Typeface.DEFAULT); textPaint.setColor(Color.WHITE);
        String display = text.length() > 12000 ? text.substring(0, 12000) + "…（答案过长）" : text;
        StaticLayout layout = null;
        for (int size = 40; size >= 28; size -= 2) {
            textPaint.setTextSize(size * scale);
            layout = StaticLayout.Builder.obtain(display, 0, display.length(), textPaint, textWidth)
                .setIncludePad(false).setLineSpacing(6 * scale, 1).build();
            if (layout.getHeight() <= textHeight) break;
        }
        if (layout.getHeight() > textHeight) {
            int maxLines = Math.max(1, (int)(textHeight / (textPaint.getFontSpacing() + 6 * scale)));
            layout = StaticLayout.Builder.obtain(display, 0, display.length(), textPaint, textWidth)
                .setIncludePad(false).setLineSpacing(6 * scale, 1).setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END).build();
        }
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.rgb(34, 38, 48)); paint.setAlpha(config.backgroundAlpha());
        canvas.drawRoundRect(0, 0, width, height, 28 * scale, 28 * scale, paint);
        int saved = canvas.save();
        try {
            canvas.clipRect(0, 0, width, height);
            canvas.translate(paddingX, paddingY); layout.draw(canvas);
        } finally { canvas.restoreToCount(saved); }
    }
    private AnswerPainter() { }
}
