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
        assertEquals(650, TriggerConfig.read(temporary.getRoot().toPath().resolve("missing").toFile()).holdMillis);
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
    @Test public void malformedSettingsCannotArmAnUnexpectedThreshold() throws Exception {
        for (String value : new String[]{"{}", "{\"holdMillis\":1}", "{\"holdMillis\":9000}", "{\"holdMillis\":\"650\"}", "{\"holdMillis\":650.5}"}) {
            var file = temporary.newFile();
            Files.write(file.toPath(), value.getBytes(StandardCharsets.UTF_8));
            assertThrows(Exception.class, () -> TriggerConfig.read(file));
        }
    }
    @Test public void readinessRequiresACompleteExactAndBoundedPacket() {
        TriggerProtocol.Ready ready = TriggerProtocol.parseReady("ANSIT_TRIGGER_READY nonce-123 925");
        assertNotNull(ready); assertEquals(925, ready.holdMillis()); assertEquals("nonce-123", ready.instance());
        for (String line : new String[]{null, "", "ANSIT_TRIGGER_READY nonce-123", "ANSIT_TRIGGER_READY nonce-123 1",
            "ANSIT_TRIGGER_READY nonce-123 3001", "ANSIT_TRIGGER_READY nonce-123 650 extra",
            "ANSIT_TRIGGER_READY nonce/123 650", "ANSIT_SERVICE_RUNNING"})
            assertNull(TriggerProtocol.parseReady(line));
    }
    @Test public void scopedCommandsCannotMatchAnotherServiceEpoch() {
        assertTrue(TriggerProtocol.current("TOGGLE epoch-a", "TOGGLE ", "epoch-a"));
        assertFalse(TriggerProtocol.current("TOGGLE epoch-a", "TOGGLE ", "epoch-b"));
        assertFalse(TriggerProtocol.current("TOGGLE epoch-a ", "TOGGLE ", "epoch-a"));
        assertFalse(TriggerProtocol.current("TOGGLE", "TOGGLE ", "epoch-a"));
    }
}
