package ltd.tgwgroup.ansit;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.*;

public class StatusBarTapTest {
    @Test public void confirmedTapDropsTheNativeScrollRequestedBeforeRelease() {
        StatusBarTap tap = tap(100, 0, null);
        List<Boolean> scroll = new ArrayList<>();

        assertTrue(tap.awaitsScrollDecision());
        tap.deferScroll(scroll::add);
        assertFalse(tap.awaitsScrollDecision());
        assertTrue(scroll.isEmpty());

        tap.resolve(true);
        assertEquals(List.of(false), scroll);
        assertFalse(tap.scrollAllowed());
        assertFalse(tap.awaitsScrollDecision());
    }

    @Test public void abandonedTapReplaysTheNativeRequestOnce() {
        StatusBarTap tap = tap(100, 0, null);
        List<Boolean> scroll = new ArrayList<>();
        tap.deferScroll(scroll::add);

        tap.resolve(false);
        tap.resolve(true);
        assertEquals(List.of(true), scroll);
        assertTrue(tap.scrollAllowed());
    }

    @Test public void doubleTapRequiresTheSameActiveSessionForBothContacts() {
        TriggerGate gate = new TriggerGate();
        StatusBarTap first = tap(100, gate.revision(), new TriggerProtocol.Ready("service-a", 650, true, 1));
        StatusBarTap second = tap(240, gate.revision(), new TriggerProtocol.Ready("service-a", 650, true, 1));
        StatusBarTap afterRestart = tap(240, gate.revision(), new TriggerProtocol.Ready("service-b", 650, true, 1));
        StatusBarTap afterReentry = tap(240, gate.revision(), new TriggerProtocol.Ready("service-a", 650, true, 2));

        assertTrue(second.active(gate));
        assertTrue(second.sharesMode(first, gate));
        assertFalse(afterRestart.sharesMode(first, gate));
        assertFalse(afterReentry.sharesMode(first, gate));
        gate.stopped("service-a");
        assertFalse(second.active(gate));
        assertFalse(second.sharesMode(first, gate));
    }

    @Test public void unavailableAndInactiveServiceNeverClaimATap() {
        TriggerGate gate = new TriggerGate();
        assertFalse(tap(100, gate.revision(), null).active(gate));
        assertFalse(tap(100, gate.revision(), new TriggerProtocol.Ready("service-a", 650)).active(gate));
    }

    private static StatusBarTap tap(long downTime, long revision, TriggerProtocol.Ready ready) {
        StatusBarTap tap = new StatusBarTap(new StatusBarTap.Touch(3, 4098, downTime), 100, 20, revision);
        tap.ready = ready;
        return tap;
    }
}
