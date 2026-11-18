package com.yiligram.design.motion;

import androidx.annotation.NonNull;

/** Spring parameters as used by {@link androidx.dynamicanimation.animation.SpringForce}. */
public final class SpringSpec {
    public final float stiffness;
    public final float dampingRatio;

    public SpringSpec(float stiffness, float dampingRatio) {
        this.stiffness = stiffness;
        this.dampingRatio = dampingRatio;
    }

    @NonNull
    @Override
    public String toString() {
        return "SpringSpec(stiffness=" + stiffness + ", damping=" + dampingRatio + ")";
    }
}
