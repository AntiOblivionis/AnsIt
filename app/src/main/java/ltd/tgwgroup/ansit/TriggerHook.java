package ltd.tgwgroup.ansit;

import android.app.KeyguardManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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
    private final Map<StatusBarTap.Touch, StatusBarTap> scrollTaps = new HashMap<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private static volatile long lastFiredDownTime = Long.MIN_VALUE;
    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p) {
        if (!p.packageName.equals("com.android.systemui") || !p.processName.equals(p.packageName)) return;
        try {
            if (!installStatusBarTouch(p.classLoader)) {
                XposedBridge.log("AnsIt: no supported status bar view");
                return;
            }
            installHyperOsScrollGuard(p.classLoader);
            XposedBridge.log("AnsIt: status bar touch trigger installed");
            HookStatus.start(loadedApk);
        } catch (Throwable t) { XposedBridge.log(t); }
    }

    private void installHyperOsScrollGuard(ClassLoader loader) {
        try {
            Class<?> listener = XposedHelpers.findClassIfExists(
                "com.android.systemui.statusbar.gesture.StatusBarLongPressGestureDetector$gestureDetector$1", loader);
            if (listener == null) return;
            Method single = listener.getDeclaredMethod("onSingleTapConfirmed", MotionEvent.class);
            Method doubleTap = listener.getDeclaredMethod("onDoubleTapEvent", MotionEvent.class);
            XC_MethodHook guard = new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (Looper.myLooper() != Looper.getMainLooper()) return;
                    MotionEvent event = (MotionEvent) param.args[0];
                    StatusBarTap tap = scrollTaps.get(touch(event));
                    if (tap == null || tap.scrollAllowed()) return;
                    if (tap.awaitsScrollDecision()) {
                        MotionEvent copy = MotionEvent.obtain(event);
                        Object receiver = param.thisObject;
                        Method method = (Method) param.method;
                        tap.deferScroll(allow -> {
                            try {
                                if (allow) XposedBridge.invokeOriginalMethod(method, receiver, new Object[]{copy});
                            } catch (Throwable error) {
                                XposedBridge.log("AnsIt: status bar scroll callback failed: " + error.getClass().getSimpleName());
                            } finally { copy.recycle(); }
                        });
                    }
                    // HyperOS can request the same double-tap scroll on DOWN and UP. Handle it
                    // once, after classification, without touching the status bar's event stream.
                    param.setResult(true);
                }
            };
            XposedBridge.hookMethod(single, guard);
            XposedBridge.hookMethod(doubleTap, guard);
            XposedBridge.log("AnsIt: HyperOS status bar scroll guard installed");
        } catch (Throwable unavailable) {
            XposedBridge.log("AnsIt: status bar scroll guard unavailable ("
                + unavailable.getClass().getSimpleName() + ")");
        }
    }

    private static StatusBarTap.Touch touch(MotionEvent event) {
        return new StatusBarTap.Touch(event.getDeviceId(), event.getSource(), event.getDownTime());
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
        StatusBarTap downTap;
        StatusBarTap pendingTap;
        StatusBarTap firstOfDoubleTap;
        boolean detached;
        long firstTapUpAt;
        float x, y;
        final Runnable hold = this::onHold;
        final Runnable singleTap = this::fireSingleTap;
        Gesture(View root) {
            this.root = root;
            root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View v) { }
                @Override public void onViewDetachedFromWindow(View v) {
                    detached = true;
                    reset(); handler.removeCallbacks(singleTap);
                    resolveTap(pendingTap, false); pendingTap = null;
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
            cancelDownTap();
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
            return pendingTap != null && !pendingTap.scrollAllowed()
                && pendingTap.touch.deviceId() == event.getDeviceId() && pendingTap.touch.source() == event.getSource()
                && event.getEventTime() - firstTapUpAt <= doubleTapTimeout()
                && Math.hypot(event.getX() - pendingTap.x, event.getY() - pendingTap.y) <= slop();
        }
        void scheduleSingleTap(StatusBarTap tap, long upAt) {
            if (pendingTap != null) fireSingleTap();
            pendingTap = tap;
            firstTapUpAt = upAt;
            handler.postDelayed(singleTap, doubleTapTimeout());
        }
        void fireSingleTap() {
            handler.removeCallbacks(singleTap);
            StatusBarTap tap = pendingTap;
            pendingTap = null;
            if (tap != null) selectTap(tap, null);
        }
        void selectTap(StatusBarTap tap, StatusBarTap first) {
            // Preparation for both contacts is queued in touch order on the same worker.
            // Queue the decision behind them if the last contact's result is still in flight.
            if (tap.prepared) {
                finishTap(tap, first);
                return;
            }
            io.execute(() -> handler.post(() -> finishTap(tap, first)));
        }
        void finishTap(StatusBarTap tap, StatusBarTap first) {
            boolean claim = !detached && tap.active(gate) && !locked() && root.isShown()
                && (first == null || tap.sharesMode(first, gate));
            resolveTap(first, claim);
            resolveTap(tap, claim);
            if (claim) {
                TriggerProtocol.Ready ready = tap.ready;
                io.execute(() -> {
                    if (gate.allowed(tap.gateRevision, ready.instance()))
                        TriggerClient.next(ready.instance(), ready.modeToken(), first != null);
                });
            }
        }
        void resolveTap(StatusBarTap tap, boolean claim) {
            if (tap == null) return;
            tap.resolve(claim);
            if (tap.scrollAllowed()) scrollTaps.remove(tap.touch, tap);
            else handler.postDelayed(() -> scrollTaps.remove(tap.touch, tap), doubleTapTimeout());
        }
        void cancelDownTap() {
            resolveTap(downTap, false); downTap = null;
            resolveTap(firstOfDoubleTap, false); firstOfDoubleTap = null;
        }
        boolean accept(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                // A duplicated delivery of the same touch (two status bar view instances or a relayed
                // event) must not start a second session and immediately toggle the first one off.
                if (event.getDownTime() == lastFiredDownTime) return true;
                reset();
                if (locked() || !StatusBarTarget.hit(root, event.getX(), event.getY())) return false;
                if (secondTapCandidate(event)) {
                    handler.removeCallbacks(singleTap);
                    firstOfDoubleTap = pendingTap;
                    pendingTap = null;
                }
                x = event.getX(); y = event.getY();
                HoldState.Attempt attempt = state.begin(event.getDownTime(), gate.revision());
                StatusBarTap tap = new StatusBarTap(touch(event), x, y, attempt.gateRevision);
                downTap = tap;
                scrollTaps.put(tap.touch, tap);
                io.execute(() -> {
                    TriggerProtocol.Ready ready = TriggerClient.prepare();
                    handler.post(() -> {
                        tap.ready = ready;
                        tap.prepared = true;
                        if (!tap.active(gate)) resolveTap(tap, false);
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
                    reset();
                }
            }
            boolean consume = state.fired();
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                HoldState.Release release = state.release(action == MotionEvent.ACTION_UP);
                handler.removeCallbacks(hold);
                if (release.instance() != null) {
                    lastFiredDownTime = event.getDownTime();
                    io.execute(() -> {
                        if (gate.allowed(release.gateRevision(), release.instance())) TriggerClient.toggle(release.instance());
                    });
                } else if (action == MotionEvent.ACTION_UP && downTap != null && downTap.touch.equals(touch(event))
                    && Math.hypot(event.getX() - x, event.getY() - y) <= slop()
                    && StatusBarTarget.hit(root, event.getX(), event.getY()) && !locked()) {
                    if (firstOfDoubleTap != null) {
                        selectTap(downTap, firstOfDoubleTap);
                    } else {
                        scheduleSingleTap(downTap, event.getEventTime());
                    }
                    downTap = null;
                    firstOfDoubleTap = null;
                } else {
                    cancelDownTap();
                }
            }
            return consume;
        }
        void reset() {
            handler.removeCallbacks(hold);
            state.cancel();
            cancelDownTap();
        }
    }
}
