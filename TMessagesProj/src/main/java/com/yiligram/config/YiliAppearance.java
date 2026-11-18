package com.yiligram.config;

import android.content.Context;

import androidx.annotation.NonNull;

import com.yiligram.design.color.ColorSchemeSpec;
import com.yiligram.design.color.DynamicColorSource;
import com.yiligram.design.color.SchemeVariant;
import com.yiligram.design.motion.YiliMotion;
import com.yiligram.design.shape.YiliShapes;
import com.yiligram.design.type.YiliType;

/** Turns {@link YiliConfig} values into design-system state. */
public final class YiliAppearance {

    private YiliAppearance() {
    }

    /** Pushes motion, shape and type preferences into the design-system singletons. */
    public static void applyTokens() {
        YiliMotion.configure(YiliConfig.get(YiliConfig.MOTION_SCHEME), YiliConfig.get(YiliConfig.MOTION_SPEED),
                YiliConfig.get(YiliConfig.MOTION_BOUNCE), YiliConfig.get(YiliConfig.ANIMATIONS));
        YiliShapes.setRoundness(YiliConfig.get(YiliConfig.SHAPE_ROUNDNESS));
        YiliType.configure(YiliConfig.get(YiliConfig.FONT_SCALE), YiliConfig.get(YiliConfig.FONT_WEIGHT_DELTA));
    }

    /** The color scheme the user configured, for light or dark mode. */
    @NonNull
    public static ColorSchemeSpec colorSpec(@NonNull Context context, boolean dark) {
        int seed = YiliConfig.get(YiliConfig.SEED_COLOR);
        SchemeVariant variant = YiliConfig.get(YiliConfig.SCHEME_VARIANT);
        if (YiliConfig.get(YiliConfig.DYNAMIC_COLOR)) {
            DynamicColorSource.SystemTheme system = DynamicColorSource.read(context);
            if (system != null) {
                seed = system.seedArgb;
                if (YiliConfig.get(YiliConfig.FOLLOW_SYSTEM_STYLE)) {
                    variant = system.variant;
                }
            }
        }
        return new ColorSchemeSpec(seed, variant, dark, YiliConfig.get(YiliConfig.AMOLED),
                YiliConfig.get(YiliConfig.CONTRAST), YiliConfig.get(YiliConfig.COLOR_SPEC));
    }
}
