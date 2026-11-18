package com.yiligram.ui;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.yiligram.design.YiliTheme;
import com.yiligram.design.color.ColorRole;
import com.yiligram.design.color.YiliColorScheme;
import com.yiligram.design.shape.ExpressiveShapes;
import com.yiligram.design.shape.ShapeMorphDrawable;
import com.yiligram.design.type.TypeStyle;
import com.yiligram.design.type.YiliType;
import com.yiligram.design.widget.YiliButton;
import com.yiligram.design.widget.YiliFab;
import com.yiligram.design.widget.YiliLoadingIndicator;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

/**
 * Debug-only gallery of the YiliGram design system (registered in the debug manifests only).
 * Shows the current color roles, type scale, expressive shapes and components, so they can be
 * checked on a device without opening any chat.
 */
public class DesignGalleryActivity extends Activity {

    private float dp;
    private YiliColorScheme s;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Theme.createCommonResources(this);
        dp = getResources().getDisplayMetrics().density;
        s = YiliTheme.colors();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = px(16);
        root.setPadding(pad, px(48), pad, px(32));
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(s.surface());
        scroll.addView(root);
        setContentView(scroll);
        getWindow().setStatusBarColor(s.surface());
        getWindow().setNavigationBarColor(s.surface());
        if (!s.spec.dark) {
            scroll.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }

        root.addView(text("YiliGram design", YiliType.Style.HEADLINE_LARGE, true, s.onSurface()));
        root.addView(text(s.spec.toString(), YiliType.Style.BODY_SMALL, false, s.onSurfaceVariant()));

        section(root, "Buttons");
        LinearLayout buttons = row(root);
        buttons.addView(new YiliButton(this).setStyle(YiliButton.Style.FILLED), wrap());
        ((TextView) buttons.getChildAt(0)).setText("Filled");
        addButton(buttons, "Tonal", YiliButton.Style.TONAL, false);
        addButton(buttons, "Outlined", YiliButton.Style.OUTLINED, false);
        LinearLayout buttons2 = row(root);
        addButton(buttons2, "Square", YiliButton.Style.FILLED, true);
        addButton(buttons2, "Elevated", YiliButton.Style.ELEVATED, false);
        addButton(buttons2, "Text", YiliButton.Style.TEXT, false);
        LinearLayout buttons3 = row(root);
        YiliButton medium = new YiliButton(this).setStyle(YiliButton.Style.FILLED).setSize(YiliButton.Size.MEDIUM);
        medium.setText("Medium");
        buttons3.addView(medium, wrap());

        section(root, "FAB");
        LinearLayout fabs = row(root);
        for (YiliFab.Size size : YiliFab.Size.values()) {
            YiliFab fab = new YiliFab(this);
            fab.setSize(size);
            fab.setIcon(getResources().getDrawable(R.drawable.msg_edit, getTheme()));
            fabs.addView(fab, wrap());
        }

        section(root, "Loading indicator");
        LinearLayout loaders = row(root);
        loaders.addView(new YiliLoadingIndicator(this), wrap());
        YiliLoadingIndicator contained = new YiliLoadingIndicator(this);
        contained.setContained(true);
        loaders.addView(contained, wrap());

        section(root, "Expressive shapes");
        GridLayout shapes = new GridLayout(this);
        shapes.setColumnCount(5);
        for (ExpressiveShapes.Shape shape : ExpressiveShapes.Shape.values()) {
            View v = new View(this);
            v.setBackground(new ShapeMorphDrawable(ExpressiveShapes.get(shape), s.primaryContainer()));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = px(56);
            lp.height = px(56);
            lp.setMargins(px(4), px(4), px(4), px(4));
            shapes.addView(v, lp);
        }
        root.addView(shapes);

        section(root, "Type scale");
        for (YiliType.Style style : YiliType.Style.values()) {
            root.addView(text(style.name().toLowerCase().replace('_', ' '), style, false, s.onSurface()));
        }

        section(root, "Color roles");
        GridLayout colors = new GridLayout(this);
        colors.setColumnCount(2);
        for (ColorRole role : ColorRole.values()) {
            TextView swatch = new TextView(this);
            swatch.setText(role.key);
            swatch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            swatch.setPadding(px(8), px(8), px(8), px(8));
            int c = s.get(role);
            swatch.setTextColor(luminance(c) > 0.4 ? 0xFF000000 : 0xFFFFFFFF);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(c);
            bg.setCornerRadius(px(8));
            swatch.setBackground(bg);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = (getResources().getDisplayMetrics().widthPixels - px(48)) / 2;
            lp.setMargins(px(4), px(4), px(4), px(4));
            colors.addView(swatch, lp);
        }
        root.addView(colors);
    }

    private void addButton(LinearLayout row, String label, YiliButton.Style style, boolean square) {
        YiliButton b = new YiliButton(this).setStyle(style).setSquare(square);
        b.setText(label);
        row.addView(b, wrap());
    }

    private void section(LinearLayout root, String title) {
        TextView t = text(title, YiliType.Style.TITLE_MEDIUM, true, s.primary());
        t.setPadding(0, px(24), 0, px(8));
        root.addView(t);
    }

    private LinearLayout row(LinearLayout root) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, px(4), 0, px(4));
        root.addView(r);
        return r;
    }

    private LinearLayout.LayoutParams wrap() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, px(8), 0);
        return lp;
    }

    private TextView text(String value, YiliType.Style style, boolean emphasized, int color) {
        TypeStyle t = YiliType.get(style, emphasized);
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(color);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, t.sizeSp);
        v.setLetterSpacing(t.letterSpacingEm);
        if (Build.VERSION.SDK_INT >= 28) {
            v.setTypeface(Typeface.create(Typeface.DEFAULT, t.weight, false));
            v.setLineHeight(Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, t.lineHeightSp, getResources().getDisplayMetrics())));
        }
        return v;
    }

    private int px(float value) {
        return Math.round(value * dp);
    }

    private static double luminance(int c) {
        return (0.2126 * ((c >> 16) & 0xFF) + 0.7152 * ((c >> 8) & 0xFF) + 0.0722 * (c & 0xFF)) / 255.0;
    }
}
