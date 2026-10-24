package com.antioblivionis.ansit;

import java.util.function.BooleanSupplier;

/** Stop acknowledgement and feedback issuance serialize on the same small gate. */
final class TriggerGate {
    private long revision;
    private String stoppedInstance = "";
    synchronized long revision() { return revision; }
    synchronized boolean allowed(long expected, String instance) {
        return expected == revision && TriggerProtocol.validInstance(instance) && !stoppedInstance.equals(instance);
    }
    synchronized boolean runIfAllowed(long expected, String instance, BooleanSupplier action) {
        return allowed(expected, instance) && action.getAsBoolean();
    }
    synchronized void stopped(String instance) {
        if (!TriggerProtocol.validInstance(instance)) return;
        stoppedInstance = instance;
        revision++; // Invalidates all in-flight work, including callbacks from earlier service instances.
    }
}
