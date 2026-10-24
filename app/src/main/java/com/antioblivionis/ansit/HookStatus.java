package com.antioblivionis.ansit;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import de.robv.android.xposed.XposedBridge;

/** Root-only module identity and trigger revocation. No broadcasts or ordinary-app callers. */
final class HookStatus {
    private static final AtomicBoolean started = new AtomicBoolean();
    private static final TriggerGate gate = new TriggerGate();
    static TriggerGate gate() { return gate; }

    static void start(String loadedApk) {
        if (!started.compareAndSet(false, true)) return;
        Thread status = new Thread(() -> {
            try {
                // Cache at module load. An APK replaced later must not make an old hook look current.
                String identity = "version=" + BuildConfig.VERSION_NAME + " apk=" + BuildIdentity.digest(loadedApk) + "\n";
                try (LocalServerSocket server = new LocalServerSocket(ServiceProtocol.HOOK_SOCKET)) {
                    while (true) {
                        try (LocalSocket client = server.accept()) {
                            if (client.getPeerCredentials().getUid() != 0) continue;
                            client.getOutputStream().write(identity.getBytes(StandardCharsets.UTF_8));
                            client.setSoTimeout(500);
                            String command = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8)).readLine();
                            if (command != null && command.startsWith("STOP_TRIGGER ")) {
                                String instance = command.substring("STOP_TRIGGER ".length());
                                if (TriggerProtocol.validInstance(instance)) {
                                    gate.stopped(instance);
                                    client.getOutputStream().write((TriggerProtocol.quiet(instance) + "\n").getBytes(StandardCharsets.UTF_8));
                                }
                            }
                        } catch (java.io.IOException ignored) { }
                    }
                }
            } catch (Exception failure) {
                XposedBridge.log("AnsIt: hook status unavailable (" + failure.getClass().getSimpleName() + ")");
            }
        }, "ansit-hook-status");
        status.setDaemon(true);
        status.start();
    }
    private HookStatus() { }
}
