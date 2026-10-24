package com.antioblivionis.ansit;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class HoldStateTest {
    private HoldState.Attempt armed(HoldState state, TriggerGate gate, String instance, int millis) {
        HoldState.Attempt attempt = state.begin(100, gate.revision());
        assertTrue(state.arm(attempt, new TriggerProtocol.Ready(instance, millis)));
        return attempt;
    }
    private boolean feedback(HoldState state, HoldState.Attempt attempt, TriggerGate gate, AtomicInteger pulses) {
        return gate.runIfAllowed(attempt.gateRevision, attempt.instance, () -> {
            if (!state.confirm(attempt, attempt.instance)) return false;
            pulses.incrementAndGet(); return true;
        });
    }
    @Test public void stoppedOrUnknownServiceNeverArmsConsumesOrVibrates() {
        for (String reply : new String[]{null, TriggerProtocol.UNAVAILABLE, "ANSIT_SERVICE_RUNNING", "bad packet"}) {
            HoldState state = new HoldState(); TriggerGate gate = new TriggerGate(); AtomicInteger pulses = new AtomicInteger();
            HoldState.Attempt attempt = state.begin(100, gate.revision());
            assertFalse(state.arm(attempt, TriggerProtocol.parseReady(reply)));
            assertFalse(state.requestConfirmation(attempt, 5000));
            assertFalse(feedback(state, attempt, gate, pulses));
            HoldState.Release release = state.release(true);
            assertFalse(release.consume()); assertNull(release.instance()); assertEquals(0, pulses.get());
        }
    }
    @Test public void thresholdIsMeasuredFromOriginalDownNotReadinessArrival() {
        HoldState state = new HoldState(); TriggerGate gate = new TriggerGate();
        HoldState.Attempt attempt = armed(state, gate, "service-a", 650);
        assertEquals(450, state.remaining(attempt, 300));
        assertFalse(state.requestConfirmation(attempt, 749));
        assertTrue(state.requestConfirmation(attempt, 750));
    }
    @Test public void customThresholdChangesTheActualHoldDecision() {
        for (int millis : new int[]{200, 650, 925, 3000}) {
            HoldState state = new HoldState(); TriggerGate gate = new TriggerGate();
            HoldState.Attempt attempt = armed(state, gate, "service-a", millis);
            assertFalse(state.requestConfirmation(attempt, 100 + millis - 1));
            assertTrue(state.requestConfirmation(attempt, 100 + millis));
        }
    }
    @Test public void acceptedHoldPulsesOnceAndOnlyTriggersOnRelease() {
        HoldState state = new HoldState(); TriggerGate gate = new TriggerGate(); AtomicInteger pulses = new AtomicInteger();
        HoldState.Attempt attempt = armed(state, gate, "service-a", 650);
        assertTrue(state.requestConfirmation(attempt, 750));
        assertFalse(state.fired());
        assertTrue(feedback(state, attempt, gate, pulses));
        assertFalse(feedback(state, attempt, gate, pulses));
        assertEquals(1, pulses.get());
        HoldState.Release release = state.release(true);
        assertTrue(release.consume()); assertEquals("service-a", release.instance()); assertFalse(attempt.valid);
        assertNull(state.release(true).instance());
    }
    @Test public void earlyReleaseCannotBeRevivedByLateReadiness() {
        HoldState state = new HoldState(); TriggerGate gate = new TriggerGate();
        HoldState.Attempt attempt = state.begin(100, gate.revision());
        assertFalse(state.release(true).consume());
        assertFalse(state.arm(attempt, new TriggerProtocol.Ready("service-a", 650)));
        assertFalse(state.requestConfirmation(attempt, 1000));
    }
    @Test public void releaseWhileConfirmationIsPendingRejectsLateFeedback() {
        HoldState state = new HoldState(); TriggerGate gate = new TriggerGate(); AtomicInteger pulses = new AtomicInteger();
        HoldState.Attempt attempt = armed(state, gate, "service-a", 650);
        assertTrue(state.requestConfirmation(attempt, 750));
        assertFalse(state.release(true).consume());
        assertFalse(feedback(state, attempt, gate, pulses)); assertEquals(0, pulses.get());
    }
    @Test public void movementCancellationCannotProduceFeedbackOrAction() {
        HoldState state = new HoldState(); TriggerGate gate = new TriggerGate(); AtomicInteger pulses = new AtomicInteger();
        HoldState.Attempt attempt = armed(state, gate, "service-a", 650);
        state.cancel();
        assertFalse(state.requestConfirmation(attempt, 750));
        assertFalse(feedback(state, attempt, gate, pulses));
        assertNull(state.release(true).instance());
    }
    @Test public void newTouchRejectsCallbacksBelongingToPreviousTouch() {
        HoldState state = new HoldState(); TriggerGate gate = new TriggerGate(); AtomicInteger pulses = new AtomicInteger();
        HoldState.Attempt old = armed(state, gate, "service-a", 650);
        assertTrue(state.requestConfirmation(old, 750));
        HoldState.Attempt next = state.begin(800, gate.revision());
        assertFalse(feedback(state, old, gate, pulses));
        assertTrue(state.live(next)); assertFalse(state.fired());
    }
    @Test public void stopBetweenHoldAndConfirmationMakesLateAckInert() {
        HoldState state = new HoldState(); TriggerGate gate = new TriggerGate(); AtomicInteger pulses = new AtomicInteger();
        HoldState.Attempt attempt = armed(state, gate, "service-a", 650);
        assertTrue(state.requestConfirmation(attempt, 750));
        gate.stopped("service-a");
        assertFalse(feedback(state, attempt, gate, pulses));
        assertEquals(0, pulses.get()); assertFalse(state.release(true).consume());
    }
    @Test public void stopAndRestartCannotRedirectAnAlreadyAcceptedReleaseToNewService() {
        HoldState state = new HoldState(); TriggerGate gate = new TriggerGate(); AtomicInteger pulses = new AtomicInteger();
        HoldState.Attempt attempt = armed(state, gate, "service-old", 650);
        assertTrue(state.requestConfirmation(attempt, 750)); assertTrue(feedback(state, attempt, gate, pulses));
        gate.stopped("service-old");
        HoldState.Release release = state.release(true);
        assertTrue(release.consume()); // The original hierarchy already received CANCEL.
        assertFalse(gate.allowed(release.gateRevision(), release.instance()));
        assertFalse(TriggerProtocol.current("TOGGLE " + release.instance(), "TOGGLE ", "service-new"));
    }
    @Test public void cancelAfterFeedbackConsumesTailWithoutTriggering() {
        HoldState state = new HoldState(); TriggerGate gate = new TriggerGate(); AtomicInteger pulses = new AtomicInteger();
        HoldState.Attempt attempt = armed(state, gate, "service-a", 650);
        assertTrue(state.requestConfirmation(attempt, 750)); assertTrue(feedback(state, attempt, gate, pulses));
        HoldState.Release release = state.release(false);
        assertTrue(release.consume()); assertNull(release.instance());
    }
    @Test public void laterStopsCannotReenableCallbacksFromAnOlderInstance() {
        TriggerGate gate = new TriggerGate(); long original = gate.revision();
        gate.stopped("service-a"); gate.stopped("service-b");
        assertFalse(gate.allowed(original, "service-a"));
        assertFalse(gate.allowed(gate.revision(), "service-b"));
        assertTrue(gate.allowed(gate.revision(), "service-c"));
    }
    @Test public void stopAcknowledgementSerializesAfterAnyAlreadyIssuedFeedback() throws Exception {
        TriggerGate gate = new TriggerGate(); long revision = gate.revision();
        CountDownLatch inside = new CountDownLatch(1), release = new CountDownLatch(1), stopAttempted = new CountDownLatch(1);
        AtomicInteger sequence = new AtomicInteger(), pulseOrder = new AtomicInteger(), stopOrder = new AtomicInteger();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> pulse = workers.submit(() -> gate.runIfAllowed(revision, "service-a", () -> {
                inside.countDown();
                try { if (!release.await(2, TimeUnit.SECONDS)) return false; }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return false; }
                pulseOrder.set(sequence.incrementAndGet()); return true;
            }));
            assertTrue(inside.await(1, TimeUnit.SECONDS));
            Future<?> stop = workers.submit(() -> { stopAttempted.countDown(); gate.stopped("service-a"); stopOrder.set(sequence.incrementAndGet()); });
            assertTrue(stopAttempted.await(1, TimeUnit.SECONDS));
            assertEquals(0, stopOrder.get());
            release.countDown();
            assertTrue(pulse.get(2, TimeUnit.SECONDS)); stop.get(2, TimeUnit.SECONDS);
            assertTrue(pulseOrder.get() < stopOrder.get());
            assertFalse(gate.runIfAllowed(revision, "service-a", () -> { fail("Late feedback"); return true; }));
        } finally { release.countDown(); workers.shutdownNow(); }
    }
}
