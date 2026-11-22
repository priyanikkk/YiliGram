package com.yiligram.wallpaper;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yiligram.config.YiliConfig;
import com.yiligram.design.YiliTheme;
import com.yiligram.design.color.ColorRole;
import com.yiligram.design.color.YiliColorScheme;
import com.yiligram.design.shape.ExpressiveShapes;

import org.telegram.messenger.AndroidUtilities;

import java.util.Random;

/**
 * Chat wallpapers drawn from Material 3 Expressive shapes in the current scheme: a soft surface gradient
 * with a scattered pattern of cookies, flowers and bursts. Follows the theme (day and night, seed color).
 */
public final class YiliWallpaper {

    public enum Style { OFF, SHAPES, BUBBLES, WAVES }

    private static Drawable cached;
    private static int cachedKey;

    private YiliWallpaper() {
    }

    public static boolean enabled() {
        return YiliConfig.get(YiliConfig.M3_WALLPAPER) != Style.OFF;
    }

    @Nullable
    public static synchronized Drawable get() {
        Style style = YiliConfig.get(YiliConfig.M3_WALLPAPER);
        if (style == Style.OFF) return null;
        YiliColorScheme c = YiliTheme.colors();
        int key = style.ordinal() * 31 + c.primary() * 17 + c.get(ColorRole.SURFACE);
        if (cached == null || cachedKey != key) {
            cached = new PatternDrawable(style, c);
            cachedKey = key;
        }
        return cached;
    }

    private static final class PatternDrawable extends Drawable {

        private static final ExpressiveShapes.Shape[] SHAPES = {
                ExpressiveShapes.Shape.COOKIE_9, ExpressiveShapes.Shape.FLOWER, ExpressiveShapes.Shape.SOFT_BURST,
                ExpressiveShapes.Shape.CLOVER_4, ExpressiveShapes.Shape.SUNNY, ExpressiveShapes.Shape.COOKIE_4,
                ExpressiveShapes.Shape.GEM, ExpressiveShapes.Shape.PENTAGON, ExpressiveShapes.Shape.PUFFY
        };

        private final Style style;
        private final int top, bottom, accent1, accent2, accent3;
        private Bitmap bitmap;
        private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private int alpha = 255;

        PatternDrawable(Style style, YiliColorScheme c) {
            this.style = style;
            top = c.get(ColorRole.SURFACE_CONTAINER_LOW);
            bottom = c.get(ColorRole.SURFACE_CONTAINER);
            accent1 = c.get(ColorRole.PRIMARY_CONTAINER);
            accent2 = c.get(ColorRole.SECONDARY_CONTAINER);
            accent3 = c.get(ColorRole.TERTIARY_CONTAINER);
        }

        private Bitmap render(int w, int h) {
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(b);
            Paint bg = new Paint();
            bg.setShader(new LinearGradient(0, 0, w * 0.3f, h, top, bottom, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, bg);

            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            Random random = new Random(0x5A11);
            int[] colors = {accent1, accent2, accent3};
            Matrix m = new Matrix();
            Path path = new Path();
            if (style == Style.WAVES) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(AndroidUtilities.dp(14));
                p.setStrokeCap(Paint.Cap.ROUND);
                float step = AndroidUtilities.dp(72);
                for (int i = 0; i * step < h + step; i++) {
                    path.rewind();
                    float y = i * step;
                    path.moveTo(-step, y);
                    for (float x = -step; x < w + step; x += step) {
                        path.quadTo(x + step / 4, y - step / 4, x + step / 2, y);
                        path.quadTo(x + step * 3 / 4, y + step / 4, x + step, y);
                    }
                    p.setColor(colors[i % 3]);
                    p.setAlpha(110);
                    canvas.drawPath(path, p);
                }
                return b;
            }
            float cell = AndroidUtilities.dp(style == Style.BUBBLES ? 96 : 64);
            for (float y = -cell / 2; y < h + cell; y += cell) {
                for (float x = -cell / 2; x < w + cell; x += cell) {
                    float size = cell * (style == Style.BUBBLES ? 0.35f + random.nextFloat() * 0.55f : 0.32f + random.nextFloat() * 0.28f);
                    float cx = x + random.nextFloat() * cell;
                    float cy = y + random.nextFloat() * cell;
                    ExpressiveShapes.Shape shape = style == Style.BUBBLES ? ExpressiveShapes.Shape.CIRCLE : SHAPES[random.nextInt(SHAPES.length)];
                    m.reset();
                    m.postTranslate(-0.5f, -0.5f);
                    m.postRotate(random.nextFloat() * 360f);
                    m.postScale(size, size);
                    m.postTranslate(cx, cy);
                    path.rewind();
                    path.addPath(ExpressiveShapes.unitPath(shape), m);
                    p.setColor(colors[random.nextInt(colors.length)]);
                    p.setAlpha(style == Style.BUBBLES ? 90 : 130);
                    canvas.drawPath(path, p);
                }
            }
            return b;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect r = getBounds();
            int w = Math.max(1, r.width()), h = Math.max(1, r.height());
            if (bitmap == null || bitmap.getWidth() != w || bitmap.getHeight() != h) {
                if (bitmap != null) bitmap.recycle();
                bitmap = render(w, h);
            }
            bitmapPaint.setAlpha(alpha);
            canvas.drawBitmap(bitmap, r.left, r.top, bitmapPaint);
        }

        @Override
        public void setAlpha(int alpha) {
            this.alpha = alpha;
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
    }
}
