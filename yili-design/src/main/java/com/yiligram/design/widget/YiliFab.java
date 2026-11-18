package com.yiligram.design.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;

import com.yiligram.design.YiliTheme;
import com.yiligram.design.color.YiliColorScheme;
import com.yiligram.design.elevation.YiliElevation;
import com.yiligram.design.motion.YiliMotion;
import com.yiligram.design.shape.SpringCornerDrawable;

/**
 * M3 floating action button: FAB 56dp (16dp corners), medium 80dp (20dp), large 96dp (28dp);
 * primaryContainer with an onPrimaryContainer icon, elevation level 3. Pressing squeezes the
 * corners and the button slightly on springs.
 */
public class YiliFab extends View implements YiliTheme.Listener {

    public enum Size {
        FAB(56, 16, 24), MEDIUM(80, 20, 28), LARGE(96, 28, 36);

        final int sizeDp;
        final int cornerDp;
        final int iconDp;

        Size(int sizeDp, int cornerDp, int iconDp) {
            this.sizeDp = sizeDp;
            this.cornerDp = cornerDp;
            this.iconDp = iconDp;
        }
    }

    private final SpringCornerDrawable background;
    private Size size = Size.FAB;
    @Nullable
    private Drawable icon;
    private final SpringAnimation scaleX = new SpringAnimation(this, DynamicAnimation.SCALE_X);
    private final SpringAnimation scaleY = new SpringAnimation(this, DynamicAnimation.SCALE_Y);

    public YiliFab(Context context) {
        super(context);
        background = new SpringCornerDrawable(0, 0f);
        setBackground(background);
        setClickable(true);
        setFocusable(true);
        setElevation(YiliElevation.Level.LEVEL3.dp * getResources().getDisplayMetrics().density);
        setSize(Size.FAB);
        applyColors(YiliTheme.colors());
    }

    public void setSize(@NonNull Size size) {
        this.size = size;
        background.setCorner(restCorner());
        requestLayout();
    }

    private float restCorner() {
        return size.cornerDp * 2f / size.sizeDp;
    }

    public void setIcon(@Nullable Drawable icon) {
        this.icon = icon == null ? null : icon.mutate();
        applyColors(YiliTheme.colors());
        invalidate();
    }

    private void applyColors(YiliColorScheme s) {
        background.setColor(s.primaryContainer());
        background.setStateLayerColor(s.onPrimaryContainer());
        if (icon != null) {
            icon.setTint(s.onPrimaryContainer());
        }
    }

    @Override
    public void onYiliThemeChanged(@NonNull YiliColorScheme scheme) {
        applyColors(scheme);
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        YiliTheme.addListener(this);
        applyColors(YiliTheme.colors());
    }

    @Override
    protected void onDetachedFromWindow() {
        YiliTheme.removeListener(this);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int px = Math.round(size.sizeDp * getResources().getDisplayMetrics().density);
        setMeasuredDimension(px, px);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (icon != null) {
            int px = Math.round(size.iconDp * getResources().getDisplayMetrics().density);
            int l = (getWidth() - px) / 2, t = (getHeight() - px) / 2;
            icon.setBounds(l, t, l + px, t + px);
            icon.draw(canvas);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                background.animateCorner(restCorner() * 1.6f);
                background.animateStateLayer(0.10f);
                YiliMotion.retarget(scaleX, YiliMotion.spatial(YiliMotion.Speed.FAST), 0.94f);
                YiliMotion.retarget(scaleY, YiliMotion.spatial(YiliMotion.Speed.FAST), 0.94f);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                background.animateCorner(restCorner());
                background.animateStateLayer(0f);
                YiliMotion.retarget(scaleX, YiliMotion.spatial(YiliMotion.Speed.FAST), 1f);
                YiliMotion.retarget(scaleY, YiliMotion.spatial(YiliMotion.Speed.FAST), 1f);
                break;
        }
        return super.onTouchEvent(event);
    }
}
