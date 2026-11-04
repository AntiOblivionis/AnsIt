package com.antioblivionis.ansit;

/** Exact, small messages; an absent, old or malformed peer never arms a gesture. */
final class TriggerProtocol {
    static final String PREPARE = "TRIGGER_PREPARE";
    static final String UNAVAILABLE = "ANSIT_TRIGGER_UNAVAILABLE";
    static final String READY = "ANSIT_TRIGGER_READY";
    static final String NEXT = "TRIGGER_NEXT";
    /**
     * Readiness also carries the active continuous-mode generation, if any. A stopped, old or
     * malformed service never arms a gesture, so both ends must agree on this exact packet.
     */
    record Ready(String instance, int holdMillis, boolean mode, long modeToken) {
        Ready(String instance, int holdMillis) { this(instance, holdMillis, false, 0); }
    }
    record Next(String instance, long modeToken, boolean doubleTap) { }
    static boolean validInstance(String value) { return value != null && value.matches("[A-Za-z0-9_-]{1,80}"); }
    static String ready(String instance, int millis, boolean mode, long modeToken) {
        if (!validInstance(instance) || millis < TriggerConfig.MIN_MS || millis > TriggerConfig.MAX_MS
            || (mode ? modeToken <= 0 : modeToken != 0))
            throw new IllegalArgumentException("Invalid trigger readiness");
        return READY + " " + instance + " " + millis + " " + (mode ? 1 : 0) + " " + modeToken;
    }
    static Ready parseReady(String line) {
        if (line == null) return null;
        String[] parts = line.split(" ", -1);
        if (parts.length != 5 || !parts[0].equals(READY) || !validInstance(parts[1])) return null;
        try {
            int millis = Integer.parseInt(parts[2]);
            if (millis < TriggerConfig.MIN_MS || millis > TriggerConfig.MAX_MS) return null;
            boolean mode;
            if ("1".equals(parts[3])) mode = true;
            else if ("0".equals(parts[3])) mode = false;
            else return null;
            long token = Long.parseLong(parts[4]);
            if (token < 0 || (mode && token == 0) || (!mode && token != 0)) return null;
            return new Ready(parts[1], millis, mode, token);
        } catch (NumberFormatException invalid) { return null; }
    }
    static String next(String instance, long modeToken, boolean doubleTap) {
        if (!validInstance(instance) || modeToken <= 0) throw new IllegalArgumentException("Invalid trigger next");
        return NEXT + " " + instance + " " + modeToken + " " + (doubleTap ? "double" : "single");
    }
    static Next parseNext(String line) {
        if (line == null) return null;
        String[] parts = line.split(" ", -1);
        if (parts.length != 4 || !parts[0].equals(NEXT) || !validInstance(parts[1])) return null;
        try {
            long token = Long.parseLong(parts[2]);
            if (token <= 0) return null;
            if ("double".equals(parts[3])) return new Next(parts[1], token, true);
            if ("single".equals(parts[3])) return new Next(parts[1], token, false);
            return null;
        } catch (NumberFormatException invalid) { return null; }
    }
    static boolean current(String command, String prefix, String instance) {
        return validInstance(instance) && (prefix + instance).equals(command);
    }
    static String confirmed(String instance) { return "ANSIT_TRIGGER_CONFIRMED " + instance; }
    static String quiet(String instance) { return "ANSIT_TRIGGER_QUIET " + instance; }
    private TriggerProtocol() { }
}
