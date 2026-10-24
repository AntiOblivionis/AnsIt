package com.antioblivionis.ansit;

import android.app.Activity;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import java.io.File;
import java.io.FileOutputStream;
import java.util.function.Consumer;
import org.json.JSONObject;

/** Known-content fixture; no personal app content is needed for acceptance. */
public final class DiagnosticActivity extends Activity {
    private int captures, touches, obscured, recordingEvents, recordingState = -1, focusLosses, pauses, resumes, mediaEvents;
    private Object captureCallback;
    private Pattern view;
    private final Consumer<Integer> recording = state -> { recordingEvents++; recordingState = state; report(); };
    private final ContentObserver mediaObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override public void onChange(boolean selfChange) { mediaEvents++; report(); }
    };
    private final BroadcastReceiver command = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent.hasExtra("phase")) { view.phase = intent.getIntExtra("phase", 0); view.invalidate(); }
            String label = intent.getStringExtra("label");
            if (label != null && label.matches("[a-z0-9-]{1,40}")) view.postDelayed(() -> saveState(label), 100);
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        view = new Pattern(); setContentView(view);
        registerReceiver(command, new IntentFilter("com.antioblivionis.ansit.FIXTURE"), "android.permission.DUMP", null,
            Build.VERSION.SDK_INT >= 33 ? Context.RECEIVER_EXPORTED : 0);
        getContentResolver().registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, mediaObserver);
    }
    @Override protected void onStart() {
        super.onStart();
        if (Build.VERSION.SDK_INT >= 34) captureCallback = Api34.register(this);
        if (Build.VERSION.SDK_INT >= 35) recordingState = Api35.register(this);
        report();
    }
    @Override protected void onStop() {
        if (Build.VERSION.SDK_INT >= 34 && captureCallback != null) {
            Api34.unregister(this, captureCallback); captureCallback = null;
        }
        if (Build.VERSION.SDK_INT >= 35) Api35.unregister(this);
        super.onStop();
    }

    @android.annotation.TargetApi(34)
    private static final class Api34 {
        static Object register(DiagnosticActivity activity) {
            ScreenCaptureCallback callback = () -> {
                activity.captures++; activity.report(); activity.view.invalidate();
            };
            activity.registerScreenCaptureCallback(activity.getMainExecutor(), callback);
            return callback;
        }
        static void unregister(DiagnosticActivity activity, Object callback) {
            activity.unregisterScreenCaptureCallback((ScreenCaptureCallback) callback);
        }
    }
    @android.annotation.TargetApi(35)
    private static final class Api35 {
        static int register(DiagnosticActivity activity) {
            return activity.getWindowManager().addScreenRecordingCallback(activity.getMainExecutor(), activity.recording);
        }
        static void unregister(DiagnosticActivity activity) {
            activity.getWindowManager().removeScreenRecordingCallback(activity.recording);
        }
    }
    @Override protected void onResume() { super.onResume(); resumes++; report(); }
    @Override protected void onPause() { pauses++; report(); super.onPause(); }
    @Override public void onWindowFocusChanged(boolean focused) { super.onWindowFocusChanged(focused); if (!focused) focusLosses++; report(); }
    @Override protected void onDestroy() { unregisterReceiver(command); getContentResolver().unregisterContentObserver(mediaObserver); super.onDestroy(); }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        view.phase = intent.getIntExtra("phase", view.phase);
        view.invalidate(); report();
    }
    private void report() {
        Log.i("AnsItFixture", "captures="+captures+" recordingEvents="+recordingEvents+" recordingState="+recordingState
            +" focusLosses="+focusLosses+" pauses="+pauses+" resumes="+resumes+" mediaEvents="+mediaEvents
            +" touches="+touches+" obscured="+obscured+" phase="+(view == null ? -1 : view.phase));
    }
    private void saveState(String label) {
        try {
            int[] location = new int[2]; view.getLocationOnScreen(location);
            JSONObject info = new JSONObject().put("uid", android.os.Process.myUid()).put("captures", captures)
                .put("recordingEvents",recordingEvents).put("recordingState",recordingState).put("focusLosses",focusLosses)
                .put("pauses",pauses).put("resumes",resumes).put("mediaEvents",mediaEvents).put("touches",touches)
                .put("obscured",obscured).put("phase",view.phase).put("x",location[0]).put("y",location[1])
                .put("width",view.getWidth()).put("height",view.getHeight()).put("focused",hasWindowFocus());
            try (var out = new FileOutputStream(new File(getFilesDir(), label+".json"))) { out.write(info.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
            Bitmap expected = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
            try {
                view.draw(new Canvas(expected));
                try (var out = new FileOutputStream(new File(getFilesDir(),label+".png"))) { expected.compress(Bitmap.CompressFormat.PNG,100,out); }
            } finally { expected.recycle(); }
            Log.i("AnsItFixture", "SAVED " + label + " " + info);
        } catch (Exception e) { Log.e("AnsItFixture", "save failed", e); }
    }
    private final class Pattern extends View {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        int phase;
        Pattern() { super(DiagnosticActivity.this); }
        @Override protected void onDraw(Canvas c) {
            int[] colors = {0xFF1976D2, 0xFFFFC107, 0xFF008577, 0xFFE64A19};
            for (int y=0; y<getHeight(); y+=100) for (int x=0; x<getWidth(); x+=100) {
                paint.setColor(colors[((x/100)+(y/100)+phase) & 3]);
                c.drawRect(x,y,x+100,y+100,paint);
            }
            paint.setTextSize(38); paint.setColor(Color.WHITE);
            c.drawText("AnsIt SECURE fixture phase="+phase, 24, 160, paint);
            c.drawText("captureCallbacks="+captures+" touches="+touches,24,220,paint);
            c.drawText("obscuredTouches="+obscured,24,280,paint);
            c.drawText("底图必须完整；答案区域可直接触摸",24,340,paint);
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                touches++;
                if ((e.getFlags() & (MotionEvent.FLAG_WINDOW_IS_OBSCURED | MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED)) != 0) obscured++;
                report(); invalidate(); performClick();
            }
            return true;
        }
        @Override public boolean performClick() { super.performClick(); return true; }
    }
}
