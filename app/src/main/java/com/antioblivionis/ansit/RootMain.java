package com.antioblivionis.ansit;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.HardwareBuffer;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.IBinder;
import android.os.Looper;
import android.view.Surface;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Executed by app_process as root, never inside a target application's process. */
public final class RootMain {
    static final String SERVICE_VERSION = BuildConfig.VERSION_NAME;
    private static String serviceInstance = java.util.UUID.randomUUID().toString();
    private static volatile boolean stopping;
    private static String serviceApk = "legacy";
    private static final AtomicLong generation = new AtomicLong();
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static Object layer;
    private static Surface surface;
    private static boolean active;
    private static String shownText;
    private static final java.io.File CONFIG_DIR = new java.io.File("/data/user/0/com.antioblivionis.ansit/files");
    private static LlmCall pendingCall;
    private static java.util.concurrent.Future<?> pendingTask;
    private static Class<?> displayControl;
    private static int systemUiUid = -1;
    private static final ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor();
    private static DisplayState displayedOn;
    private static String lastMonitorError;
    private static final java.util.concurrent.atomic.AtomicReference<String> nextTriggerDump = new java.util.concurrent.atomic.AtomicReference<>();
    private record DisplayState(int width, int height, int rotation, int state, int layerStack, long physicalId, boolean locked) { }

    public static void main(String[] args) throws Exception {
        try { run(args); }
        catch (Throwable t) { t.printStackTrace(System.out); System.exit(1); }
    }
    private static void run(String[] args) throws Exception {
        Looper.prepareMainLooper();
        if (args.length == 1 && args[0].equals("prepare-adjustment")) {
            if (android.os.Process.myUid() != 0) throw new SecurityException("Root required");
            try {
                if (!"OK".equals(control("HIDE"))) throw new IllegalStateException("Invalid hide response");
            } catch (java.io.IOException absent) { if (!isAbsent(absent)) throw absent; }
            System.out.println("ANSIT_EDITOR_READY"); return;
        }
        if (args.length == 1 && args[0].equals("refresh-overlay")) {
            if (android.os.Process.myUid() != 0) throw new SecurityException("Root required");
            try {
                if (!"ANSIT_OVERLAY_APPLIED".equals(control("RELAYOUT"))) throw new IllegalStateException("Invalid overlay response");
                System.out.println("ANSIT_OVERLAY_APPLIED");
            } catch (java.io.IOException absent) {
                if (!isAbsent(absent)) throw absent;
                System.out.println("ANSIT_OVERLAY_SAVED");
            }
            return;
        }
        if (args.length == 1 && args[0].equals("restart-systemui")) {
            if (android.os.Process.myUid() != 0) throw new SecurityException("Root required");
            restartSystemUi(); return;
        }
        if (args.length == 2 && args[0].equals("manage")) {
            if (android.os.Process.myUid() != 0) throw new SecurityException("Root required");
            if (args[1].equals("inspect")) { printInspection(); return; }
            if (args[1].equals("stop")) {
                try { control("STOP"); } catch (java.io.IOException absent) { if (!isAbsent(absent)) throw absent; }
                for (int attempt = 0; attempt < 30 && serviceRunning(); attempt++) Thread.sleep(100);
                if (serviceRunning()) throw new IllegalStateException("Service did not stop");
            } else if (!args[1].equals("state")) throw new IllegalArgumentException("Unknown operation");
            System.out.println(serviceRunning() ? "ANSIT_SERVICE_RUNNING" : "ANSIT_SERVICE_STOPPED"); return;
        }
        if (args.length > 0 && args[0].equals("bootstrap")) {
            if (android.os.Process.myUid() != 0 || args.length != 3) throw new SecurityException("Root bootstrap required");
            bootstrap(args[1], new java.io.File(args[2])); return;
        }
        if (args.length == 2 && args[0].equals("serve")) serviceInstance = args[1];
        if (args.length > 0 && args[0].equals("display")) { System.out.println(displayState()); return; }
        if (args.length > 0 && (args[0].equals("toggle") || args[0].equals("stop") || args[0].equals("command"))) {
            try (LocalSocket client = new LocalSocket()) {
                client.connect(new android.net.LocalSocketAddress(ServiceProtocol.CONTROL_SOCKET, android.net.LocalSocketAddress.Namespace.ABSTRACT));
                client.setSoTimeout(2000);
                String command = args[0].equals("command") ? args[1] : args[0].toUpperCase(java.util.Locale.ROOT);
                client.getOutputStream().write((command + "\n").getBytes(StandardCharsets.UTF_8));
                System.out.println(new String(StreamCompat.readNBytes(client.getInputStream(), 128), StandardCharsets.UTF_8));
            }
            return;
        }
        if (args.length > 0 && args[0].equals("inspect")) {
            for (String name : new String[]{"android.view.SurfaceControl", "android.view.SurfaceControl$Builder",
                "android.view.SurfaceControl$Transaction", "android.graphics.Typeface", "android.view.DisplayControl", "com.android.server.display.DisplayControl",
                "android.window.ScreenCapture", "android.window.ScreenCaptureInternal",
                "android.window.ScreenCaptureInternal$DisplayCaptureArgs$Builder",
                "android.window.ScreenCaptureInternal$CaptureArgs$Builder"}) {
                try {
                    Class<?> c = Class.forName(name);
                    System.out.println("CLASS " + name);
                    for (Constructor<?> ctor : c.getDeclaredConstructors()) System.out.println(ctor);
                    for (Method m : c.getDeclaredMethods()) System.out.println(m);
                } catch (Throwable t) { System.out.println(t); }
            }
            return;
        }
        serviceApk = BuildIdentity.digest(System.getenv("CLASSPATH"));
        call(android.graphics.Typeface.class, "loadPreinstalledSystemFontMap", new Class<?>[0]);
        systemUiUid = resolveSystemUiUid();
        monitor.scheduleWithFixedDelay(() -> {
            try {
                DisplayState now = displayState();
                lastMonitorError = null;
                synchronized (RootMain.class) {
                    if (active && displayedOn != null && (!now.equals(displayedOn) || now.locked || now.state != 2)) {
                        cancel();
                        System.out.println("CLOSED display/lock change");
                    }
                }
            } catch (Throwable t) {
                synchronized (RootMain.class) {
                    if (active) cancel();
                }
                if (!t.toString().equals(lastMonitorError)) { t.printStackTrace(System.out); lastMonitorError = t.toString(); }
            }
        }, 250, 250, TimeUnit.MILLISECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(RootMain::cancel));
        serve();
    }

