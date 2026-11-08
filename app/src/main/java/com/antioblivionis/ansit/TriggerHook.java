package com.antioblivionis.ansit;

import android.app.KeyguardManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** No additional window, touch listener or exported broadcast receiver. */
public final class TriggerHook implements IXposedHookLoadPackage, IXposedHookZygoteInit {
    private String loadedApk;
    @Override public void initZygote(StartupParam startupParam) { loadedApk = startupParam.modulePath; }
    private final WeakHashMap<View, Gesture> gestures = new WeakHashMap<>();
    private final Set<View> pendingAttach = Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<View> attachedSources = Collections.newSetFromMap(new WeakHashMap<>());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    // Readiness refreshes these before every tap is classified; the root validates them again.
    private volatile String serviceInstance;
    private volatile boolean continuous;
    private volatile long modeToken;
    private static volatile long lastFiredDownTime = Long.MIN_VALUE;
    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p) {
        if (!p.packageName.equals("com.android.systemui") || !p.processName.equals(p.packageName)) return;
        try {
            if (!installStatusBarTouch(p.classLoader)) {
                XposedBridge.log("AnsIt: no supported status bar view");
                return;
            }
            XposedBridge.log("AnsIt: status bar touch trigger installed");
            HookStatus.start(loadedApk);
        } catch (Throwable t) { XposedBridge.log(t); }
    }

    /**
     * HyperOS/HyperCeiler and AOSP both expose a status bar view that is inflated once per window:
     * MiuiPhoneStatusBarView (HyperOS) and PhoneStatusBarView (AOSP). Hooking their lifecycle
     * gives us a real View to listen on, with no final framework entry or deoptimization involved.
     * HyperCeiler uses the same onFinishInflate pattern for its status-bar gestures.
     */
    private boolean installStatusBarTouch(ClassLoader loader) {
        String[] views = {
            "com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView",
            "com.android.systemui.statusbar.phone.PhoneStatusBarView"
        };
        for (String name : views) {
            Class<?> type = XposedHelpers.findClassIfExists(name, loader);
            if (type == null) continue;
            try {
                boolean hooked = false;
                for (String methodName : new String[]{"onFinishInflate", "onAttachedToWindow"}) {
                    Method method = findMethod(type, methodName);
                    if (method == null) continue;
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            if (param.thisObject instanceof View view) attachStatusBarTouch(view);
                        }
                    });
                    hooked = true;
                }
                if (!hooked) continue;
                XposedBridge.log("AnsIt: status bar touch target " + name);
                return true;
            } catch (Throwable unavailable) {
                XposedBridge.log("AnsIt: touch hook unavailable on " + name + " ("
                        + unavailable.getClass().getSimpleName() + ")");
            }
        }
        String[] roots = {
            "com.android.systemui.statusbar.window.StatusBarWindowView",
            "com.android.systemui.statusbar.phone.StatusBarWindowView"
        };
        for (String name : roots) {
            Class<?> type = XposedHelpers.findClassIfExists(name, loader);
            if (type == null) continue;
            try {
                Method attached = findMethod(type, "onAttachedToWindow");
                if (attached == null) continue;
                XposedBridge.hookMethod(attached, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (param.thisObject instanceof View view) attachStatusBarTouch(view);
                    }
                });
                XposedBridge.log("AnsIt: status bar root touch target " + name);
                return true;
            } catch (Throwable unavailable) {
                XposedBridge.log("AnsIt: root touch hook unavailable on " + name + " ("
                        + unavailable.getClass().getSimpleName() + ")");
            }
        }
        return false;
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameters) {
        for (Class<?> current = type; current != null && current != View.class; current = current.getSuperclass()) {
            try { return current.getDeclaredMethod(name, parameters); }
            catch (NoSuchMethodException ignored) { }
        }
        return null;
    }

    private void attachStatusBarTouch(View source) {
        if (attachedSources.contains(source)) return;
        View root = source.getRootView();
        if (!StatusBarTarget.isStatusBarWindow(root)) {
            // onFinishInflate can run before the window's LayoutParams are visible. Retry once when
            // the view is attached instead of silently keeping the legacy fragile hook.
            if (pendingAttach.add(source)) {
                source.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                    @Override public void onViewAttachedToWindow(View v) {
                        v.removeOnAttachStateChangeListener(this);
                        pendingAttach.remove(v);
                        attachStatusBarTouch(v);
                    }
                    @Override public void onViewDetachedFromWindow(View v) {
                        v.removeOnAttachStateChangeListener(this);
                        pendingAttach.remove(v);
                    }
                });
            }
            return;
        }
        source.setOnTouchListener((view, event) -> {
            Gesture gesture = gestures.get(root);
            if (gesture == null) {
                if (event.getActionMasked() != MotionEvent.ACTION_DOWN) return false;
                gesture = new Gesture(root);
                gestures.put(root, gesture);
            }
            if (source == root) return gesture.accept(event);
            // Listener coordinates are relative to the status bar view; the gesture target works in
            // status-bar-window coordinates. Move a copy so the original SystemUI event is untouched.
            int[] from = new int[2], to = new int[2];
            source.getLocationOnScreen(from);
            root.getLocationOnScreen(to);
            MotionEvent copy = MotionEvent.obtain(event);
            copy.offsetLocation(from[0] - to[0], from[1] - to[1]);
            try { return gesture.accept(copy); }
            finally { copy.recycle(); }
        });
        XposedBridge.log("AnsIt: status bar touch attached to " + source.getClass().getName());
        attachedSources.add(source);
    }

    private final class Gesture {
        final View root;
        final Handler handler = new Handler(Looper.getMainLooper());
        final HoldState state = new HoldState();
        final TriggerGate gate = HookStatus.gate();
        boolean secondTap;
        boolean moved;
        boolean tapEligible;
        boolean tapPending;
        long firstTapUpAt;
        float tapX, tapY;
        float x, y;
        final Runnable hold = this::onHold;
        final Runnable singleTap = this::fireSingleTap;
        Gesture(View root) {
            this.root = root;
            root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View v) { }
                @Override public void onViewDetachedFromWindow(View v) {
                    reset(); handler.removeCallbacks(singleTap); tapPending = false;
                    gestures.remove(v); v.removeOnAttachStateChangeListener(this);
                }
            });
        }
        boolean locked() {
            KeyguardManager keyguard = root.getContext().getSystemService(KeyguardManager.class);
            return keyguard == null || keyguard.isKeyguardLocked();
        }
        private void onHold() {
            HoldState.Attempt attempt = state.current();
            if (attempt == null) return;
            moved = true;
            secondTap = false;
            if (!StatusBarTarget.hit(root, x, y) || locked() || !gate.allowed(attempt.gateRevision, attempt.instance)) { reset(); return; }
            if (!state.requestConfirmation(attempt, SystemClock.uptimeMillis())) return;
            io.execute(() -> {
                if (!attempt.valid) return;
                boolean ready = TriggerClient.confirm(attempt.instance);
                handler.post(() -> {
                    if (!state.live(attempt)) return;
                    if (!ready || !StatusBarTarget.hit(root, x, y) || locked()) { reset(); return; }
                    boolean accepted = gate.runIfAllowed(attempt.gateRevision, attempt.instance, () -> {
                        if (!state.confirm(attempt, attempt.instance)) return false;
                        HoldFeedback.signal(root);
                        return true;
                    });
                    if (!accepted) reset();
                });
            });
        }
        int slop() { return ViewConfiguration.get(root.getContext()).getScaledTouchSlop(); }
        int doubleTapTimeout() { return ViewConfiguration.get(root.getContext()).getDoubleTapTimeout(); }
        boolean secondTapCandidate(MotionEvent event) {
            return tapPending && SystemClock.uptimeMillis() - firstTapUpAt <= doubleTapTimeout()
                && Math.hypot(event.getX() - tapX, event.getY() - tapY) <= slop()
                && !locked() && StatusBarTarget.hit(root, event.getX(), event.getY());
        }
        void scheduleSingleTap() {
            tapPending = true;
            firstTapUpAt = SystemClock.uptimeMillis();
            tapX = x; tapY = y;
            handler.removeCallbacks(singleTap);
            handler.postDelayed(singleTap, doubleTapTimeout());
        }
        void fireSingleTap() {
            tapPending = false;
            sendNext(false);
        }
        void fireDoubleTap() {
            sendNext(true);
        }
        void sendNext(boolean doubleTap) {
            String instance = serviceInstance;
            long token = modeToken;
            if (continuous && instance != null && token > 0) io.execute(() -> TriggerClient.next(instance, token, doubleTap));
        }
        boolean accept(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                // A duplicated delivery of the same touch (two status bar view instances or a relayed
                // event) must not start a second session and immediately toggle the first one off.
                if (event.getDownTime() == lastFiredDownTime) return true;
                boolean second = secondTapCandidate(event);
                reset();
                moved = false;
                if (second) {
                    handler.removeCallbacks(singleTap);
                    tapPending = false;
                    secondTap = true;
                }
                if (locked() || !StatusBarTarget.hit(root, event.getX(), event.getY())) return false;
                tapEligible = true;
                x = event.getX(); y = event.getY();
                HoldState.Attempt attempt = state.begin(event.getDownTime(), gate.revision());
                io.execute(() -> {
                    // Refresh readiness even when the finger left early: taps still need the mode token.
                    TriggerProtocol.Ready ready = TriggerClient.prepare();
                    if (ready != null) {
                        serviceInstance = ready.instance();
                        continuous = ready.mode();
                        modeToken = ready.modeToken();
                    }
                    handler.post(() -> {
                        if (!state.live(attempt)) return;
                        if (ready == null || !root.isShown() || locked()
                            || !gate.allowed(attempt.gateRevision, ready.instance()) || !state.arm(attempt, ready)) {
                            reset(); return;
                        }
                        handler.postDelayed(hold, state.remaining(attempt, SystemClock.uptimeMillis()));
                    });
                });
                return false;
            }
            if (!state.fired() && (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_POINTER_DOWN)) {
                if (event.getPointerCount() != 1 || Math.hypot(event.getX() - x, event.getY() - y) > slop()) {
                    moved = true;
                    reset();
                }
            }
            boolean consume = state.fired();
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                HoldState.Release release = state.release(action == MotionEvent.ACTION_UP);
                handler.removeCallbacks(hold);
                if (release.instance() != null) {
                    lastFiredDownTime = event.getDownTime();
                    secondTap = false;
                    io.execute(() -> {
                        if (gate.allowed(release.gateRevision(), release.instance())) TriggerClient.toggle(release.instance());
                    });
                } else if (action == MotionEvent.ACTION_UP && tapEligible && !moved) {
                    if (secondTap) {
                        secondTap = false;
                        fireDoubleTap();
                    } else {
                        scheduleSingleTap();
                    }
                }
            }
            return consume;
        }
        void reset() {
            handler.removeCallbacks(hold);
            state.cancel();
            secondTap = false;
            tapEligible = false;
        }
    }
}
