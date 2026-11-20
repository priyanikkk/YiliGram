package com.yiligram.ui;

import android.graphics.Canvas;
import android.os.SystemClock;

import com.yiligram.bridge.ThemeBridge;
import com.yiligram.config.YiliConfig;
import com.yiligram.design.shape.ExpressiveShapes;
import com.yiligram.design.shape.ExpressiveShapes.Shape;
import com.yiligram.design.shape.ShapeMorphDrawable;

import org.telegram.ui.ActionBar.Theme;

/**
 * The Material 3 Expressive loading indicator drawn in place of Telegram's spinning arc
 * (RadialProgressView): a filled shape that morphs through soft burst, cookie, pentagon, pill,
 * sunny, cookie and oval every 650 ms with a back-out ease, while the whole thing slowly spins.
 * Time-based, so it needs nothing but an invalidate per frame from its host.
 */
public final class YiliLoadingShape {

    private static final Shape[] SEQUENCE = {
            Shape.SOFT_BURST, Shape.COOKIE_9, Shape.PENTAGON, Shape.PILL,
            Shape.SUNNY, Shape.COOKIE_4, Shape.OVAL
    };
    private static final long STEP_MS = 650;

    private final ShapeMorphDrawable shape = new ShapeMorphDrawable(ExpressiveShapes.get(SEQUENCE[0]), 0);
    private final long start = SystemClock.uptimeMillis();
    private long shownStep = -1;

    /** Whether spinners should be drawn as the M3 loading indicator. */
    public static boolean enabled() {
        return YiliConfig.get(YiliConfig.M3_CONTROLS) && Theme.getActiveTheme() != null
                && ThemeBridge.isYiliTheme(Theme.getActiveTheme().getKey());
    }

    /** Draws the indicator centered at (cx, cy) with the given diameter and color. */
    public void draw(Canvas canvas, float cx, float cy, float size, int color) {
        long elapsed = SystemClock.uptimeMillis() - start;
        long step = elapsed / STEP_MS;
        float t = (elapsed % STEP_MS) / (float) STEP_MS;
        if (step != shownStep) {
            shownStep = step;
            shape.setShapes(ExpressiveShapes.get(SEQUENCE[(int) (step % SEQUENCE.length)]),
                    ExpressiveShapes.get(SEQUENCE[(int) ((step + 1) % SEQUENCE.length)]));
        }
        float morph = backOut(Math.min(1f, t * 1.7f));
        shape.setProgress(Math.max(0f, Math.min(1f, morph)));
        shape.setRotation((step * 90f + 90f * morph + elapsed * 360f / 4666f) % 360f);
        shape.setColor(color);
        float half = size / 2f;
        shape.setBounds((int) (cx - half), (int) (cy - half), (int) (cx + half), (int) (cy + half));
        shape.draw(canvas);
    }

    private static float backOut(float t) {
        float s = 1.5f, u = t - 1f;
        return u * u * ((s + 1f) * u + s) + 1f;
    }
}
