package com.antioblivionis.ansit;

/** Stable transport name: the new application must still be able to stop the old root service. */
final class ServiceProtocol {
    static final String CONTROL_SOCKET = "dev.ansit.control";
    static final String HOOK_SOCKET = "com.antioblivionis.ansit.hook";
    private ServiceProtocol() { }
}
