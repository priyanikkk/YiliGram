package com.yiligram.config;

import android.app.Activity;
import android.app.Application;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.DisplayMetrics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Interface scale ("density"): multiplies the display density of the app's and every activity's
 * {@link Resources}, the way Android's "Display size" setting does, so dp, sp and drawables all
 * scale together. Always computed from the system metrics, so applying it twice is harmless.
 * Changes need a restart.
 */
public final class YiliDensity {

    public static final float MIN = 0.8f;
    public static final float MAX = 1.25f;

    private static float scale = 1f;

    private YiliDensity() {
    }

    /** Reads the setting and patches the application resources; call right after YiliConfig.init. */
    public static void install(@NonNull Application app) {
        float s = YiliConfig.get(YiliConfig.UI_SCALE);
        scale = Math.max(MIN, Math.min(MAX, s));
        if (scale == 1f) {
            return;
        }
        apply(app.getResources());
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityPreCreated(@NonNull Activity activity, @Nullable Bundle state) {
                apply(activity.getResources());
            }

            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle state) {
                apply(activity.getResources());
            }

            @Override
            public void onActivityStarted(@NonNull Activity activity) {
            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                apply(activity.getResources());
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {
            }
        });
    }

    public static float scale() {
        return scale;
    }

    /** Sets the scaled density on {@code res}; a no-op at scale 1. */
    public static void apply(@Nullable Resources res) {
        if (res == null || scale == 1f) {
            return;
        }
        DisplayMetrics system = Resources.getSystem().getDisplayMetrics();
        DisplayMetrics m = res.getDisplayMetrics();
        float fontScale = res.getConfiguration().fontScale;
        m.density = system.density * scale;
        m.densityDpi = Math.round(system.densityDpi * scale);
        m.scaledDensity = m.density * (fontScale > 0 ? fontScale : 1f);
        res.getConfiguration().densityDpi = m.densityDpi;
    }
}
