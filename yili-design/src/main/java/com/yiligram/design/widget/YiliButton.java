package com.yiligram.design.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.yiligram.design.YiliTheme;
import com.yiligram.design.color.ColorRole;
import com.yiligram.design.color.YiliColorScheme;
import com.yiligram.design.shape.SpringCornerDrawable;
import com.yiligram.design.type.TypeStyle;
import com.yiligram.design.type.YiliType;

/**
 * M3 Expressive common button: filled, tonal, outlined, elevated or text; round (pill) or square
 * shape. On press the corners morph (round → squarer, square → rounder) on a fast spatial
 * spring, and a 10% state layer fades in, as specified by M3 Expressive buttons.
 */
@SuppressLint("AppCompatCustomView")
public class YiliButton extends TextView implements YiliTheme.Listener {

    public enum Style { FILLED, TONAL, OUTLINED, ELEVATED, TEXT }

    /** M3 Expressive button sizes: container height in dp. */
    public enum Size {
        XSMALL(32, 12), SMALL(40, 16), MEDIUM(56, 24), LARGE(96, 48), XLARGE(136, 64);

        final int heightDp;
        final int paddingDp;

        Size(int heightDp, int paddingDp) {
            this.heightDp = heightDp;
            this.paddingDp = paddingDp;
        }
    }

    private final SpringCornerDrawable background;
    private Style style = Style.FILLED;
    private Size size = Size.SMALL;
    private boolean square;

    public YiliButton(Context context) {
        super(context);
        background = new SpringCornerDrawable(0, 1f);
        setBackground(background);
        setGravity(Gravity.CENTER);
        setSingleLine(true);
        setClickable(true);
        setFocusable(true);
        applySize();
        applyColors(YiliTheme.colors());
    }

    public YiliButton setStyle(@NonNull Style style) {
        this.style = style;
        applyColors(YiliTheme.colors());
        return this;
    }

    public YiliButton setSize(@NonNull Size size) {
        this.size = size;
        applySize();
        return this;
    }

    /** Square shape (smaller corners) instead of the default round/pill shape. */
    public YiliButton setSquare(boolean square) {
        this.square = square;
        background.setCorner(restCorner());
        return this;
    }

    private float restCorner() {
        return square ? 0.35f : 1f;
    }

    private float pressedCorner() {
        return square ? 0.55f : 0.45f;
    }

    private void applySize() {
        float d = getResources().getDisplayMetrics().density;
        int pad = Math.round(size.paddingDp * d);
        setPadding(pad, 0, pad, 0);
        setMinHeight(Math.round(size.heightDp * d));
        setMinimumHeight(Math.round(size.heightDp * d));
        TypeStyle t = YiliType.get(size.heightDp >= 96 ? YiliType.Style.HEADLINE_SMALL
                : size.heightDp >= 56 ? YiliType.Style.TITLE_MEDIUM : YiliType.Style.LABEL_LARGE);
        setTextSize(TypedValue.COMPLEX_UNIT_SP, t.sizeSp);
        setLetterSpacing(t.letterSpacingEm);
        setTypeface(Typeface.create(Typeface.DEFAULT, t.weight >= 600 ? Typeface.BOLD : Typeface.NORMAL));
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            setTypeface(Typeface.create(Typeface.DEFAULT, t.weight, false));
        }
        background.setCorner(restCorner());
    }

    private void applyColors(YiliColorScheme s) {
        int content;
        background.setStroke(0f, 0);
        switch (style) {
            case TONAL:
                background.setColor(s.secondaryContainer());
                content = s.onSecondaryContainer();
                break;
            case OUTLINED:
                background.setColor(0);
                background.setStroke(getResources().getDisplayMetrics().density, s.outlineVariant());
                content = s.onSurfaceVariant();
                break;
            case ELEVATED:
                background.setColor(s.get(ColorRole.SURFACE_CONTAINER_LOW));
                content = s.primary();
                setElevation(getResources().getDisplayMetrics().density);
                break;
            case TEXT:
                background.setColor(0);
                content = s.primary();
                break;
            case FILLED:
            default:
                background.setColor(s.primary());
                content = s.onPrimary();
                break;
        }
        setTextColor(content);
        background.setStateLayerColor(content);
    }

    @Override
    public void onYiliThemeChanged(@NonNull YiliColorScheme scheme) {
        applyColors(scheme);
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

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                background.animateCorner(pressedCorner());
                background.animateStateLayer(0.10f);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                background.animateCorner(restCorner());
                background.animateStateLayer(0f);
                break;
        }
        return super.onTouchEvent(event);
    }
}
