package com.yiligram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import com.yiligram.design.YiliTheme;
import com.yiligram.design.color.ColorSchemeSpec;
import com.yiligram.design.color.ColorSpecVersion;
import com.yiligram.design.color.ContrastLevel;
import com.yiligram.design.color.SchemeVariant;
import com.yiligram.design.color.YiliColorScheme;

import org.telegram.messenger.Utilities;

/**
 * Row of preset seed colors. Each swatch shows the scheme the seed produces (primary, secondary
 * container, tertiary), like the color picker in Android's Wallpaper &amp; style.
 */
public class SeedColorPickerView extends View {

    public static final int[] PRESETS = {
            YiliTheme.DEFAULT_SEED, 0xFF6750A4, 0xFF1B6EF3, 0xFF006A6A, 0xFF386A20,
            0xFFE8A33D, 0xFFB3261E, 0xFFD0507A, 0xFF5B5B7E, 0xFF7D5260
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float dp;
    private final int[][] swatches = new int[PRESETS.length][];
    private Boolean swatchesDark;
    private int selected;
    private Utilities.Callback<Integer> onSelect;

    public SeedColorPickerView(Context context) {
        super(context);
        dp = context.getResources().getDisplayMetrics().density;
    }

    /** Swatch colors depend on light/dark mode; recomputed when the mode changes. */
    private void ensureSwatches() {
        boolean dark = YiliTheme.colors().spec.dark;
        if (swatchesDark != null && swatchesDark == dark) {
            return;
        }
        swatchesDark = dark;
        for (int i = 0; i < PRESETS.length; i++) {
            YiliColorScheme s = YiliColorScheme.generate(new ColorSchemeSpec(PRESETS[i], SchemeVariant.TONAL_SPOT,
                    dark, false, ContrastLevel.STANDARD, ColorSpecVersion.M3_EXPRESSIVE_2025));
            swatches[i] = new int[]{s.primaryContainer(), s.primary(), s.secondaryContainer(), s.get(com.yiligram.design.color.ColorRole.TERTIARY_CONTAINER)};
        }
    }

    public void setSelectedColor(int seed) {
        selected = -1;
        for (int i = 0; i < PRESETS.length; i++) {
            if ((PRESETS[i] | 0xFF000000) == (seed | 0xFF000000)) {
                selected = i;
            }
        }
        invalidate();
    }

    public void setOnSelect(Utilities.Callback<Integer> onSelect) {
        this.onSelect = onSelect;
    }

    private float cell() {
        return (getWidth() - 32 * dp) / 5f;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        float cell = (w - 32 * dp) / 5f;
        setMeasuredDimension(w, Math.round(cell * 2 + 16 * dp));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        ensureSwatches();
        float cell = cell();
        float r = Math.min(cell, 64 * dp) / 2f - 6 * dp;
        YiliColorScheme cur = YiliTheme.colors();
        for (int i = 0; i < PRESETS.length; i++) {
            float cx = 16 * dp + cell * (i % 5) + cell / 2f;
            float cy = 8 * dp + cell * (i / 5) + cell / 2f;
            int[] c = swatches[i];
            // Background disc, then three quadrants like Android's palette swatches.
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(c[0]);
            canvas.drawCircle(cx, cy, r, paint);
            float ir = r * 0.62f;
            android.graphics.RectF o = new android.graphics.RectF(cx - ir, cy - ir, cx + ir, cy + ir);
            paint.setColor(c[1]);
            canvas.drawArc(o, 180, 180, true, paint);
            paint.setColor(c[2]);
            canvas.drawArc(o, 90, 90, true, paint);
            paint.setColor(c[3]);
            canvas.drawArc(o, 0, 90, true, paint);
            if (i == selected) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(3 * dp);
                paint.setColor(cur.primary());
                canvas.drawCircle(cx, cy, r + 4 * dp, paint);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            return true;
        }
        if (e.getAction() == MotionEvent.ACTION_UP) {
            float cell = cell();
            int col = (int) ((e.getX() - 16 * dp) / cell);
            int row = (int) ((e.getY() - 8 * dp) / cell);
            int i = row * 5 + col;
            if (col >= 0 && col < 5 && row >= 0 && i < PRESETS.length) {
                selected = i;
                invalidate();
                performClick();
                if (onSelect != null) {
                    onSelect.run(PRESETS[i]);
                }
            }
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
