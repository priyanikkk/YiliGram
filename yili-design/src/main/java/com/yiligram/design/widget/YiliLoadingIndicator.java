package com.yiligram.design.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;

import com.yiligram.design.YiliTheme;
import com.yiligram.design.color.YiliColorScheme;
import com.yiligram.design.motion.SpringSpec;
import com.yiligram.design.motion.YiliMotion;
import com.yiligram.design.shape.ExpressiveShapes;
import com.yiligram.design.shape.ExpressiveShapes.Shape;
import com.yiligram.design.shape.ShapeMorphDrawable;

/**
 * M3 Expressive loading indicator: an indeterminate indicator that morphs through a sequence of
 * expressive shapes while rotating. 48dp container, 38dp indicator (MDC LoadingIndicator).
 *
 * <p>Morphs are driven by springs, so a step overshoots slightly and settles, as in the spec.
 */
public class YiliLoadingIndicator extends View implements YiliTheme.Listener {

    /** Shape sequence of the M3 Expressive loading indicator. */
    private static final Shape[] SEQUENCE = {
            Shape.SOFT_BURST, Shape.COOKIE_9, Shape.PENTAGON, Shape.PILL,
            Shape.SUNNY, Shape.COOKIE_4, Shape.OVAL
    };
    private static final long STEP_MS = 650;
    private static final float STEP_ROTATION = 90f;
    private static final float SPIN_DEGREES_PER_MS = 360f / 4666f;

    private final ShapeMorphDrawable shape;
    private final Paint containerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SpringAnimation morphSpring;
    private boolean contained;
    private boolean customColors;
    private int step;
    private float morph;     // 0..1 (may overshoot) progress of the current step
    private float stepBase;  // accumulated rotation of completed steps
    private long startTime;
    private boolean running;

    private static final FloatPropertyCompat<YiliLoadingIndicator> MORPH =
            new FloatPropertyCompat<YiliLoadingIndicator>("morph") {
                @Override
                public float getValue(YiliLoadingIndicator v) {
                    return v.morph;
                }

                @Override
                public void setValue(YiliLoadingIndicator v, float value) {
                    v.morph = value;
                    v.shape.setProgress(Math.max(0f, Math.min(1f, value)));
                    v.invalidate();
                }
            };

    private final Runnable nextStep = new Runnable() {
        @Override
        public void run() {
            if (!running) {
                return;
            }
            advance();
            postDelayed(this, STEP_MS);
        }
    };

    public YiliLoadingIndicator(Context context) {
        this(context, null);
    }

    public YiliLoadingIndicator(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        shape = new ShapeMorphDrawable(ExpressiveShapes.get(SEQUENCE[0]), 0);
        shape.setCallback(this);
        morphSpring = new SpringAnimation(this, MORPH);
        applyColors(YiliTheme.colors());
    }

    /** Contained variant: the indicator sits on a filled circular container. */
    public void setContained(boolean contained) {
        this.contained = contained;
        if (!customColors) {
            applyColors(YiliTheme.colors());
        }
        invalidate();
    }

    public void setColors(@ColorInt int indicator, @ColorInt int container) {
        customColors = true;
        shape.setColor(indicator);
        containerPaint.setColor(container);
        invalidate();
    }

    private void applyColors(YiliColorScheme s) {
        shape.setColor(contained ? s.onPrimaryContainer() : s.primary());
        containerPaint.setColor(s.primaryContainer());
    }

    @Override
    public void onYiliThemeChanged(@NonNull YiliColorScheme scheme) {
        if (!customColors) {
            applyColors(scheme);
            invalidate();
        }
    }

    private void advance() {
        Shape from = SEQUENCE[step % SEQUENCE.length];
        Shape to = SEQUENCE[(step + 1) % SEQUENCE.length];
        stepBase += STEP_ROTATION * (step == 0 && stepBase == 0 ? 0 : 1);
        step++;
        shape.setShapes(ExpressiveShapes.get(from), ExpressiveShapes.get(to));
        morph = 0f;
        shape.setProgress(0f);
        SpringSpec spec = YiliMotion.token(YiliMotion.Scheme.EXPRESSIVE, YiliMotion.Kind.SPATIAL, YiliMotion.Speed.FAST);
        if (YiliMotion.isEnabled()) {
            morphSpring.cancel();
            morphSpring.setStartValue(0f);
            morphSpring.setSpring(YiliMotion.force(spec, 1f));
            morphSpring.start();
        } else {
            MORPH.setValue(this, 1f);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        YiliTheme.addListener(this);
        start();
    }

    @Override
    protected void onDetachedFromWindow() {
        YiliTheme.removeListener(this);
        stop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (isAttachedToWindow()) {
            if (visibility == VISIBLE && isShown()) {
                start();
            } else {
                stop();
            }
        }
    }

    private void start() {
        if (running) {
            return;
        }
        running = true;
        startTime = SystemClock.uptimeMillis();
        postDelayed(nextStep, STEP_MS);
        invalidate();
    }

    private void stop() {
        running = false;
        removeCallbacks(nextStep);
        morphSpring.cancel();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = Math.round(48 * getResources().getDisplayMetrics().density);
        setMeasuredDimension(resolveSize(size, widthMeasureSpec), resolveSize(size, heightMeasureSpec));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float size = Math.min(w, h);
        if (contained) {
            canvas.drawCircle(w / 2f, h / 2f, size / 2f, containerPaint);
        }
        int indicator = Math.round(size * 38f / 48f);
        int left = Math.round((w - indicator) / 2f), top = Math.round((h - indicator) / 2f);
        shape.setBounds(left, top, left + indicator, top + indicator);
        float spin = running && YiliMotion.isEnabled()
                ? (SystemClock.uptimeMillis() - startTime) * SPIN_DEGREES_PER_MS : 0f;
        shape.setRotation(spin + stepBase + STEP_ROTATION * morph);
        shape.draw(canvas);
        if (running && YiliMotion.isEnabled()) {
            postInvalidateOnAnimation();
        }
    }

    @Override
    protected boolean verifyDrawable(@NonNull android.graphics.drawable.Drawable who) {
        return who == shape || super.verifyDrawable(who);
    }
}
