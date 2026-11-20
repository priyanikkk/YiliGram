package com.yiligram.design.shape;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.graphics.shapes.Morph;
import androidx.graphics.shapes.RoundedPolygon;
import androidx.graphics.shapes.Shapes_androidKt;

/**
 * Draws a shape morphing between two {@link RoundedPolygon}s (unit-square normalized), filled
 * with a color, scaled into the drawable bounds and optionally rotated around the center.
 */
public class ShapeMorphDrawable extends Drawable {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** The fill paint (e.g. to set a gradient shader). */
    @NonNull
    public Paint getPaint() {
        return paint;
    }
    private final Path unitPath = new Path();
    private final Path path = new Path();
    private final Matrix matrix = new Matrix();
    private Morph morph;
    private RoundedPolygon from;
    private float progress;
    private float rotation;
    private float scale = 1f;

    public ShapeMorphDrawable(@NonNull RoundedPolygon shape, @ColorInt int color) {
        setShapes(shape, shape);
        paint.setColor(color);
    }

    /** Morph source and target; progress 0 shows {@code from}, 1 shows {@code to}. */
    public void setShapes(@NonNull RoundedPolygon from, @NonNull RoundedPolygon to) {
        this.from = from;
        this.morph = from == to ? null : new Morph(from, to);
        invalidateSelf();
    }

    public void setProgress(float progress) {
        this.progress = progress;
        invalidateSelf();
    }

    public float getProgress() {
        return progress;
    }

    public void setRotation(float degrees) {
        rotation = degrees;
        invalidateSelf();
    }

    /** Scale of the shape inside the bounds (1 = fills the bounds). */
    public void setShapeScale(float scale) {
        this.scale = scale;
        invalidateSelf();
    }

    public void setColor(@ColorInt int color) {
        paint.setColor(color);
        invalidateSelf();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();
        if (b.isEmpty()) {
            return;
        }
        unitPath.rewind();
        if (morph != null) {
            Shapes_androidKt.toPath(morph, progress, unitPath);
        } else {
            Shapes_androidKt.toPath(from, unitPath);
        }
        float size = Math.min(b.width(), b.height()) * scale;
        matrix.reset();
        matrix.setTranslate(-0.5f, -0.5f);
        matrix.postScale(size, size);
        matrix.postRotate(rotation);
        matrix.postTranslate(b.exactCenterX(), b.exactCenterY());
        unitPath.transform(matrix, path);
        canvas.drawPath(path, paint);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
