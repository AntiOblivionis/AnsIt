package com.antioblivionis.ansit;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class TriggerConfigTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @Test public void absentSettingsKeep650MillisecondDefault() throws Exception {
        TriggerConfig missing = TriggerConfig.read(temporary.getRoot().toPath().resolve("missing").toFile());
        assertEquals(650, missing.holdMillis);
        assertFalse(missing.swapTaps);
    }
    @Test public void acceptsExactIntegerValuesAcrossSupportedRange() throws Exception {
        for (int value : new int[]{200, 650, 925, 3000}) assertEquals(value, new TriggerConfig(value).holdMillis);
        assertThrows(TriggerConfig.Invalid.class, () -> new TriggerConfig(199));
        assertThrows(TriggerConfig.Invalid.class, () -> new TriggerConfig(3001));
        assertThrows(TriggerConfig.Invalid.class, () -> new TriggerConfig(-1));
    }
    @Test public void savedThresholdRoundTripsWithoutRounding() throws Exception {
        var file = temporary.newFile();
        Files.write(file.toPath(), new TriggerConfig(925).json().toString().getBytes(StandardCharsets.UTF_8));
        assertEquals(925, TriggerConfig.read(file).holdMillis);
    }
    @Test public void swapFlagDefaultsOffAndRoundTrips() throws Exception {
        var legacy = temporary.newFile();
        Files.write(legacy.toPath(), "{\"holdMillis\":925}".getBytes(StandardCharsets.UTF_8));
        assertFalse(TriggerConfig.read(legacy).swapTaps);
        var file = temporary.newFile();
        Files.write(file.toPath(), new TriggerConfig(925, true).json().toString().getBytes(StandardCharsets.UTF_8));
        TriggerConfig read = TriggerConfig.read(file);
        assertEquals(925, read.holdMillis);
        assertTrue(read.swapTaps);
    }
    @Test public void malformedSettingsCannotArmAnUnexpectedThreshold() throws Exception {
        for (String value : new String[]{"{}", "{\"holdMillis\":1}", "{\"holdMillis\":9000}", "{\"holdMillis\":\"650\"}", "{\"holdMillis\":650.5}"}) {
            var file = temporary.newFile();
            Files.write(file.toPath(), value.getBytes(StandardCharsets.UTF_8));
            assertThrows(Exception.class, () -> TriggerConfig.read(file));
        }
    }
    @Test public void malformedSwapFlagCannotChangeTheMapping() throws Exception {
        for (String value : new String[]{"{\"holdMillis\":650,\"swapTaps\":\"true\"}",
            "{\"holdMillis\":650,\"swapTaps\":1}", "{\"holdMillis\":650,\"swapTaps\":null}"}) {
            var file = temporary.newFile();
            Files.write(file.toPath(), value.getBytes(StandardCharsets.UTF_8));
            assertThrows(Exception.class, () -> TriggerConfig.read(file));
        }
    }
    @Test public void readinessRequiresACompleteExactAndBoundedPacket() {
        TriggerProtocol.Ready idle = TriggerProtocol.parseReady("ANSIT_TRIGGER_READY nonce-123 925 0 0");
        assertNotNull(idle); assertEquals(925, idle.holdMillis()); assertEquals("nonce-123", idle.instance());
        assertFalse(idle.mode()); assertEquals(0, idle.modeToken());
        TriggerProtocol.Ready active = TriggerProtocol.parseReady("ANSIT_TRIGGER_READY nonce-123 925 1 42");
        assertNotNull(active); assertTrue(active.mode()); assertEquals(42, active.modeToken());
        for (String line : new String[]{null, "", "ANSIT_TRIGGER_READY nonce-123",
            "ANSIT_TRIGGER_READY nonce-123 925", "ANSIT_TRIGGER_READY nonce-123 925 0",
            "ANSIT_TRIGGER_READY nonce-123 1 0 0", "ANSIT_TRIGGER_READY nonce-123 3001 0 0",
            "ANSIT_TRIGGER_READY nonce-123 925 1 0", "ANSIT_TRIGGER_READY nonce-123 925 0 7",
            "ANSIT_TRIGGER_READY nonce-123 925 2 1", "ANSIT_TRIGGER_READY nonce-123 925 1 -1",
            "ANSIT_TRIGGER_READY nonce-123 925 1 1 extra", "ANSIT_TRIGGER_READY nonce/123 925 0 0",
            "ANSIT_TRIGGER_READY nonce-123 925 0 x", "ANSIT_SERVICE_RUNNING"})
            assertNull(TriggerProtocol.parseReady(line));
        assertThrows(IllegalArgumentException.class, () -> TriggerProtocol.ready("nonce-123", 650, true, 0));
        assertThrows(IllegalArgumentException.class, () -> TriggerProtocol.ready("nonce-123", 650, false, 3));
    }
    @Test public void nextPacketIsExactAndScopedToTheActiveMode() {
        TriggerProtocol.Next next = TriggerProtocol.parseNext("TRIGGER_NEXT nonce-123 42 double");
        assertNotNull(next); assertEquals("nonce-123", next.instance()); assertEquals(42, next.modeToken()); assertTrue(next.doubleTap());
        TriggerProtocol.Next single = TriggerProtocol.parseNext("TRIGGER_NEXT nonce-123 42 single");
        assertNotNull(single); assertFalse(single.doubleTap());
        for (String line : new String[]{null, "", "TRIGGER_NEXT", "TRIGGER_NEXT nonce-123",
            "TRIGGER_NEXT nonce-123 0 single", "TRIGGER_NEXT nonce-123 -1 single",
            "TRIGGER_NEXT nonce/123 42 single", "TRIGGER_NEXT nonce-123 42 triple",
            "TRIGGER_NEXT nonce-123 42 single extra"})
            assertNull(TriggerProtocol.parseNext(line));
        assertThrows(IllegalArgumentException.class, () -> TriggerProtocol.next("nonce-123", 0, false));
        assertThrows(IllegalArgumentException.class, () -> TriggerProtocol.next("nonce/123", 1, false));
    }
    @Test public void scopedCommandsCannotMatchAnotherServiceEpoch() {
        assertTrue(TriggerProtocol.current("TOGGLE epoch-a", "TOGGLE ", "epoch-a"));
        assertFalse(TriggerProtocol.current("TOGGLE epoch-a", "TOGGLE ", "epoch-b"));
        assertFalse(TriggerProtocol.current("TOGGLE epoch-a ", "TOGGLE ", "epoch-a"));
        assertFalse(TriggerProtocol.current("TOGGLE", "TOGGLE ", "epoch-a"));
    }
}
