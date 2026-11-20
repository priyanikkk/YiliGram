package com.yiligram.design.shape;

import android.graphics.Matrix;
import android.graphics.Path;

import androidx.annotation.NonNull;
import androidx.graphics.shapes.CornerRounding;
import androidx.graphics.shapes.RoundedPolygon;
import androidx.graphics.shapes.RoundedPolygonKt;
import androidx.graphics.shapes.ShapesKt;
import androidx.graphics.shapes.Shapes_androidKt;

import java.util.EnumMap;

/**
 * M3 Expressive shape library, built with androidx.graphics.shapes.
 *
 * <p>Every shape is normalized to the unit square (0..1) so it can be scaled to any bounds and
 * morphed into any other shape with {@link androidx.graphics.shapes.Morph}. The set follows the
 * M3 Expressive shape catalog; geometry is our own construction from polygons and stars.
 */
public final class ExpressiveShapes {

    public enum Shape {
        CIRCLE, SQUARE, PILL, OVAL, TRIANGLE, PENTAGON, GEM, SUNNY, VERY_SUNNY,
        COOKIE_4, COOKIE_6, COOKIE_7, COOKIE_9, COOKIE_12,
        CLOVER_4, CLOVER_8, SOFT_BURST, BURST, FLOWER, PUFFY
    }

    private static final EnumMap<Shape, RoundedPolygon> cache = new EnumMap<>(Shape.class);

    private ExpressiveShapes() {
    }

    @NonNull
    public static synchronized RoundedPolygon get(@NonNull Shape shape) {
        RoundedPolygon p = cache.get(shape);
        if (p == null) {
            p = create(shape).normalized();
            cache.put(shape, p);
        }
        return p;
    }

    private static CornerRounding r(float radius) {
        return new CornerRounding(radius, 0f);
    }

    private static CornerRounding r(float radius, float smoothing) {
        return new CornerRounding(radius, smoothing);
    }

    private static RoundedPolygon polygon(int vertices, CornerRounding rounding) {
        return RoundedPolygonKt.RoundedPolygon(vertices, 1f, 0f, 0f, rounding);
    }

    private static RoundedPolygon star(int vertices, float inner, CornerRounding rounding, CornerRounding innerRounding) {
        return ShapesKt.star(RoundedPolygon.Companion, vertices, 1f, inner, rounding, innerRounding, null, 0f, 0f);
    }

    private static RoundedPolygon create(Shape shape) {
        switch (shape) {
            case CIRCLE:
                return ShapesKt.circle(RoundedPolygon.Companion, 10, 1f, 0f, 0f);
            case SQUARE:
                return ShapesKt.rectangle(RoundedPolygon.Companion, 2f, 2f, r(0.6f), null, 0f, 0f);
            case PILL:
                return ShapesKt.pill(RoundedPolygon.Companion, 2f, 1.2f, 0f, 0f, 0f);
            case OVAL: {
                Matrix m = new Matrix();
                m.setScale(1f, 0.64f);
                m.postRotate(-45f);
                return Shapes_androidKt.transformed(ShapesKt.circle(RoundedPolygon.Companion, 10, 1f, 0f, 0f), m);
            }
            case TRIANGLE:
                return polygon(3, r(0.35f));
            case PENTAGON:
                return polygon(5, r(0.3f));
            case GEM:
                return polygon(6, r(0.25f, 0.4f));
            case SUNNY:
                return star(8, 0.8f, r(0.15f), r(0.15f));
            case VERY_SUNNY:
                return star(8, 0.65f, r(0.12f), r(0.12f));
            case COOKIE_4:
                return star(4, 0.75f, r(0.45f), r(0.35f));
            case COOKIE_6:
                return star(6, 0.8f, r(0.35f), r(0.3f));
            case COOKIE_7:
                return star(7, 0.8f, r(0.3f), r(0.25f));
            case COOKIE_9:
                return star(9, 0.82f, r(0.25f), r(0.2f));
            case COOKIE_12:
                return star(12, 0.85f, r(0.18f), r(0.15f));
            case CLOVER_4:
                return star(4, 0.45f, r(0.6f), r(0.1f));
            case CLOVER_8:
                return star(8, 0.6f, r(0.35f), r(0.08f));
            case SOFT_BURST:
                return star(10, 0.75f, r(0.12f), r(0.12f));
            case BURST:
                return star(12, 0.7f, r(0.05f), r(0.05f));
            case FLOWER:
                return star(8, 0.55f, r(0.4f), r(0.05f));
            case PUFFY:
            default:
                return star(12, 0.85f, r(0.3f), r(0.05f));
        }
    }

    private static final EnumMap<Shape, Path> unitPaths = new EnumMap<>(Shape.class);

    /** Outline of a shape in the unit square (0..1, centered on 0.5). Do not modify the returned path. */
    @NonNull
    public static synchronized Path unitPath(@NonNull Shape shape) {
        Path p = unitPaths.get(shape);
        if (p == null) {
            p = new Path();
            Shapes_androidKt.toPath(get(shape), p);
            unitPaths.put(shape, p);
        }
        return p;
    }
}
