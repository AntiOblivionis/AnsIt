package ltd.tgwgroup.ansit;

/** Stable transport names allow this version to identify and stop an existing service or hook. */
final class ServiceProtocol {
    static final String CONTROL_SOCKET = "dev.ansit.control";
    static final String HOOK_SOCKET = "com.antioblivionis.ansit.hook";
    private ServiceProtocol() { }
}
