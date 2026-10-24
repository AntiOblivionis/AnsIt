package com.antioblivionis.ansit;

import java.io.IOException;

/** Bounded, event-driven reconciliation; a stopped service never starts merely because the UI opened. */
final class ServiceController {
    interface Backend {
        RootServiceStarter.Snapshot inspect() throws Exception;
        void start() throws Exception;
        void stop() throws Exception;
    }
    static RootServiceStarter.Snapshot reconcile(Backend backend, String expected, Runnable updating) throws Exception {
        RootServiceStarter.Snapshot before = backend.inspect();
        if (before.state == RootServiceStarter.State.RUNNING && !before.serviceCurrent(expected)) {
            updating.run();
            return start(backend, expected);
        }
        return before;
    }
    static RootServiceStarter.Snapshot start(Backend backend, String expected) throws Exception {
        backend.start();
        RootServiceStarter.Snapshot actual = backend.inspect();
        if (!actual.serviceCurrent(expected)) throw new IOException("服务尚未确认加载当前版本，请重新检查");
        return actual;
    }
    static RootServiceStarter.Snapshot stop(Backend backend) throws Exception {
        backend.stop();
        RootServiceStarter.Snapshot actual = backend.inspect();
        if (actual.state != RootServiceStarter.State.STOPPED) throw new IOException("服务尚未确认停止，请重新检查");
        return actual;
    }
    private ServiceController() { }
}
