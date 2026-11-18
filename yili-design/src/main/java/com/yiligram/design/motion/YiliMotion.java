package com.yiligram.design.motion;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/**
 * M3 Expressive motion tokens (spring physics).
 *
 * <p>Values from material-components-android {@code motion/res/values/tokens.xml}
 * (token set version 34): {@code m3_sys_motion_{standard,expressive}_spring_*}.
 *
 * <p><b>Spatial</b> springs move things (position, size, shape) and may overshoot;
 * <b>effects</b> springs change color/opacity and never overshoot.
 */
public final class YiliMotion {

    public enum Scheme { STANDARD, EXPRESSIVE }

    public enum Speed { FAST, DEFAULT, SLOW }

    public enum Kind { SPATIAL, EFFECTS }

    // [scheme][kind][speed] = {stiffness, damping}
    private static final float[][][][] TOKENS = {
            { // STANDARD
                    {{1400f, 0.9f}, {700f, 0.9f}, {300f, 0.9f}},   // spatial
                    {{3800f, 1f}, {1600f, 1f}, {800f, 1f}},        // effects
            },
            { // EXPRESSIVE
                    {{800f, 0.6f}, {380f, 0.8f}, {200f, 0.8f}},    // spatial
                    {{3800f, 1f}, {1600f, 1f}, {800f, 1f}},        // effects
            },
    };

    private static volatile Scheme scheme = Scheme.EXPRESSIVE;
    private static volatile float speedScale = 1f;
    private static volatile float bounceScale = 1f;
    private static volatile boolean enabled = true;

    private YiliMotion() {
    }

    /**
     * Global user preferences (Settings → YiliGram appearance → motion).
     *
     * @param speed  1 = spec; 2 = twice as fast; 0.5 = twice as slow
     * @param bounce 1 = spec overshoot; 0 = critically damped (no overshoot)
     */
    public static void configure(@NonNull Scheme motionScheme, float speed, float bounce, boolean animationsEnabled) {
        scheme = motionScheme;
        speedScale = Math.max(0.1f, speed);
        bounceScale = Math.max(0f, Math.min(1f, bounce));
        enabled = animationsEnabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /** Raw token value, ignoring user preferences. */
    @NonNull
    public static SpringSpec token(@NonNull Scheme s, @NonNull Kind kind, @NonNull Speed speed) {
        float[] v = TOKENS[s.ordinal()][kind.ordinal()][speed.ordinal()];
        return new SpringSpec(v[0], v[1]);
    }

    /** Token for the current scheme with user speed / bounce preferences applied. */
    @NonNull
    public static SpringSpec spring(@NonNull Kind kind, @NonNull Speed speed) {
        SpringSpec t = token(scheme, kind, speed);
        // Settling time of a spring scales with 1/sqrt(stiffness).
        float stiffness = t.stiffness * speedScale * speedScale;
        float damping = 1f - (1f - t.dampingRatio) * bounceScale;
        return new SpringSpec(stiffness, damping);
    }

    @NonNull
    public static SpringSpec spatial(@NonNull Speed speed) {
        return spring(Kind.SPATIAL, speed);
    }

    @NonNull
    public static SpringSpec effects(@NonNull Speed speed) {
        return spring(Kind.EFFECTS, speed);
    }

    @NonNull
    public static SpringForce force(@NonNull SpringSpec spec, float finalPosition) {
        return new SpringForce(finalPosition).setStiffness(spec.stiffness).setDampingRatio(spec.dampingRatio);
    }

    /**
     * Starts (or retargets) a spring. With animations disabled the value jumps to the target.
     */
    @NonNull
    public static <T> SpringAnimation animate(@NonNull T target, @NonNull FloatPropertyCompat<T> property,
                                              @NonNull SpringSpec spec, float to) {
        SpringAnimation anim = new SpringAnimation(target, property);
        anim.setSpring(force(spec, to));
        if (!enabled) {
            property.setValue(target, to);
            return anim;
        }
        anim.start();
        return anim;
    }

    /** Retargets a running animation in place, keeping its velocity (no visible jump). */
    public static void retarget(@NonNull SpringAnimation anim, @NonNull SpringSpec spec, float to) {
        SpringForce force = anim.getSpring();
        if (force == null) {
            anim.setSpring(force(spec, to));
        } else {
            force.setStiffness(spec.stiffness).setDampingRatio(spec.dampingRatio).setFinalPosition(to);
        }
        if (!enabled) {
            anim.skipToEnd();
        } else if (!anim.isRunning()) {
            anim.start();
        }
    }

    /** Minimum visible change for alpha/scale animations. */
    public static final float THRESHOLD_SCALE = DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE;
}
