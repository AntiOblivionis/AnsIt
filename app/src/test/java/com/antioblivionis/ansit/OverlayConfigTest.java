package com.antioblivionis.ansit;

import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class OverlayConfigTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final float EPS = .00001f;

    @Test public void clampsInvalidBoundsAndOpacity() {
        OverlayConfig c = new OverlayConfig(-2, 4, 3, -1, 6);
        assertEquals(0, c.left, EPS); assertEquals(1, c.width, EPS);
        assertEquals(OverlayConfig.MIN_HEIGHT, c.height, EPS);
        assertEquals(1 - c.height, c.top, EPS); assertEquals(1, c.transparency, EPS);
    }
    @Test public void rejectsNonFiniteInputInsteadOfPublishingBrokenGeometry() {
        assertThrows(IllegalArgumentException.class, () -> new OverlayConfig(Float.NaN, 0, .5f, .3f, .2f));
        assertThrows(IllegalArgumentException.class, () -> new OverlayConfig(0, 0, Float.POSITIVE_INFINITY, .3f, .2f));
        assertThrows(IllegalArgumentException.class, () -> OverlayConfig.defaults().withTransparency(Float.NaN));
    }
    @Test public void movingAtEdgesKeepsSizeAndEntireFrameVisible() {
        OverlayConfig c = OverlayConfig.defaults().move(10, -10);
        assertEquals(1 - c.width, c.left, EPS); assertEquals(0, c.top, EPS);
        assertEquals(OverlayConfig.defaults().width, c.width, EPS);
        assertEquals(OverlayConfig.defaults().height, c.height, EPS);
    }
    @Test public void resizingTopLeftAnchorsOppositeCorner() {
        OverlayConfig before = OverlayConfig.defaults();
        OverlayConfig after = before.resize(OverlayConfig.LEFT | OverlayConfig.TOP, .1f, .05f);
        assertEquals(before.left + before.width, after.left + after.width, EPS);
        assertEquals(before.top + before.height, after.top + after.height, EPS);
        assertTrue(after.width < before.width); assertTrue(after.height < before.height);
    }
    @Test public void resizingBottomRightAnchorsOriginAndEnforcesMinimumSize() {
        OverlayConfig before = OverlayConfig.defaults();
        OverlayConfig after = before.resize(OverlayConfig.RIGHT | OverlayConfig.BOTTOM, -10, -10);
        assertEquals(before.left, after.left, EPS); assertEquals(before.top, after.top, EPS);
        assertEquals(OverlayConfig.MIN_WIDTH, after.width, EPS);
        assertEquals(OverlayConfig.MIN_HEIGHT, after.height, EPS);
    }
    @Test public void hostileRepeatedDragsRemainInsidePortraitAndLandscapeScreens() {
        Random random = new Random(513);
        OverlayConfig c = OverlayConfig.defaults();
        int[] edges = { 3, 6, 9, 12 };
        for (int i = 0; i < 2000; i++) {
            float dx = random.nextFloat() * 4 - 2, dy = random.nextFloat() * 4 - 2;
            c = i % 2 == 0 ? c.move(dx, dy) : c.resize(edges[i % 4], dx, dy);
            for (int[] size : new int[][]{{1200, 2608}, {2608, 1200}, {1, 1}, {320, 400}}) {
                OverlayConfig.Pixels p = c.pixels(size[0], size[1]);
                assertTrue(p.x() >= 0 && p.y() >= 0 && p.width() > 0 && p.height() > 0);
                assertTrue(p.x() + p.width() <= size[0]); assertTrue(p.y() + p.height() <= size[1]);
            }
        }
    }
    @Test public void opacityEndpointsAndMidpointMatchPainterAlpha() {
        assertEquals(255, OverlayConfig.defaults().withTransparency(0).backgroundAlpha());
        assertEquals(128, OverlayConfig.defaults().withTransparency(.5f).backgroundAlpha());
        assertEquals(0, OverlayConfig.defaults().withTransparency(1).backgroundAlpha());
    }
    @Test public void serializesAndReloadsTheExactSavedLayout() throws Exception {
        OverlayConfig c = new OverlayConfig(.17f, .42f, .5f, .31f, .73f);
        assertEquals(c, OverlayConfig.fromJson(new JSONObject(c.json().toString())));
        var file = temporary.newFile();
        Files.write(file.toPath(), c.json().toString().getBytes(StandardCharsets.UTF_8));
        assertEquals(c, OverlayConfig.read(file));
    }
    @Test public void readingPublishedSettingsDoesNotDeleteAnotherWritersStagingFile() throws Exception {
        var file = temporary.newFile("overlay-config.json");
        var staging = file.toPath().resolveSibling("overlay-config.json.new");
        OverlayConfig old = OverlayConfig.defaults(), replacement = old.move(.02f, .1f);
        Files.write(file.toPath(), old.json().toString().getBytes(StandardCharsets.UTF_8));
        Files.write(staging, replacement.json().toString().getBytes(StandardCharsets.UTF_8));
        assertEquals(old, OverlayConfig.read(file));
        assertTrue(Files.exists(staging));
        assertEquals(replacement.json().toString(), new String(Files.readAllBytes(staging), StandardCharsets.UTF_8));
    }
    @Test public void missingFileUsesDefaultButOversizedOrIncompleteFilesAreRejected() throws Exception {
        assertEquals(OverlayConfig.defaults(), OverlayConfig.read(temporary.getRoot().toPath().resolve("missing").toFile()));
        var file = temporary.newFile();
        Files.write(file.toPath(), "x".repeat(4097).getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> OverlayConfig.read(file));
        assertThrows(Exception.class, () -> OverlayConfig.fromJson(new JSONObject("{}")));
    }
}
