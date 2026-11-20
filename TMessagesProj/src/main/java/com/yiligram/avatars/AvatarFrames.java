package com.yiligram.avatars;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Path;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.yiligram.config.YiliConfig;
import com.yiligram.design.shape.ExpressiveShapes;

import org.telegram.messenger.R;

/**
 * Avatar frames: avatars can be cut to a Material 3 Expressive shape instead of a circle.
 * ImageReceiver clips round avatars to the chosen frame; AvatarDrawable fills the whole square
 * while a frame is being drawn, so the clip alone defines the outline.
 */
public final class AvatarFrames {

    public enum Frame {
        CIRCLE(null, R.string.AvatarFrameCircle),
        COOKIE_9(ExpressiveShapes.Shape.COOKIE_9, R.string.AvatarFrameCookie),
        COOKIE_12(ExpressiveShapes.Shape.COOKIE_12, R.string.AvatarFrameScallop),
        CLOVER_4(ExpressiveShapes.Shape.CLOVER_4, R.string.AvatarFrameClover),
        SUNNY(ExpressiveShapes.Shape.SUNNY, R.string.AvatarFrameSun),
        SOFT_BURST(ExpressiveShapes.Shape.SOFT_BURST, R.string.AvatarFrameBurst),
        FLOWER(ExpressiveShapes.Shape.FLOWER, R.string.AvatarFrameFlower),
        PENTAGON(ExpressiveShapes.Shape.PENTAGON, R.string.AvatarFramePentagon),
        GEM(ExpressiveShapes.Shape.GEM, R.string.AvatarFrameGem),
        SQUARE(ExpressiveShapes.Shape.SQUARE, R.string.AvatarFrameSquare);

        public final ExpressiveShapes.Shape shape;
        @StringRes
        public final int title;

        Frame(ExpressiveShapes.Shape shape, @StringRes int title) {
            this.shape = shape;
            this.title = title;
        }
    }

    /** True while an avatar is drawn inside a frame clip (UI thread only). */
    public static boolean drawingFramed;

    private static final Path path = new Path();
    private static final Matrix matrix = new Matrix();

    private AvatarFrames() {
    }

    @NonNull
    public static Frame current() {
        return YiliConfig.isInitialized() ? YiliConfig.get(YiliConfig.AVATAR_FRAME) : Frame.CIRCLE;
    }

    /** Outline of {@code frame} fitted to the given square-ish rect. */
    @NonNull
    public static Path outline(@NonNull Frame frame, float x, float y, float w, float h) {
        path.rewind();
        if (frame.shape == null) {
            path.addOval(x, y, x + w, y + h, Path.Direction.CW);
            return path;
        }
        matrix.reset();
        matrix.setScale(w, h);
        matrix.postTranslate(x, y);
        ExpressiveShapes.unitPath(frame.shape).transform(matrix, path);
        return path;
    }

    /**
     * Starts a framed draw if this avatar is round and a frame is chosen: clips the canvas and
     * returns true; the caller draws the avatar as a plain square and then calls {@link #end}.
     */
    public static boolean begin(Canvas canvas, float x, float y, float w, float h) {
        Frame f = current();
        if (f == Frame.CIRCLE || w <= 0 || h <= 0) {
            return false;
        }
        canvas.save();
        canvas.clipPath(outline(f, x, y, w, h));
        drawingFramed = true;
        return true;
    }

    public static void end(Canvas canvas) {
        drawingFramed = false;
        canvas.restore();
    }
}
