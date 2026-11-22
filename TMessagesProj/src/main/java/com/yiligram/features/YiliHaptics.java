package com.yiligram.features;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.HapticFeedbackConstants;
import android.view.View;

import com.yiligram.config.YiliConfig;

import org.telegram.messenger.ApplicationLoader;

/**
 * Haptic feedback with a user-chosen strength. SYSTEM keeps Telegram's own feedback; the other levels use
 * predefined effects (Android 10+) or short one-shots.
 */
public final class YiliHaptics {

    public enum Strength { OFF, SYSTEM, LIGHT, MEDIUM, STRONG }

    /** Weight of an event: a tick (switches, scrolling), a click (actions) or a heavy click (thresholds). */
    public enum Kind { TICK, CLICK, HEAVY }

    private YiliHaptics() {
    }

    public static void tick(View view) {
        perform(view, Kind.TICK, HapticFeedbackConstants.KEYBOARD_TAP);
    }

    public static void click(View view) {
        perform(view, Kind.CLICK, HapticFeedbackConstants.KEYBOARD_TAP);
    }

    public static void heavy(View view) {
        perform(view, Kind.HEAVY, HapticFeedbackConstants.LONG_PRESS);
    }

    private static void perform(View view, Kind kind, int systemConstant) {
        Strength strength = YiliConfig.get(YiliConfig.HAPTICS);
        try {
            switch (strength) {
                case OFF:
                    return;
                case SYSTEM:
                    if (view != null) {
                        view.performHapticFeedback(systemConstant, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                    }
                    return;
                default:
                    vibrate(strength, kind);
            }
        } catch (Exception ignore) {
        }
    }

    private static void vibrate(Strength strength, Kind kind) {
        Vibrator vibrator = (Vibrator) ApplicationLoader.applicationContext.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) return;
        int level = strength.ordinal() - Strength.LIGHT.ordinal() + kind.ordinal(); // 0..4
        if (Build.VERSION.SDK_INT >= 29) {
            int effect = level <= 0 ? VibrationEffect.EFFECT_TICK
                    : level <= 2 ? VibrationEffect.EFFECT_CLICK : VibrationEffect.EFFECT_HEAVY_CLICK;
            vibrator.vibrate(VibrationEffect.createPredefined(effect));
        } else if (Build.VERSION.SDK_INT >= 26) {
            int[] ms = {8, 12, 18, 24, 32};
            int[] amp = {60, 110, 160, 210, 255};
            vibrator.vibrate(VibrationEffect.createOneShot(ms[level], amp[level]));
        } else {
            vibrator.vibrate(10 + level * 6);
        }
    }
}
