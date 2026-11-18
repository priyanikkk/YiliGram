package com.yiligram.design.shape;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;

import com.yiligram.design.motion.YiliMotion;

/**
 * Rounded rectangle whose corner radius animates on a spring: the building block of M3
 * Expressive buttons and FABs, whose shape changes on press and selection.
 *
 * <p>The radius is a fraction of "full" (0 = square, 1 = half the shorter side) so the same value
 * works at any size; an optional outline is drawn inside the bounds.
 */
public class SpringCornerDrawable extends Drawable {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint overlay = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final SpringAnimation spring;
    private float fraction;
    private float overlayAlpha;
    private final SpringAnimation overlaySpring;

    private static final FloatPropertyCompat<SpringCornerDrawable> FRACTION =
            new FloatPropertyCompat<SpringCornerDrawable>("cornerFraction") {
                @Override
                public float getValue(SpringCornerDrawable d) {
                    return d.fraction;
                }

                @Override
                public void setValue(SpringCornerDrawable d, float value) {
                    d.fraction = value;
                    d.invalidateSelf();
                }
            };

    private static final FloatPropertyCompat<SpringCornerDrawable> OVERLAY =
            new FloatPropertyCompat<SpringCornerDrawable>("stateLayer") {
                @Override
                public float getValue(SpringCornerDrawable d) {
                    return d.overlayAlpha;
                }

                @Override
                public void setValue(SpringCornerDrawable d, float value) {
                    d.overlayAlpha = Math.max(0f, value);
                    d.invalidateSelf();
                }
            };

    public SpringCornerDrawable(@ColorInt int color, float cornerFraction) {
        fill.setColor(color);
        stroke.setStyle(Paint.Style.STROKE);
        fraction = cornerFraction;
        spring = new SpringAnimation(this, FRACTION);
        overlaySpring = new SpringAnimation(this, OVERLAY);
    }

    public void setColor(@ColorInt int color) {
        fill.setColor(color);
        invalidateSelf();
    }

    public void setStroke(float widthPx, @ColorInt int color) {
        stroke.setStrokeWidth(widthPx);
        stroke.setColor(color);
        invalidateSelf();
    }

    /** Color of the M3 state layer (usually the content color); its alpha is animated. */
    public void setStateLayerColor(@ColorInt int color) {
        overlay.setColor(color);
        invalidateSelf();
    }

    /** Animates the corner to {@code target} (fraction of full rounding). */
    public void animateCorner(float target) {
        YiliMotion.retarget(spring, YiliMotion.spatial(YiliMotion.Speed.FAST), target);
    }

    public void setCorner(float target) {
        spring.cancel();
        FRACTION.setValue(this, target);
    }

    /** Animates the state layer opacity (0, 0.08 hover, 0.10 pressed). */
    public void animateStateLayer(float alpha) {
        YiliMotion.retarget(overlaySpring, YiliMotion.effects(YiliMotion.Speed.FAST), alpha);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        rect.set(getBounds());
        float radius = Math.max(0f, fraction) * Math.min(rect.width(), rect.height()) / 2f;
        if (fill.getAlpha() > 0) {
            canvas.drawRoundRect(rect, radius, radius, fill);
        }
        if (overlayAlpha > 0f) {
            int base = overlay.getColor();
            overlay.setAlpha(Math.round(255 * Math.min(1f, overlayAlpha)));
            canvas.drawRoundRect(rect, radius, radius, overlay);
            overlay.setColor(base);
        }
        if (stroke.getStrokeWidth() > 0f) {
            float half = stroke.getStrokeWidth() / 2f;
            rect.inset(half, half);
            canvas.drawRoundRect(rect, Math.max(0f, radius - half), Math.max(0f, radius - half), stroke);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        fill.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        fill.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
