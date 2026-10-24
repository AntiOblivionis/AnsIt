package com.antioblivionis.ansit;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.HapticFeedbackConstants;
import android.view.View;
import de.robv.android.xposed.XposedBridge;

/** Called once when the hold is accepted, before the finger is released. Runs inside SystemUI. */
final class HoldFeedback {
    static void signal(View root) {
        try {
            Context context = root.getContext();
            VibratorManager manager = context.getSystemService(VibratorManager.class);
            Vibrator vibrator = manager == null ? context.getSystemService(Vibrator.class) : manager.getDefaultVibrator();
            if (vibrator == null || !vibrator.hasVibrator()) {
                XposedBridge.log("AnsIt: no vibrator available for hold feedback");
                return;
            }
            int amplitude = vibrator.hasAmplitudeControl() ? 80 : VibrationEffect.DEFAULT_AMPLITUDE;
            VibrationEffect effect = VibrationEffect.createOneShot(24, amplitude);
            if (Build.VERSION.SDK_INT >= 33) Api33.vibrate(context, vibrator, effect);
            else vibrator.vibrate(effect, new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build());
        } catch (RuntimeException failure) {
            // Feedback must never take down SystemUI or prevent the accepted hold from finishing.
            XposedBridge.log("AnsIt: direct hold feedback failed: " + failure.getClass().getSimpleName());
            try {
                if (!root.performHapticFeedback(HapticFeedbackConstants.CONFIRM, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING))
                    XposedBridge.log("AnsIt: fallback hold feedback was rejected");
            } catch (RuntimeException ignored) { }
        }
    }

    @android.annotation.TargetApi(33)
    private static final class Api33 {
        static void vibrate(Context context, Vibrator vibrator, VibrationEffect effect) {
            VibrationAttributes.Builder attributes = new VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_TOUCH);
            // SystemUI is allowed to request a deliberate acknowledgement even if touch intensity
            // is off. This does not change the user's global setting or bypass interruption policy.
            if (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED) {
                try {
                    int flag = VibrationAttributes.class.getField("FLAG_BYPASS_USER_VIBRATION_INTENSITY_OFF").getInt(null);
                    attributes.setFlags(flag, flag);
                } catch (ReflectiveOperationException ignored) {
                    XposedBridge.log("AnsIt: touch-intensity override unavailable on this ROM");
                }
            }
            vibrator.vibrate(effect, attributes.build());
        }
    }
    private HoldFeedback() { }
}