    private static int resolveSystemUiUid() throws Exception {
        int resolved = -1;
        Process query = new ProcessBuilder("/system/bin/cmd", "package", "list", "packages", "-U", "com.android.systemui").start();
        try (BufferedReader lines = new BufferedReader(new InputStreamReader(query.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = lines.readLine()) != null) {
                if (line.matches("package:com\\.android\\.systemui uid:[0-9,]+")) resolved = Integer.parseInt(line.substring(line.indexOf("uid:")+4).split(",")[0]);
            }
        }
        if (query.waitFor() != 0 || resolved < 0) throw new SecurityException("Cannot resolve SystemUI uid");
        return resolved;
    }

    private static void serve() throws Exception {
        try (LocalServerSocket server = new LocalServerSocket(ServiceProtocol.CONTROL_SOCKET)) {
            System.out.println("READY root=" + android.os.Process.myUid() + " systemui=" + systemUiUid);
            while (true) {
                try (LocalSocket client = server.accept()) {
                    int uid = client.getPeerCredentials().getUid();
                    if (uid != 0 && uid != systemUiUid) continue;
                    client.setSoTimeout(1500);
                    String cmd = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8)).readLine();
                    if (uid == 0 && "HEALTH".equals(cmd)) {
                        client.getOutputStream().write(("version=" + SERVICE_VERSION + " instance=" + serviceInstance + " apk=" + serviceApk + "\n").getBytes(StandardCharsets.UTF_8));
                    } else if (TriggerProtocol.PREPARE.equals(cmd)) {
                        String reply = TriggerProtocol.UNAVAILABLE;
                        try {
                            if (triggerReady()) reply = TriggerProtocol.ready(serviceInstance,
                                TriggerConfig.read(new java.io.File(CONFIG_DIR, TriggerConfig.FILE_NAME)).holdMillis);
                        } catch (Exception invalidSettings) { }
                        client.getOutputStream().write((reply + "\n").getBytes(StandardCharsets.UTF_8));
                    } else if (cmd != null && cmd.startsWith("TRIGGER_CHECK ")) {
                        String reply = TriggerProtocol.current(cmd, "TRIGGER_CHECK ", serviceInstance) && triggerReady()
                            ? TriggerProtocol.confirmed(serviceInstance) : TriggerProtocol.UNAVAILABLE;
                        client.getOutputStream().write((reply + "\n").getBytes(StandardCharsets.UTF_8));
                    } else if ("TOGGLE".equals(cmd) || (cmd != null && cmd.startsWith("TOGGLE "))) {
                        boolean accepted = !stopping && ("TOGGLE".equals(cmd) || TriggerProtocol.current(cmd, "TOGGLE ", serviceInstance));
                        if (accepted) toggle();
                        client.getOutputStream().write((accepted ? "OK\n" : "IGNORED\n").getBytes(StandardCharsets.UTF_8));
                    } else if (uid == 0 && "SHOW".equals(cmd)) {
                        synchronized (RootMain.class) { if (!active) toggle(); }
                        client.getOutputStream().write("OK\n".getBytes(StandardCharsets.UTF_8));
                    } else if (uid == 0 && "HIDE".equals(cmd)) {
                        cancel(); client.getOutputStream().write("OK\n".getBytes(StandardCharsets.UTF_8));
                    } else if (uid == 0 && "STATUS".equals(cmd)) {
                        synchronized (RootMain.class) {
                            client.getOutputStream().write(("active="+active+" layer="+(layer != null)+" generation="+generation.get()+"\n").getBytes(StandardCharsets.UTF_8));
                        }
                    } else if (uid == 0 && "RELAYOUT".equals(cmd)) {
                        synchronized (RootMain.class) {
                            if (active && layer != null && shownText != null && displayedOn != null) {
                                DisplayState now = displayState();
                                if (!now.equals(displayedOn) || now.locked || now.state != 2) cancel();
                                else show(shownText, displayedOn.width, displayedOn.height);
                            }
                        }
                        client.getOutputStream().write("ANSIT_OVERLAY_APPLIED\n".getBytes(StandardCharsets.UTF_8));
                    } else if (uid == 0 && cmd != null && cmd.startsWith("ARM ")) {
                        String label = cmd.substring(4);
                        if (!label.matches("[a-z0-9-]{1,40}")) throw new SecurityException("Invalid diagnostic label");
                        nextTriggerDump.set(label); client.getOutputStream().write("ARMED\n".getBytes(StandardCharsets.UTF_8));
                    } else if ("STOP".equals(cmd)) {
                        stopping = true; cancel(); revokeTrigger(); return;
                    }
                    else if (uid == 0 && "INCLUDE".equals(cmd)) {
                        setExcluded(false); client.getOutputStream().write("OK\n".getBytes(StandardCharsets.UTF_8));
                    } else if (uid == 0 && "EXCLUDE".equals(cmd)) {
                        setExcluded(true); client.getOutputStream().write("OK\n".getBytes(StandardCharsets.UTF_8));
                    } else if (uid == 0 && cmd != null && cmd.startsWith("CAPTURE ")) {
                        String path = cmd.substring(8);
                        if (!path.matches("/data/local/tmp/ansit-[a-z0-9-]+\\.png")) throw new SecurityException("Invalid diagnostic path");
                        Bitmap b = capture();
                        try (var out = new java.io.FileOutputStream(path)) { b.compress(Bitmap.CompressFormat.PNG, 100, out); }
                        finally { b.recycle(); }
                        client.getOutputStream().write("SAVED\n".getBytes(StandardCharsets.UTF_8));
                    }
                } catch (Throwable t) { t.printStackTrace(); }
            }
        } finally { cancel(); worker.shutdownNow(); monitor.shutdownNow(); }
    }

    private static String control(String command) throws Exception {
        try (LocalSocket socket = new LocalSocket()) {
            socket.connect(new android.net.LocalSocketAddress(ServiceProtocol.CONTROL_SOCKET, android.net.LocalSocketAddress.Namespace.ABSTRACT));
            if (socket.getPeerCredentials().getUid() != 0) throw new SecurityException("Service is not root");
            socket.setSoTimeout(2000);
            socket.getOutputStream().write((command + "\n").getBytes(StandardCharsets.UTF_8));
            return new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)).readLine();
        }
    }

    private static boolean triggerReady() {
        if (stopping) return false;
        try { DisplayState now = displayState(); return now.state == 2 && !now.locked; }
        catch (Exception unavailable) { return false; }
    }

    private static void revokeTrigger() {
        // A stopped service cannot answer future preparations; also revoke already-acknowledged holds.
        try (LocalSocket hook = new LocalSocket()) {
            hook.connect(new android.net.LocalSocketAddress(ServiceProtocol.HOOK_SOCKET, android.net.LocalSocketAddress.Namespace.ABSTRACT));
            android.net.Credentials peer = hook.getPeerCredentials();
            if (peer.getUid() != systemUiUid) return;
            String name = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("/proc/" + peer.getPid() + "/cmdline")),
                StandardCharsets.UTF_8).split("\u0000", 2)[0];
            if (!name.equals("com.android.systemui")) return;
            hook.setSoTimeout(700);
            hook.getOutputStream().write(("STOP_TRIGGER " + serviceInstance + "\n").getBytes(StandardCharsets.UTF_8));
            BufferedReader lines = new BufferedReader(new InputStreamReader(hook.getInputStream(), StandardCharsets.UTF_8));
            lines.readLine(); // Module identity, retained for older inspection clients.
            if (!TriggerProtocol.quiet(serviceInstance).equals(lines.readLine()))
                System.out.println("TRIGGER_QUIET unconfirmed; reload SystemUI module if outdated");
        } catch (Exception unavailable) {
            // Do not prevent stopping when SystemUI is absent or still running an older module.
            System.out.println("TRIGGER_QUIET unavailable; fresh holds still require a live root service");
        }
    }

    private static boolean isAbsent(java.io.IOException error) {
        String message = error.getMessage();
        return message != null && (message.contains("Connection refused") || message.contains("No such file or directory"));
    }

    private static boolean serviceRunning() throws Exception {
        try {
            String result = control("STATUS");
            if (result == null || !result.startsWith("active=")) throw new IllegalStateException("Invalid service response");
            return true; // An idle service is still running; active describes the answer task.
        } catch (java.io.IOException error) {
            if (isAbsent(error)) return false;
            throw error; // Timeouts and denied access do not mean stopped.
        }
    }

    private static void printInspection() throws Exception {
        System.out.println("ANSIT_ROOT_OK");
        if (serviceRunning()) {
            System.out.println("ANSIT_SERVICE_RUNNING");
            String health = control("HEALTH");
            java.util.regex.Matcher identity = java.util.regex.Pattern.compile(
                "version=([A-Za-z0-9._-]+) instance=[A-Za-z0-9_-]+(?: apk=([a-f0-9]{64}))?").matcher(health == null ? "" : health);
            if (!identity.matches()) throw new IllegalStateException("Invalid health response");
            System.out.println("ANSIT_SERVICE_IDENTITY " + identity.group(1) + " " + (identity.group(2) == null ? "legacy" : identity.group(2)));
        } else System.out.println("ANSIT_SERVICE_STOPPED");
        try (LocalSocket hook = new LocalSocket()) {
            hook.connect(new android.net.LocalSocketAddress(ServiceProtocol.HOOK_SOCKET, android.net.LocalSocketAddress.Namespace.ABSTRACT));
            android.net.Credentials peer = hook.getPeerCredentials();
            if (peer.getUid() != resolveSystemUiUid()) throw new SecurityException("Unexpected hook uid");
            byte[] process = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("/proc/" + peer.getPid() + "/cmdline"));
            String name = new String(process, StandardCharsets.UTF_8).split("\u0000", 2)[0];
            if (!name.equals("com.android.systemui")) throw new SecurityException("Unexpected hook process");
            hook.setSoTimeout(1500);
            String health = new BufferedReader(new InputStreamReader(hook.getInputStream(), StandardCharsets.UTF_8)).readLine();
            java.util.regex.Matcher identity = java.util.regex.Pattern.compile("version=([A-Za-z0-9._-]+) apk=([a-f0-9]{64})").matcher(health == null ? "" : health);
            if (!identity.matches()) throw new IllegalStateException("Invalid hook response");
            System.out.println("ANSIT_HOOK_LOADED " + identity.group(1) + " " + identity.group(2));
        } catch (java.io.IOException missing) {
            System.out.println(isAbsent(missing) ? "ANSIT_HOOK_ABSENT" : "ANSIT_HOOK_UNKNOWN");
        } catch (Exception unknown) { System.out.println("ANSIT_HOOK_UNKNOWN"); }
    }

    private static void restartSystemUi() throws Exception {
        Process query = new ProcessBuilder("/system/bin/pidof", "com.android.systemui").start();
        String pids = new String(StreamCompat.readNBytes(query.getInputStream(), 4097), StandardCharsets.UTF_8).trim();
        if (!query.waitFor(3, TimeUnit.SECONDS) || query.exitValue() != 0 || !pids.matches("[0-9]+"))
            throw new IllegalStateException("Cannot identify SystemUI");
        int pid = Integer.parseInt(pids);
        if (android.system.Os.stat("/proc/" + pid).st_uid != resolveSystemUiUid())
            throw new SecurityException("Unexpected SystemUI uid");
        String process = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("/proc/" + pid + "/cmdline")), StandardCharsets.UTF_8);
        if (!process.split("\u0000", 2)[0].equals("com.android.systemui")) throw new SecurityException("SystemUI identity changed");
        android.os.Process.sendSignal(pid, 15);
        System.out.println("ANSIT_SYSTEMUI_RESTART_SENT");
    }

    private static void bootstrap(String apk, java.io.File log) throws Exception {
        String expectedApk = BuildIdentity.digest(apk);
        try { control("STOP"); } catch (java.io.IOException absent) { if (!isAbsent(absent)) throw absent; }
        boolean stopped = false;
        for (int attempt = 0; attempt < 30; attempt++) {
            try { control("STATUS"); }
            catch (java.io.IOException gone) { if (!isAbsent(gone)) throw gone; stopped = true; break; }
            Thread.sleep(100);
        }
        if (!stopped) throw new IllegalStateException("Previous service did not stop");
        String nonce = java.util.UUID.randomUUID().toString();
        try (var file = new java.io.FileOutputStream(log)) { android.system.Os.chmod(log.getPath(), 0600); }
        ProcessBuilder builder = new ProcessBuilder("/system/bin/nohup", "/system/bin/app_process", "/system/bin", "--nice-name=ansit-root", "com.antioblivionis.ansit.RootMain", "serve", nonce);
        builder.environment().put("CLASSPATH", apk);
        builder.redirectInput(ProcessBuilder.Redirect.from(new java.io.File("/dev/null")));
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(log)); builder.redirectErrorStream(true);
        Process service = builder.start();
        String expected = "version=" + SERVICE_VERSION + " instance=" + nonce + " apk=" + expectedApk;
        for (int attempt = 0; attempt < 100; attempt++) {
            try {
                if (expected.equals(control("HEALTH"))) {
                    System.out.println("ANSIT_SERVICE_READY " + SERVICE_VERSION); return;
                }
            } catch (java.io.IOException ignored) { }
            if (!service.isAlive()) throw new IllegalStateException("New service exited");
            Thread.sleep(100);
        }
        service.destroy(); throw new IllegalStateException("New service health check failed");
    }

    private static synchronized void cancel() {
        generation.incrementAndGet(); active = false;
        if (pendingCall != null) { pendingCall.cancel(); pendingCall = null; }
        if (pendingTask != null) { pendingTask.cancel(true); pendingTask = null; }
        hide();
    }

    private static synchronized void setExcluded(boolean excluded) throws Exception {
        if (layer == null) throw new IllegalStateException("No answer layer");
        Object transaction = Class.forName("android.view.SurfaceControl$Transaction").getConstructor().newInstance();
        try {
            call(transaction, "setSkipScreenshot", new Class<?>[]{layer.getClass(), boolean.class}, layer, excluded);
            call(transaction, "apply", new Class<?>[0]);
        } finally { call(transaction, "close", new Class<?>[0]); }
    }

    private static synchronized void toggle() {
        long request = generation.incrementAndGet();
        if (active) { cancel(); System.out.println("CLOSED " + request); return; }
        try {
            displayedOn = displayState();
            if (displayedOn.locked || displayedOn.state != 2) return;
        } catch (Exception e) { e.printStackTrace(); return; }
        active = true;
        LlmCall api = new LlmCall(); pendingCall = api;
        pendingTask = worker.submit(() -> {
            Bitmap bitmap = null;
            try {
                ApiConfig config = ApiConfig.read(new java.io.File(CONFIG_DIR, ApiConfig.FILE_NAME));
                SearchConfig search;
                try { search = SearchConfig.read(new java.io.File(CONFIG_DIR, SearchConfig.FILE_NAME)); }
                catch (Exception invalidSearch) { search = SearchConfig.disabled(); }
                synchronized (RootMain.class) { if (!active || generation.get() != request) return; }
                bitmap = capture();
                String diagnostic = nextTriggerDump.getAndSet(null);
                if (diagnostic != null) {
                    java.io.File file = new java.io.File("/data/local/tmp/ansit-trigger-"+diagnostic+".png");
                    try (var out = new java.io.FileOutputStream(file)) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); }
                    android.system.Os.chmod(file.getPath(), 0600);
                    System.out.println("TRIGGER_FRAME " + request + " " + diagnostic);
                }
                synchronized (RootMain.class) {
                    if (!active || generation.get() != request) return;
                    show("正在解答…", bitmap.getWidth(), bitmap.getHeight());
                    System.out.println("REQUEST " + request);
                }
                String result = api.answer(config, search, bitmap);
                synchronized (RootMain.class) {
                    if (active && generation.get() == request) {
                        show(result, bitmap.getWidth(), bitmap.getHeight());
                        System.out.println("SHOWN " + request);
                    }
                }
            } catch (java.util.concurrent.CancellationException ignored) {
            } catch (Throwable t) {
                synchronized (RootMain.class) {
                    if (active && generation.get() == request) {
                        String message = t instanceof ApiConfig.Invalid || t instanceof LlmCall.Failure ? t.getMessage() : "识屏或请求失败，请重试";
                        try { show(message, displayedOn.width, displayedOn.height); System.out.println("ERROR " + request); }
                        catch (Exception drawing) { active = false; hide(); }
                    }
                }
            } finally {
                if (bitmap != null) bitmap.recycle();
                synchronized (RootMain.class) { if (pendingCall == api) { pendingCall = null; pendingTask = null; } }
            }
        });
    }

    private static Object call(Object receiver, String name, Class<?>[] signature, Object... args) throws Exception {
        Class<?> type = receiver instanceof Class<?> ? (Class<?>)receiver : receiver.getClass();
        Method method = type.getMethod(name, signature);
        method.setAccessible(true);
        return method.invoke(receiver instanceof Class<?> ? null : receiver, args);
    }

    private static DisplayState displayState() throws Exception {
        Class<?> sm = Class.forName("android.os.ServiceManager");
        IBinder displayBinder = (IBinder)call(sm, "getService", new Class<?>[]{String.class}, "display");
        Object dm = call(Class.forName("android.hardware.display.IDisplayManager$Stub"), "asInterface", new Class<?>[]{IBinder.class}, displayBinder);
        Object info = call(dm, "getDisplayInfo", new Class<?>[]{int.class}, 0);
        if (info == null) throw new IllegalStateException("Display unavailable");
        IBinder windowBinder = (IBinder)call(sm, "getService", new Class<?>[]{String.class}, "window");
        Object wm = call(Class.forName("android.view.IWindowManager$Stub"), "asInterface", new Class<?>[]{IBinder.class}, windowBinder);
        boolean locked = (boolean)call(wm, "isKeyguardLocked", new Class<?>[0]);
        Class<?> type = info.getClass();
        String uniqueId = (String)type.getField("uniqueId").get(info);
        if (uniqueId == null || !uniqueId.startsWith("local:")) throw new IllegalStateException("Default display is not physical");
        return new DisplayState(type.getField("logicalWidth").getInt(info), type.getField("logicalHeight").getInt(info),
            type.getField("rotation").getInt(info), type.getField("state").getInt(info), type.getField("layerStack").getInt(info),
            Long.parseLong(uniqueId.substring(6)), locked);
    }

    // This class executes under root app_process, outside normal application hidden-API limits.
    // These system-server bindings are deliberately device-specific; no ordinary-app fallback.
    @android.annotation.SuppressLint("BlockedPrivateApi")
    private static synchronized Class<?> getDisplayControl() throws Exception {
        Class<?> dc = displayControl;
        if (dc == null) {
        try { dc = Class.forName("android.view.DisplayControl"); }
        catch (ClassNotFoundException old) {
            Class<?> legacy = Class.forName("android.view.SurfaceControl");
            try {
                legacy.getMethod("getPhysicalDisplayIds");
                legacy.getMethod("getPhysicalDisplayToken", long.class);
                displayControl = legacy;
                return legacy;
            } catch (NoSuchMethodException moved) { }
            // Use the system-server linker namespace (also used by scrcpy's DisplayControl wrapper).
            Class<?> factory = Class.forName("com.android.internal.os.ClassLoaderFactory");
            Method create = factory.getDeclaredMethod("createClassLoader", String.class, String.class,
                String.class, ClassLoader.class, int.class, boolean.class, String.class);
            ClassLoader loader = (ClassLoader)create.invoke(null, android.system.Os.getenv("SYSTEMSERVERCLASSPATH"),
                null, null, ClassLoader.getSystemClassLoader(), 0, true, null);
            dc = loader.loadClass("com.android.server.display.DisplayControl");
            Method load = Runtime.class.getDeclaredMethod("loadLibrary0", Class.class, String.class);
            load.setAccessible(true);
            load.invoke(Runtime.getRuntime(), dc, "android_servers");
        }
        displayControl = dc;
        }
        return dc;
    }

    private static Bitmap capture() throws Exception {
        DisplayState before = displayState();
        if (before.locked || before.state != 2) throw new IllegalStateException("Display not capturable");
        Class<?> dc = getDisplayControl();
        long[] ids = (long[])call(dc, "getPhysicalDisplayIds", new Class<?>[0]);
        if (ids.length == 0) throw new IllegalStateException("No physical display");
        long physicalId = before.physicalId;
        if (java.util.Arrays.stream(ids).noneMatch(id -> id == physicalId)) throw new IllegalStateException("Default display token unavailable");
        IBinder token = (IBinder)call(dc, "getPhysicalDisplayToken", new Class<?>[]{long.class}, physicalId);
        Class<?> capture = CaptureCompat.captureType();
        String prefix = capture.getName();
        Class<?> builderType = Class.forName(prefix + "$DisplayCaptureArgs$Builder");
        Object builder = builderType.getConstructor(IBinder.class).newInstance(token);
        CaptureCompat.excludeSystemOverlays(builder);
        // Fail if no secure-content policy is available; never silently downgrade.
        try { call(builder, "setCaptureSecureLayers", new Class<?>[]{boolean.class}, true); }
        catch (NoSuchMethodException newer) { call(builder, "setSecureContentPolicy", new Class<?>[]{int.class}, 1); }
        Object captureArgs = call(builder, "build", new Class<?>[0]);
        Object buffer = call(capture, "captureDisplay", new Class<?>[]{Class.forName(prefix + "$DisplayCaptureArgs")}, captureArgs);
        if (buffer == null) throw new IllegalStateException("Capture returned null");
        Bitmap bitmap = (Bitmap)call(buffer, "asBitmap", new Class<?>[0]);
        if (bitmap == null) throw new IllegalStateException("No captured bitmap");
        Bitmap copy;
        try { copy = bitmap.copy(Bitmap.Config.ARGB_8888, false); }
        finally {
            bitmap.recycle();
            HardwareBuffer hw = (HardwareBuffer)call(buffer, "getHardwareBuffer", new Class<?>[0]);
            if (hw != null) hw.close();
        }
        if (copy == null) throw new IllegalStateException("Bitmap copy failed");
        if (copy.getColorSpace() != null && !copy.getColorSpace().isSrgb()) {
            Bitmap srgb = Bitmap.createBitmap(copy.getWidth(), copy.getHeight(), Bitmap.Config.ARGB_8888);
            try { new Canvas(srgb).drawBitmap(copy, 0, 0, null); }
            catch (Throwable t) { srgb.recycle(); throw t; }
            finally { copy.recycle(); }
            copy = srgb;
        }
        DisplayState after = displayState();
        if (!before.equals(after) || copy.getWidth() != after.width || copy.getHeight() != after.height) {
            copy.recycle(); throw new IllegalStateException("Display changed during capture or geometry mismatch");
        }
        return copy;
    }

    private static synchronized void show(String text, int screenWidth, int screenHeight) throws Exception {
        hide();
        Class<?> sc = Class.forName("android.view.SurfaceControl");
        Class<?> bc = Class.forName("android.view.SurfaceControl$Builder");
        OverlayConfig config;
        try { config = OverlayConfig.read(new java.io.File(CONFIG_DIR, OverlayConfig.FILE_NAME)); }
        catch (Exception invalid) { config = OverlayConfig.defaults(); }
        OverlayConfig.Pixels bounds = config.pixels(screenWidth, screenHeight);
        int width = bounds.width(), height = bounds.height();
        Object builder = bc.getConstructor().newInstance();
        call(builder, "setName", new Class<?>[]{String.class}, "AnsIt.Answer");
        call(builder, "setBufferSize", new Class<?>[]{int.class, int.class}, width, height);
        call(builder, "setFormat", new Class<?>[]{int.class}, PixelFormat.TRANSLUCENT);
        // Set before first presentation: no frame may be shown without exclusion.
        call(builder, "setFlags", new Class<?>[]{int.class}, 0x40);
        layer = call(builder, "build", new Class<?>[0]);
        surface = Surface.class.getConstructor(sc).newInstance(layer);
        Canvas canvas = surface.lockHardwareCanvas();
        try {
            canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR);
            AnswerPainter.draw(canvas, text, width, height, screenWidth, screenHeight, config);
        } finally { surface.unlockCanvasAndPost(canvas); }
        Object transaction = Class.forName("android.view.SurfaceControl$Transaction").getConstructor().newInstance();
        try {
            call(transaction, "setLayerStack", new Class<?>[]{sc, int.class}, layer, displayedOn.layerStack);
            call(transaction, "setLayer", new Class<?>[]{sc, int.class}, layer, 2_000_000);
            call(transaction, "setTrustedOverlay", new Class<?>[]{sc, boolean.class}, layer, true);
            call(transaction, "setPosition", new Class<?>[]{sc, float.class, float.class}, layer, (float)bounds.x(), (float)bounds.y());
            call(transaction, "show", new Class<?>[]{sc}, layer);
            call(transaction, "apply", new Class<?>[0]);
        } finally { call(transaction, "close", new Class<?>[0]); }
        shownText = text;
    }

    private static synchronized void hide() {
        shownText = null;
        if (surface != null) { surface.release(); surface = null; }
        if (layer != null) {
            try {
                Object t = Class.forName("android.view.SurfaceControl$Transaction").getConstructor().newInstance();
                try { call(t, "remove", new Class<?>[]{layer.getClass()}, layer); call(t, "apply", new Class<?>[0]); }
                finally { call(t, "close", new Class<?>[0]); }
                call(layer, "release", new Class<?>[0]);
            } catch (Throwable e) { e.printStackTrace(); }
            layer = null;
        }
    }
}
