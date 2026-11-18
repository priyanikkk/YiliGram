package com.yiligram.design.shape;

/**
 * M3 Expressive corner radius scale (dp).
 *
 * <p>Values from material-components-android {@code m3_sys_shape_corner_value_*} tokens.
 * {@link Corner#FULL} means "half of the shorter side" (pill / circle).
 */
public final class YiliShapes {

    public enum Corner {
        NONE(0f),
        EXTRA_SMALL(4f),
        SMALL(8f),
        MEDIUM(12f),
        LARGE(16f),
        LARGE_INCREASED(20f),
        EXTRA_LARGE(28f),
        EXTRA_LARGE_INCREASED(32f),
        EXTRA_EXTRA_LARGE(48f),
        FULL(Float.POSITIVE_INFINITY);

        /** Radius in dp; {@code Float.POSITIVE_INFINITY} for {@link #FULL}. */
        public final float dp;

        Corner(float dp) {
            this.dp = dp;
        }

        /** Radius in px for a shape of the given size, honoring {@link #FULL}. */
        public float resolvePx(float density, float width, float height) {
            float half = Math.min(width, height) / 2f;
            return this == FULL ? half : Math.min(dp * density, half);
        }
    }

    /** User-adjustable roundness: 0 = square, 1 = spec, 2 = twice as round (capped at FULL). */
    private static volatile float roundness = 1f;

    private YiliShapes() {
    }

    public static void setRoundness(float value) {
        roundness = Math.max(0f, Math.min(2f, value));
    }

    public static float getRoundness() {
        return roundness;
    }

    /** Corner radius in dp after the user's roundness preference. */
    public static float radiusDp(Corner corner) {
        return corner == Corner.FULL ? Float.POSITIVE_INFINITY : corner.dp * roundness;
    }
}
