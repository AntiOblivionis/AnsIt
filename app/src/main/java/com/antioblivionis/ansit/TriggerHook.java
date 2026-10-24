package com.antioblivionis.ansit;

import android.app.KeyguardManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
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
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p) {
        if (!p.packageName.equals("com.android.systemui") || !p.processName.equals(p.packageName)) return;
        try {
            // ViewRootImpl dispatches each window's pointer event here before vendor View overrides.
            // Resolve the non-SDK entry through LSPosed; this never runs in the ordinary app.
            XposedHelpers.findAndHookMethod(View.class, "dispatchPointerEvent", MotionEvent.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    View root = (View) param.thisObject;
                    Gesture g = gestures.get(root);
                    if (!StatusBarTarget.isStatusBarWindow(root)) {
                        if (g != null) g.reset();
                        return;
                    }
                    MotionEvent event = (MotionEvent) param.args[0];
                    if (g == null) {
                        if (event.getActionMasked() != MotionEvent.ACTION_DOWN) return;
                        g = new Gesture(root); gestures.put(root, g);
                    }
                    if (g.accept(event)) param.setResult(true);
                }
            });
            deoptimizePointerCaller(p.classLoader);
            XposedBridge.log("AnsIt: framework status bar trigger installed");
            HookStatus.start(loadedApk);
        } catch (Throwable t) { XposedBridge.log(t); }
    }

    private static void deoptimizePointerCaller(ClassLoader loader) {
        try {
            // ART may inline the final View entry into its framework caller. LSPosed exposes
            // this extension even when modules compile against the legacy Xposed API.
            var deoptimize = XposedBridge.class.getMethod("deoptimizeMethod", java.lang.reflect.Member.class);
            Class<?> stage = Class.forName("android.view.ViewRootImpl$ViewPostImeInputStage", false, loader);
            for (var method : stage.getDeclaredMethods()) {
                if (method.getName().equals("processPointerEvent") || method.getName().equals("onProcess"))
                    deoptimize.invoke(null, method);
            }
        } catch (ReflectiveOperationException unavailable) {
            XposedBridge.log("AnsIt: pointer caller deoptimization unavailable (" + unavailable.getClass().getSimpleName() + ")");
        }
    }

    private final class Gesture {
        final View root;
        final Handler handler = new Handler(Looper.getMainLooper());
        final HoldState state = new HoldState();
        final TriggerGate gate = HookStatus.gate();
        boolean internalCancel;
        float x, y;
        final Runnable hold = this::onHold;
        Gesture(View root) {
            this.root = root;
            root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View v) { }
                @Override public void onViewDetachedFromWindow(View v) {
                    reset(); gestures.remove(v); v.removeOnAttachStateChangeListener(this);
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
                        MotionEvent cancel = MotionEvent.obtain(attempt.startedAt, SystemClock.uptimeMillis(),
                            MotionEvent.ACTION_CANCEL, x, y, 0);
                        internalCancel = true;
                        try { root.dispatchTouchEvent(cancel); }
                        finally { internalCancel = false; cancel.recycle(); }
                        HoldFeedback.signal(root);
                        return true;
                    });
                    if (!accepted) reset();
                });
            });
        }
        boolean accept(MotionEvent event) {
            int action = event.getActionMasked();
            if (internalCancel) return false;
            if (action == MotionEvent.ACTION_DOWN) {
                reset();
                if (locked() || !StatusBarTarget.hit(root, event.getX(), event.getY())) return false;
                x = event.getX(); y = event.getY();
                HoldState.Attempt attempt = state.begin(event.getDownTime(), gate.revision());
                io.execute(() -> {
                    if (!attempt.valid) return;
                    TriggerProtocol.Ready ready = TriggerClient.prepare();
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
                int slop = ViewConfiguration.get(root.getContext()).getScaledTouchSlop();
                if (event.getPointerCount() != 1 || Math.hypot(event.getX() - x, event.getY() - y) > slop) reset();
            }
            boolean consume = state.fired();
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                HoldState.Release release = state.release(action == MotionEvent.ACTION_UP);
                handler.removeCallbacks(hold);
                if (release.instance() != null) io.execute(() -> {
                    if (gate.allowed(release.gateRevision(), release.instance())) TriggerClient.toggle(release.instance());
                });
            }
            return consume;
        }
        void reset() { handler.removeCallbacks(hold); state.cancel(); }
    }
}
