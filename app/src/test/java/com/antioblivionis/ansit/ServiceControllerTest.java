package com.antioblivionis.ansit;

import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.*;
import static com.antioblivionis.ansit.RootServiceStarter.*;

public class ServiceControllerTest {
    private static final String CURRENT = "a".repeat(64);
    private static final String OLD = "b".repeat(64);
    private static Snapshot running(String hash) {
        return new Snapshot(State.RUNNING, BuildConfig.VERSION_NAME, hash, Hook.ABSENT, "", "");
    }
    private static Snapshot stopped() { return new Snapshot(State.STOPPED, "", "", Hook.ABSENT, "", ""); }
    private static final class Fake implements ServiceController.Backend {
        Snapshot actual;
        int starts, stops, reads;
        boolean failStart, wrongStart, failRead, failStop;
        Fake(Snapshot initial) { actual = initial; }
        public Snapshot inspect() throws IOException {
            reads++;
            if (failRead) throw new IOException("not authorized");
            return actual;
        }
        public void start() throws IOException {
            starts++;
            if (failStart) throw new IOException("start failed");
            actual = running(wrongStart ? OLD : CURRENT);
        }
        public void stop() { stops++; if (!failStop) actual = stopped(); }
    }

    @Test public void enteringWithStoppedServiceDoesNotStartAnything() throws Exception {
        Fake fake = new Fake(stopped());
        assertEquals(State.STOPPED, ServiceController.reconcile(fake, CURRENT, () -> fail()).state);
        assertEquals(0, fake.starts);
        assertEquals(1, fake.reads);
    }
    @Test public void enteringWithCurrentServiceDoesNotRestartOrCancelItsWork() throws Exception {
        Fake fake = new Fake(running(CURRENT));
        assertTrue(ServiceController.reconcile(fake, CURRENT, () -> fail()).serviceCurrent(CURRENT));
        assertEquals(0, fake.starts);
        assertEquals(0, fake.stops);
    }
    @Test public void enteringWithSameVersionDifferentApkReplacesAndVerifiesProcess() throws Exception {
        Fake fake = new Fake(running(OLD));
        int[] updates = {0};
        assertTrue(ServiceController.reconcile(fake, CURRENT, () -> updates[0]++).serviceCurrent(CURRENT));
        assertEquals(1, updates[0]); assertEquals(1, fake.starts); assertEquals(2, fake.reads);
    }
    @Test public void legacyServiceWithoutFingerprintIsUpdated() throws Exception {
        Fake fake = new Fake(new Snapshot(State.RUNNING, "0.3.0", "legacy", Hook.ABSENT, "", ""));
        assertTrue(ServiceController.reconcile(fake, CURRENT, () -> {}).serviceCurrent(CURRENT));
        assertEquals(1, fake.starts);
    }
    @Test public void unavailableRootNeverTriggersAStart() {
        Fake fake = new Fake(stopped()); fake.failRead = true;
        assertThrows(IOException.class, () -> ServiceController.reconcile(fake, CURRENT, () -> {}));
        assertEquals(0, fake.starts);
    }
    @Test public void explicitStartAlwaysBootstrapsCurrentApkEvenIfServiceAlreadyExists() throws Exception {
        Fake fake = new Fake(running(CURRENT));
        assertTrue(ServiceController.start(fake, CURRENT).serviceCurrent(CURRENT));
        assertEquals(1, fake.starts); assertEquals(1, fake.reads);
    }
    @Test public void wrongProcessAfterStartCannotBeReportedAsSuccess() {
        Fake fake = new Fake(stopped()); fake.wrongStart = true;
        assertThrows(IOException.class, () -> ServiceController.start(fake, CURRENT));
    }
    @Test public void failedUpdateDoesNotFabricateANewRunningState() {
        Fake fake = new Fake(running(OLD)); fake.failStart = true;
        assertThrows(IOException.class, () -> ServiceController.reconcile(fake, CURRENT, () -> {}));
        assertEquals(OLD, fake.actual.apk);
    }
    @Test public void explicitStopVerifiesStoppedAndNeverRestarts() throws Exception {
        Fake fake = new Fake(running(OLD));
        assertEquals(State.STOPPED, ServiceController.stop(fake).state);
        assertEquals(1, fake.stops); assertEquals(0, fake.starts);
    }
    @Test public void stopWithoutAcknowledgedStoppedStateFails() {
        Fake fake = new Fake(running(CURRENT)); fake.failStop = true;
        assertThrows(IOException.class, () -> ServiceController.stop(fake));
    }
}
