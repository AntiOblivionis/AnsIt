package com.antioblivionis.ansit;

/** Exact, small messages; an absent, old or malformed peer never arms a gesture. */
final class TriggerProtocol {
    static final String PREPARE = "TRIGGER_PREPARE";
    static final String UNAVAILABLE = "ANSIT_TRIGGER_UNAVAILABLE";
    record Ready(String instance, int holdMillis) { }
    static boolean validInstance(String value) { return value != null && value.matches("[A-Za-z0-9_-]{1,80}"); }
    static String ready(String instance, int millis) {
        if (!validInstance(instance) || millis < TriggerConfig.MIN_MS || millis > TriggerConfig.MAX_MS)
            throw new IllegalArgumentException("Invalid trigger readiness");
        return "ANSIT_TRIGGER_READY " + instance + " " + millis;
    }
    static Ready parseReady(String line) {
        if (line == null) return null;
        String[] parts = line.split(" ", -1);
        if (parts.length != 3 || !parts[0].equals("ANSIT_TRIGGER_READY") || !validInstance(parts[1])) return null;
        try {
            int millis = Integer.parseInt(parts[2]);
            if (millis < TriggerConfig.MIN_MS || millis > TriggerConfig.MAX_MS) return null;
            return new Ready(parts[1], millis);
        } catch (NumberFormatException invalid) { return null; }
    }
    static boolean current(String command, String prefix, String instance) {
        return validInstance(instance) && (prefix + instance).equals(command);
    }
    static String confirmed(String instance) { return "ANSIT_TRIGGER_CONFIRMED " + instance; }
    static String quiet(String instance) { return "ANSIT_TRIGGER_QUIET " + instance; }
    private TriggerProtocol() { }
}
