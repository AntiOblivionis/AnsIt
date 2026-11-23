package ltd.tgwgroup.ansit;

import java.util.function.Consumer;

/** Tracks the native scroll request for one physical touch on the SystemUI main thread. */
final class StatusBarTap {
    record Touch(int deviceId, int source, long downTime) { }

    final Touch touch;
    final float x, y;
    final long gateRevision;
    TriggerProtocol.Ready ready;
    boolean prepared;
    private Boolean claimed;
    private Consumer<Boolean> deferredScroll;

    StatusBarTap(Touch touch, float x, float y, long gateRevision) {
        this.touch = touch;
        this.x = x;
        this.y = y;
        this.gateRevision = gateRevision;
    }

    boolean scrollAllowed() { return Boolean.FALSE.equals(claimed); }

    boolean awaitsScrollDecision() { return claimed == null && deferredScroll == null; }

    void deferScroll(Consumer<Boolean> callback) { deferredScroll = callback; }

    void resolve(boolean claim) {
        if (claimed != null) return;
        claimed = claim;
        Consumer<Boolean> callback = deferredScroll;
        deferredScroll = null;
        if (callback != null) callback.accept(!claim);
    }

    boolean active(TriggerGate gate) {
        return !scrollAllowed() && ready != null && ready.mode() && gate.allowed(gateRevision, ready.instance());
    }

    boolean sharesMode(StatusBarTap first, TriggerGate gate) {
        return first.active(gate) && ready.instance().equals(first.ready.instance())
            && ready.modeToken() == first.ready.modeToken();
    }
}
