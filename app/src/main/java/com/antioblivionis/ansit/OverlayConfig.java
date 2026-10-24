package com.antioblivionis.ansit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.json.JSONObject;

/** Screen-relative geometry shared by the editor and the excluded root layer. */
public final class OverlayConfig {
    static final String FILE_NAME = "overlay-config.json";
    static final float MIN_WIDTH = .28f, MIN_HEIGHT = .10f;
    static final int LEFT = 1, TOP = 2, RIGHT = 4, BOTTOM = 8;
    public final float left, top, width, height, transparency;

    public OverlayConfig(float left, float top, float width, float height, float transparency) {
        if (!Float.isFinite(left) || !Float.isFinite(top) || !Float.isFinite(width)
            || !Float.isFinite(height) || !Float.isFinite(transparency))
            throw new IllegalArgumentException("Non-finite overlay geometry");
        this.width = clamp(width, MIN_WIDTH, 1f);
        this.height = clamp(height, MIN_HEIGHT, 1f);
        this.left = clamp(left, 0f, 1f - this.width);
        this.top = clamp(top, 0f, 1f - this.height);
        this.transparency = clamp(transparency, 0f, 1f);
    }
    public static OverlayConfig defaults() { return new OverlayConfig(.06f, .20f, .88f, .30f, .10f); }
    public OverlayConfig move(float dx, float dy) {
        return new OverlayConfig(left + dx, top + dy, width, height, transparency);
    }
    public OverlayConfig resize(int edges, float dx, float dy) {
        float l = left, t = top, r = left + width, b = top + height;
        if ((edges & LEFT) != 0) l = clamp(l + dx, 0f, r - MIN_WIDTH);
        if ((edges & RIGHT) != 0) r = clamp(r + dx, l + MIN_WIDTH, 1f);
        if ((edges & TOP) != 0) t = clamp(t + dy, 0f, b - MIN_HEIGHT);
        if ((edges & BOTTOM) != 0) b = clamp(b + dy, t + MIN_HEIGHT, 1f);
        return new OverlayConfig(l, t, r - l, b - t, transparency);
    }
    public OverlayConfig withTransparency(float value) {
        return new OverlayConfig(left, top, width, height, value);
    }
    public int backgroundAlpha() { return Math.round((1f - transparency) * 255); }

    public record Pixels(int x, int y, int width, int height) { }
    public Pixels pixels(int screenWidth, int screenHeight) {
        if (screenWidth < 1 || screenHeight < 1) throw new IllegalArgumentException("Invalid screen size");
        int w = Math.max(1, Math.min(screenWidth, Math.round(width * screenWidth)));
        int h = Math.max(1, Math.min(screenHeight, Math.round(height * screenHeight)));
        return new Pixels(Math.max(0, Math.min(screenWidth - w, Math.round(left * screenWidth))),
            Math.max(0, Math.min(screenHeight - h, Math.round(top * screenHeight))), w, h);
    }
    static OverlayConfig read(File file) throws Exception {
        if (!file.isFile()) return defaults();
        // Read the published base file only. AtomicFile readers can delete another process's active .new file.
        byte[] bytes;
        try (var input = new java.io.FileInputStream(file)) { bytes = StreamCompat.readNBytes(input, 4097); }
        if (bytes.length > 4096) throw new IllegalArgumentException("Overlay settings too large");
        return fromJson(new JSONObject(new String(bytes, StandardCharsets.UTF_8)));
    }
    static OverlayConfig fromJson(JSONObject json) throws Exception {
        return new OverlayConfig((float)json.getDouble("left"), (float)json.getDouble("top"),
            (float)json.getDouble("width"), (float)json.getDouble("height"), (float)json.getDouble("transparency"));
    }
    JSONObject json() throws Exception {
        return new JSONObject().put("left", left).put("top", top).put("width", width)
            .put("height", height).put("transparency", transparency);
    }
    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
    @Override public boolean equals(Object other) {
        if (!(other instanceof OverlayConfig o)) return false;
        return left == o.left && top == o.top && width == o.width && height == o.height && transparency == o.transparency;
    }
    @Override public int hashCode() { return Objects.hash(left, top, width, height, transparency); }
}
