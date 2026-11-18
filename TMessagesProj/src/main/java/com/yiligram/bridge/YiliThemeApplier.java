package com.yiligram.bridge;

import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.res.Configuration;

import androidx.annotation.NonNull;

import com.yiligram.config.YiliAppearance;
import com.yiligram.config.YiliConfig;
import com.yiligram.design.color.DynamicColorSource;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.ui.ActionBar.Theme;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Re-applies the YiliGram theme when something it depends on changes: the user's color settings
 * ({@link YiliConfig}) or, with dynamic color, the system palette (new wallpaper).
 *
 * <p>Uses Telegram's own {@code needSetDayNightTheme} path, so every open screen recolors.
 */
public final class YiliThemeApplier implements YiliConfig.Listener, ComponentCallbacks {

    private static final Set<YiliConfig.Key<?>> COLOR_KEYS = new HashSet<>(Arrays.asList(
            YiliConfig.DYNAMIC_COLOR, YiliConfig.SEED_COLOR, YiliConfig.FOLLOW_SYSTEM_STYLE,
            YiliConfig.SCHEME_VARIANT, YiliConfig.CONTRAST, YiliConfig.AMOLED, YiliConfig.COLOR_SPEC));

    private static YiliThemeApplier instance;
    private final Context context;
    private Integer lastSystemSeed;

    private YiliThemeApplier(Context context) {
        this.context = context.getApplicationContext();
        lastSystemSeed = systemSeed();
    }

    public static void install(@NonNull Context context) {
        if (instance != null) {
            return;
        }
        instance = new YiliThemeApplier(context);
        YiliConfig.addListener(instance);
        instance.context.registerComponentCallbacks(instance);
    }

    @Override
    public void onYiliConfigChanged(@NonNull Set<YiliConfig.Key<?>> changed) {
        YiliAppearance.applyTokens();
        for (YiliConfig.Key<?> key : changed) {
            if (COLOR_KEYS.contains(key)) {
                reapply();
                return;
            }
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        if (!YiliConfig.get(YiliConfig.DYNAMIC_COLOR)) {
            return;
        }
        // The system palette changes together with a configuration change (assets/overlays).
        AndroidUtilities.runOnUIThread(() -> {
            Integer seed = systemSeed();
            if (seed != null && !seed.equals(lastSystemSeed)) {
                lastSystemSeed = seed;
                reapply();
            }
        }, 300);
    }

    @Override
    public void onLowMemory() {
    }

    private Integer systemSeed() {
        DynamicColorSource.SystemTheme t = DynamicColorSource.read(context);
        return t == null ? null : t.seedArgb;
    }

    /** Re-applies the active theme if it is one of the generated YiliGram themes. */
    public static void reapply() {
        Theme.ThemeInfo active = Theme.getActiveTheme();
        if (active == null || !ThemeBridge.isYiliTheme(active.name)) {
            return;
        }
        boolean night = Theme.isCurrentThemeNight();
        NotificationCenter.getGlobalInstance().postNotificationName(
                NotificationCenter.needSetDayNightTheme, active, night, null, -1);
    }
}
