package com.yiligram.design.type;

import androidx.annotation.NonNull;

/**
 * M3 Expressive type scale, baseline and emphasized.
 *
 * <p>Values from material-components-android {@code typography/res/values/tokens.xml} and
 * {@code styles.xml} (token set 34): brand_regular = 400, brand_medium = 500, plain_medium = 500,
 * plain_regular + bold = 700.
 */
public final class YiliType {

    public enum Style {
        DISPLAY_LARGE, DISPLAY_MEDIUM, DISPLAY_SMALL,
        HEADLINE_LARGE, HEADLINE_MEDIUM, HEADLINE_SMALL,
        TITLE_LARGE, TITLE_MEDIUM, TITLE_SMALL,
        BODY_LARGE, BODY_MEDIUM, BODY_SMALL,
        LABEL_LARGE, LABEL_MEDIUM, LABEL_SMALL
    }

    private static final TypeStyle[] BASELINE = {
            new TypeStyle(57, 64, -0.00438596f, 400, true),
            new TypeStyle(45, 52, 0f, 400, true),
            new TypeStyle(36, 44, 0f, 400, true),
            new TypeStyle(32, 40, 0f, 400, true),
            new TypeStyle(28, 36, 0f, 400, true),
            new TypeStyle(24, 32, 0f, 400, true),
            new TypeStyle(22, 28, 0f, 400, true),
            new TypeStyle(16, 24, 0.009375f, 500, false),
            new TypeStyle(14, 20, 0.00714286f, 500, false),
            new TypeStyle(16, 24, 0.03125f, 400, false),
            new TypeStyle(14, 20, 0.01785714f, 400, false),
            new TypeStyle(12, 16, 0.03333333f, 400, false),
            new TypeStyle(14, 20, 0.00714286f, 500, false),
            new TypeStyle(12, 16, 0.04166667f, 500, false),
            new TypeStyle(11, 16, 0.04545455f, 500, false),
    };

    private static final TypeStyle[] EMPHASIZED = {
            new TypeStyle(57, 64, 0f, 500, true),
            new TypeStyle(45, 52, 0f, 500, true),
            new TypeStyle(36, 44, 0f, 500, true),
            new TypeStyle(32, 40, 0f, 500, true),
            new TypeStyle(28, 36, 0f, 500, true),
            new TypeStyle(24, 32, 0f, 500, true),
            new TypeStyle(22, 28, 0f, 500, true),
            new TypeStyle(16, 24, 0.009375f, 700, false),
            new TypeStyle(14, 20, 0.00714286f, 700, false),
            new TypeStyle(16, 24, 0.009375f, 500, false),
            new TypeStyle(14, 20, 0.01785714f, 500, false),
            new TypeStyle(12, 16, 0.03333333f, 500, false),
            new TypeStyle(14, 20, 0.00714286f, 700, false),
            new TypeStyle(12, 16, 0.04166667f, 700, false),
            new TypeStyle(11, 16, 0.04545455f, 700, false),
    };

    /** User font-size multiplier (on top of the system font scale) and weight offset. */
    private static volatile float sizeScale = 1f;
    private static volatile int weightDelta = 0;

    private YiliType() {
    }

    public static void configure(float sizeMultiplier, int weightOffset) {
        sizeScale = Math.max(0.7f, Math.min(1.6f, sizeMultiplier));
        weightDelta = Math.max(-300, Math.min(300, weightOffset));
    }

    /** Raw token, ignoring user preferences. */
    @NonNull
    public static TypeStyle token(@NonNull Style style, boolean emphasized) {
        return (emphasized ? EMPHASIZED : BASELINE)[style.ordinal()];
    }

    /** Style with the user's size and weight preferences applied. */
    @NonNull
    public static TypeStyle get(@NonNull Style style, boolean emphasized) {
        TypeStyle t = token(style, emphasized);
        if (sizeScale == 1f && weightDelta == 0) {
            return t;
        }
        int w = Math.max(100, Math.min(1000, t.weight + weightDelta));
        return new TypeStyle(t.sizeSp * sizeScale, t.lineHeightSp * sizeScale, t.letterSpacingEm, w, t.brand);
    }

    @NonNull
    public static TypeStyle get(@NonNull Style style) {
        return get(style, false);
    }
}
