package com.yiligram.config;

import androidx.annotation.StringRes;

import com.yiligram.avatars.AvatarFrames;
import com.yiligram.design.motion.YiliMotion;
import com.yiligram.wallpaper.YiliWallpaper;

import org.telegram.messenger.R;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * How much Material 3 the interface uses: Telegram's look, half (M3 colors and controls on Telegram's
 * layout) or full Material 3 Expressive. Colors and fonts are left as they are.
 */
public final class YiliStyles {

    public enum Level {
        TELEGRAM(R.string.YiliStyleTelegram, R.string.YiliStyleTelegramInfo),
        HALF(R.string.YiliStyleHalf, R.string.YiliStyleHalfInfo),
        FULL(R.string.YiliStyleFull, R.string.YiliStyleFullInfo);

        @StringRes public final int title;
        @StringRes public final int info;

        Level(int title, int info) {
            this.title = title;
            this.info = info;
        }
    }

    private YiliStyles() {
    }

    public static Map<YiliConfig.Key<?>, Object> values(Level level) {
        Map<YiliConfig.Key<?>, Object> v = new LinkedHashMap<>();
        switch (level) {
            case TELEGRAM:
                v.put(YiliConfig.M3_CONTROLS, false);
                v.put(YiliConfig.BUBBLE_SHAPE, YiliConfig.BubbleShape.ROUNDED);
                v.put(YiliConfig.BUBBLE_TAILS, true);
                v.put(YiliConfig.AVATAR_FRAME, AvatarFrames.Frame.CIRCLE);
                v.put(YiliConfig.M3_PROFILE, false);
                v.put(YiliConfig.M3_WALLPAPER, YiliWallpaper.Style.OFF);
                v.put(YiliConfig.SHAPE_MORPH, false);
                v.put(YiliConfig.SHAPE_ROUNDNESS, 0.6f);
                v.put(YiliConfig.MOTION_SCHEME, YiliMotion.Scheme.STANDARD);
                break;
            case HALF:
                v.put(YiliConfig.M3_CONTROLS, true);
                v.put(YiliConfig.BUBBLE_SHAPE, YiliConfig.BubbleShape.ROUNDED);
                v.put(YiliConfig.BUBBLE_TAILS, true);
                v.put(YiliConfig.AVATAR_FRAME, AvatarFrames.Frame.CIRCLE);
                v.put(YiliConfig.M3_PROFILE, false);
                v.put(YiliConfig.M3_WALLPAPER, YiliWallpaper.Style.OFF);
                v.put(YiliConfig.SHAPE_MORPH, false);
                v.put(YiliConfig.SHAPE_ROUNDNESS, 1f);
                v.put(YiliConfig.MOTION_SCHEME, YiliMotion.Scheme.EXPRESSIVE);
                break;
            case FULL:
                v.put(YiliConfig.M3_CONTROLS, true);
                v.put(YiliConfig.BUBBLE_SHAPE, YiliConfig.BubbleShape.M3);
                v.put(YiliConfig.BUBBLE_TAILS, false);
                v.put(YiliConfig.AVATAR_FRAME, AvatarFrames.Frame.COOKIE_9);
                v.put(YiliConfig.M3_PROFILE, true);
                v.put(YiliConfig.M3_WALLPAPER, YiliWallpaper.Style.SHAPES);
                v.put(YiliConfig.SHAPE_MORPH, true);
                v.put(YiliConfig.SHAPE_ROUNDNESS, 1.4f);
                v.put(YiliConfig.MOTION_SCHEME, YiliMotion.Scheme.EXPRESSIVE);
                break;
        }
        return v;
    }

    public static void apply(Level level) {
        YiliConfig.setAll(values(level));
        org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(org.telegram.messenger.NotificationCenter.didSetNewWallpapper);
    }

    /** The level the current settings match, or null for a custom mix. */
    public static Level current() {
        for (Level l : Level.values()) {
            boolean match = true;
            for (Map.Entry<YiliConfig.Key<?>, Object> e : values(l).entrySet()) {
                if (!e.getValue().equals(YiliConfig.get(e.getKey()))) {
                    match = false;
                    break;
                }
            }
            if (match) return l;
        }
        return null;
    }
}
