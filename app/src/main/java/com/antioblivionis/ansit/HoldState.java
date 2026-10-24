package com.antioblivionis.ansit;

/** Main-thread gesture state; workers may only inspect Attempt.valid before doing I/O. */
final class HoldState {
    enum Stage { PREPARING, ARMED, VERIFYING, FIRED }
    static final class Attempt {
        final long startedAt, gateRevision;
        volatile boolean valid = true;
        String instance;
        int holdMillis;
        Stage stage = Stage.PREPARING;
        Attempt(long now, long revision) { startedAt = now; gateRevision = revision; }
    }
    record Release(boolean consume, String instance, long gateRevision) { }
    private Attempt current;
    Attempt begin(long now, long revision) { cancel(); return current = new Attempt(now, revision); }
    Attempt current() { return current; }
    boolean live(Attempt attempt) { return attempt != null && current == attempt && attempt.valid; }
    boolean arm(Attempt attempt, TriggerProtocol.Ready ready) {
        if (!live(attempt) || attempt.stage != Stage.PREPARING || ready == null) return false;
        attempt.instance = ready.instance(); attempt.holdMillis = ready.holdMillis(); attempt.stage = Stage.ARMED;
        return true;
    }
    long remaining(Attempt attempt, long now) { return Math.max(0, attempt.startedAt + attempt.holdMillis - now); }
    boolean requestConfirmation(Attempt attempt, long now) {
        if (!live(attempt) || attempt.stage != Stage.ARMED || remaining(attempt, now) > 0) return false;
        attempt.stage = Stage.VERIFYING; return true;
    }
    boolean confirm(Attempt attempt, String instance) {
        if (!live(attempt) || attempt.stage != Stage.VERIFYING || !attempt.instance.equals(instance)) return false;
        attempt.stage = Stage.FIRED; return true;
    }
    boolean fired() { return current != null && current.valid && current.stage == Stage.FIRED; }
    Release release(boolean up) {
        Release result = new Release(fired(), up && fired() ? current.instance : null,
            current == null ? -1 : current.gateRevision);
        cancel(); return result;
    }
    void cancel() { if (current != null) current.valid = false; current = null; }
}
