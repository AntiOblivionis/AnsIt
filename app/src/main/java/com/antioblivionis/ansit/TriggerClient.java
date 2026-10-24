package com.antioblivionis.ansit;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Used only on the worker, never the SystemUI touch thread. */
final class TriggerClient {
    static TriggerProtocol.Ready prepare() { return TriggerProtocol.parseReady(request(TriggerProtocol.PREPARE)); }
    static boolean confirm(String instance) {
        return TriggerProtocol.validInstance(instance)
            && TriggerProtocol.confirmed(instance).equals(request("TRIGGER_CHECK " + instance));
    }
    static void toggle(String instance) {
        if (TriggerProtocol.validInstance(instance)) request("TOGGLE " + instance);
    }
    private static String request(String command) {
        try (LocalSocket socket = new LocalSocket()) {
            socket.connect(new LocalSocketAddress(ServiceProtocol.CONTROL_SOCKET, LocalSocketAddress.Namespace.ABSTRACT));
            if (socket.getPeerCredentials().getUid() != 0) return null;
            socket.setSoTimeout(300);
            socket.getOutputStream().write((command + "\n").getBytes(StandardCharsets.UTF_8));
            return new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)).readLine();
        } catch (Exception unavailable) { return null; } // Stopped service is routine, not a log-worthy failure.
    }
    private TriggerClient() { }
}
