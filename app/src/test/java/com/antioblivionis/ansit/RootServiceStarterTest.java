package com.antioblivionis.ansit;

import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.*;

public class RootServiceStarterTest {
    @Test public void acceptsOnlyExplicitLiveServiceResult() throws Exception {
        assertEquals(RootServiceStarter.State.RUNNING, RootServiceStarter.parseState("unrelated log\nANSIT_SERVICE_RUNNING\n", 0));
    }
    @Test public void acceptsConfirmedStoppedResult() throws Exception {
        assertEquals(RootServiceStarter.State.STOPPED, RootServiceStarter.parseState("ANSIT_SERVICE_STOPPED\n", 0));
    }
    @Test public void denialTimeoutAndMalformedResponseAreNotStopped() {
        assertThrows(IOException.class, () -> RootServiceStarter.parseState("permission denied", 1));
        assertThrows(IOException.class, () -> RootServiceStarter.parseState("", 0));
        assertThrows(IOException.class, () -> RootServiceStarter.parseState("timeout", 0));
        assertThrows(IOException.class, () -> RootServiceStarter.parseState("ANSIT_SERVICE_STOPPED", 1));
    }
    @Test public void rejectsConflictingOrEmbeddedStateMarkers() {
        assertThrows(IOException.class, () -> RootServiceStarter.parseState("ANSIT_SERVICE_RUNNING\nANSIT_SERVICE_STOPPED", 0));
        assertThrows(IOException.class, () -> RootServiceStarter.parseState("error: ANSIT_SERVICE_STOPPED", 0));
    }
    @Test public void parsesRootAndHookAsSeparateActualStates() throws Exception {
        String hash = "a".repeat(64);
        RootServiceStarter.Snapshot snapshot = RootServiceStarter.parseInspection(
            "ANSIT_ROOT_OK\nANSIT_SERVICE_STOPPED\nANSIT_HOOK_LOADED " + BuildConfig.VERSION_NAME + " " + hash + "\n", 0);
        assertEquals(RootServiceStarter.State.STOPPED, snapshot.state);
        assertTrue(snapshot.hookCurrent(hash));
        assertFalse(snapshot.serviceCurrent(hash));
    }
    @Test public void parsesCurrentAndLegacyServiceIdentities() throws Exception {
        String hash = "a".repeat(64);
        RootServiceStarter.Snapshot current = RootServiceStarter.parseInspection(
            "ANSIT_ROOT_OK\nANSIT_SERVICE_RUNNING\nANSIT_SERVICE_IDENTITY " + BuildConfig.VERSION_NAME + " " + hash + "\nANSIT_HOOK_ABSENT\n", 0);
        assertTrue(current.serviceCurrent(hash));
        assertFalse(current.serviceCurrent("b".repeat(64)));
        RootServiceStarter.Snapshot legacy = RootServiceStarter.parseInspection(
            "ANSIT_ROOT_OK\nANSIT_SERVICE_RUNNING\nANSIT_SERVICE_IDENTITY 0.3.0 legacy\nANSIT_HOOK_ABSENT\n", 0);
        assertFalse(legacy.serviceCurrent(hash));
    }
    @Test public void rejectsIncompleteConflictingAndDeniedInspection() {
        assertThrows(IOException.class, () -> RootServiceStarter.parseInspection("ANSIT_ROOT_OK\nANSIT_SERVICE_RUNNING\nANSIT_HOOK_ABSENT\n", 0));
        assertThrows(IOException.class, () -> RootServiceStarter.parseInspection("ANSIT_SERVICE_STOPPED\nANSIT_HOOK_ABSENT\n", 0));
        assertThrows(IOException.class, () -> RootServiceStarter.parseInspection("ANSIT_ROOT_OK\nANSIT_SERVICE_STOPPED\nANSIT_HOOK_ABSENT\nANSIT_HOOK_UNKNOWN\n", 0));
        assertThrows(IOException.class, () -> RootServiceStarter.parseInspection("ANSIT_ROOT_OK\nANSIT_SERVICE_STOPPED\nANSIT_HOOK_ABSENT\n", 1));
    }
}
