package com.yiligram.badges;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yiligram.design.YiliTheme;
import com.yiligram.design.color.ColorRole;
import com.yiligram.design.color.YiliColorScheme;
import com.yiligram.design.shape.ExpressiveShapes;
import com.yiligram.design.shape.ShapeMorphDrawable;

import org.telegram.messenger.AndroidUtilities;

/**
 * The two YiliGram badges, drawn from the Material 3 Expressive shape set in the current scheme:
 * <ul>
 * <li>{@link YiliBadges.Type#ADMIN} - team: a 12-point soft burst with a primary -> tertiary
 * gradient and a four-point sparkle in the middle;</li>
 * <li>{@link YiliBadges.Type#BETA} - beta tester: a 4-lobe cookie in tertiary container with a bold
 * "β".</li>
 * </ul>
 */
public class YiliBadgeDrawable extends Drawable {

    private final YiliBadges.Type type;
    private final int size;
    private final ShapeMorphDrawable shape;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glyph = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path sparkle = new Path();

    public YiliBadgeDrawable(YiliBadges.Type type, int sizeDp) {
        this.type = type;
        this.size = AndroidUtilities.dp(sizeDp);
        ExpressiveShapes.Shape s = type == YiliBadges.Type.ADMIN ? ExpressiveShapes.Shape.SOFT_BURST : ExpressiveShapes.Shape.COOKIE_4;
        shape = new ShapeMorphDrawable(ExpressiveShapes.get(s), 0);
        glyph.setTypeface(Typeface.create(AndroidUtilities.bold(), Typeface.BOLD));
        glyph.setTextAlign(Paint.Align.CENTER);
    }

    public YiliBadges.Type getType() {
        return type;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();
        float cx = b.exactCenterX(), cy = b.exactCenterY();
        float r = Math.min(b.width(), b.height()) / 2f;
        YiliColorScheme c = YiliTheme.colors();
        if (type == YiliBadges.Type.ADMIN) {
            // Burst with a diagonal primary -> tertiary gradient.
            shape.setColor(c.primary());
            shape.setBounds(b);
            shape.getPaint().setShader(new LinearGradient(b.left, b.top, b.right, b.bottom,
                    c.primary(), c.get(ColorRole.TERTIARY), Shader.TileMode.CLAMP));
            shape.draw(canvas);
            shape.getPaint().setShader(null);
            // Four-point sparkle.
            float o = r * 0.52f, i = r * 0.13f;
            sparkle.rewind();
            sparkle.moveTo(cx, cy - o);
            sparkle.quadTo(cx + i, cy - i, cx + o, cy);
            sparkle.quadTo(cx + i, cy + i, cx, cy + o);
            sparkle.quadTo(cx - i, cy + i, cx - o, cy);
            sparkle.quadTo(cx - i, cy - i, cx, cy - o);
            sparkle.close();
            paint.setColor(c.get(ColorRole.ON_PRIMARY));
            canvas.drawPath(sparkle, paint);
        } else {
            shape.setColor(c.get(ColorRole.TERTIARY_CONTAINER));
            shape.setBounds(b);
            shape.draw(canvas);
            glyph.setColor(c.get(ColorRole.ON_TERTIARY_CONTAINER));
            glyph.setTextSize(r * 1.25f);
            Paint.FontMetrics fm = glyph.getFontMetrics();
            canvas.drawText("β", cx, cy - (fm.ascent + fm.descent) / 2f, glyph);
        }
    }

    @Override
    public int getIntrinsicWidth() {
        return size;
    }

    @Override
    public int getIntrinsicHeight() {
        return size;
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        glyph.setAlpha(alpha);
        shape.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        // Badges keep their own colors (Telegram tints name drawables in some places).
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
