package com.yiligram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;

import androidx.annotation.NonNull;

import com.yiligram.design.YiliTheme;
import com.yiligram.design.color.ColorRole;
import com.yiligram.design.color.YiliColorScheme;
import com.yiligram.design.shape.YiliShapes;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

/**
 * Live preview at the top of the appearance screen: a mini chat (incoming / outgoing bubble),
 * a FAB and the key color roles, drawn with the current scheme and roundness.
 */
public class AppearancePreviewView extends View implements YiliTheme.Listener {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float dp;
    private final String inText;
    private final String outText;

    public AppearancePreviewView(Context context) {
        super(context);
        dp = context.getResources().getDisplayMetrics().density;
        textPaint.setTextSize(15 * context.getResources().getDisplayMetrics().scaledDensity);
        inText = LocaleController.getString(R.string.YiliPreviewIn);
        outText = LocaleController.getString(R.string.YiliPreviewOut);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), Math.round(232 * dp));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        YiliTheme.addListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        YiliTheme.removeListener(this);
        super.onDetachedFromWindow();
    }

    @Override
    public void onYiliThemeChanged(@NonNull YiliColorScheme scheme) {
        invalidate();
    }

    private float corner(YiliShapes.Corner c, float w, float h) {
        float r = YiliShapes.radiusDp(c);
        return Float.isInfinite(r) ? Math.min(w, h) / 2f : Math.min(r * dp, Math.min(w, h) / 2f);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        YiliColorScheme s = YiliTheme.colors();
        float w = getWidth(), h = getHeight();
        float pad = 16 * dp;

        // Card with the chat background.
        rect.set(pad, 8 * dp, w - pad, h - 8 * dp);
        paint.setColor(s.surface());
        float cardRadius = corner(YiliShapes.Corner.EXTRA_LARGE, rect.width(), rect.height());
        canvas.drawRoundRect(rect, cardRadius, cardRadius, paint);
        paint.setColor(s.outlineVariant());
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp);
        canvas.drawRoundRect(rect, cardRadius, cardRadius, paint);
        paint.setStyle(Paint.Style.FILL);

        final float cardLeft = rect.left, cardRight = rect.right, cardBottom = rect.bottom;
        float maxBubble = (w - 2 * pad) * 0.72f;
        float top = rect.top + 20 * dp;
        top = bubble(canvas, inText, false, pad + 14 * dp, top, maxBubble, s) + 10 * dp;
        bubble(canvas, outText, true, w - pad - 14 * dp, top, maxBubble, s);

        // FAB
        float fab = 48 * dp;
        float fx = cardRight - 14 * dp - fab, fy = cardBottom - 14 * dp - fab;
        rect.set(fx, fy, fx + fab, fy + fab);
        paint.setColor(s.primaryContainer());
        float fr = corner(YiliShapes.Corner.LARGE, fab, fab);
        canvas.drawRoundRect(rect, fr, fr, paint);
        paint.setColor(s.onPrimaryContainer());
        paint.setStrokeWidth(2.5f * dp);
        paint.setStrokeCap(Paint.Cap.ROUND);
        float cx = rect.centerX(), cy = rect.centerY(), l = 8 * dp;
        canvas.drawLine(cx - l, cy, cx + l, cy, paint);
        canvas.drawLine(cx, cy - l, cx, cy + l, paint);

        // Swatches of the main roles.
        ColorRole[] roles = {ColorRole.PRIMARY, ColorRole.SECONDARY, ColorRole.TERTIARY,
                ColorRole.PRIMARY_CONTAINER, ColorRole.SECONDARY_CONTAINER, ColorRole.TERTIARY_CONTAINER};
        float sw = 22 * dp, sx = cardLeft + 14 * dp, sy = cardBottom - 14 * dp - (fab + sw) / 2f;
        for (ColorRole role : roles) {
            paint.setColor(s.get(role));
            canvas.drawCircle(sx + sw / 2f, sy + sw / 2f, sw / 2f, paint);
            sx += sw + 8 * dp;
        }
    }

    /** Draws a bubble, returns its bottom. */
    private float bubble(Canvas canvas, String text, boolean out, float edge, float top, float maxWidth, YiliColorScheme s) {
        float hp = 14 * dp, vp = 10 * dp;
        textPaint.setColor(out ? s.onPrimaryContainer() : s.onSurface());
        int textWidth = (int) Math.min(maxWidth - 2 * hp, textPaint.measureText(text) + 1);
        StaticLayout layout = new StaticLayout(text, textPaint, textWidth, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
        float bw = layout.getWidth() + 2 * hp, bh = layout.getHeight() + 2 * vp;
        float left = out ? edge - bw : edge;
        rect.set(left, top, left + bw, top + bh);
        paint.setColor(out ? s.primaryContainer() : s.get(ColorRole.SURFACE_CONTAINER_HIGH));
        float r = corner(YiliShapes.Corner.EXTRA_LARGE, bw, bh);
        float small = corner(YiliShapes.Corner.EXTRA_SMALL, bw, bh);
        drawBubble(canvas, rect, r, small, out);
        canvas.save();
        canvas.translate(left + hp, top + vp);
        layout.draw(canvas);
        canvas.restore();
        return top + bh;
    }

    private final android.graphics.Path path = new android.graphics.Path();

    /** Rounded rect with a small corner on the tail side (bottom-left for in, bottom-right for out). */
    private void drawBubble(Canvas canvas, RectF r, float big, float small, boolean out) {
        float[] radii = out
                ? new float[]{big, big, big, big, small, small, big, big}
                : new float[]{big, big, big, big, big, big, small, small};
        path.rewind();
        path.addRoundRect(r, radii, android.graphics.Path.Direction.CW);
        canvas.drawPath(path, paint);
    }
}
