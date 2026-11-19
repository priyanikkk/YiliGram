package com.yiligram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import com.yiligram.config.YiliPresets;
import com.yiligram.design.YiliTheme;
import com.yiligram.design.color.ColorRole;
import com.yiligram.design.color.ColorSchemeSpec;
import com.yiligram.design.color.ColorSpecVersion;
import com.yiligram.design.color.ContrastLevel;
import com.yiligram.design.color.YiliColorScheme;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Horizontal row of built-in appearance presets. Each card is drawn in the preset's own scheme
 * and corner roundness, so the row previews what applying it will look like.
 */
public class PresetPickerView extends HorizontalScrollView {

    private final LinearLayout row;
    private Utilities.Callback<YiliPresets.Preset> onSelect;

    public PresetPickerView(Context context) {
        super(context);
        setHorizontalScrollBarEnabled(false);
        setClipToPadding(false);
        setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(8), AndroidUtilities.dp(12), AndroidUtilities.dp(12));
        row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        addView(row);
        for (YiliPresets.Preset p : YiliPresets.ALL) {
            Card card = new Card(context, p);
            card.setOnClickListener(v -> {
                if (onSelect != null) {
                    onSelect.run(p);
                }
            });
            row.addView(card, LayoutHelper.createLinear(92, 116, 4, 0, 4, 0));
        }
    }

    public void setOnSelect(Utilities.Callback<YiliPresets.Preset> onSelect) {
        this.onSelect = onSelect;
    }

    /** Re-reads the current preset and light/dark mode. */
    public void refresh() {
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        YiliPresets.Preset current = YiliPresets.current();
        for (int i = 0; i < row.getChildCount(); i++) {
            Card c = (Card) row.getChildAt(i);
            c.selected = c.preset == current;
            c.invalidate();
        }
    }

    private static final class Card extends View {
        final YiliPresets.Preset preset;
        boolean selected;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private YiliColorScheme scheme;
        private Boolean schemeDark;

        Card(Context context, YiliPresets.Preset preset) {
            super(context);
            this.preset = preset;
            text.setTextSize(AndroidUtilities.dp(13));
            text.setTypeface(AndroidUtilities.bold());
            setContentDescription(LocaleController.getString(preset.name));
        }

        private YiliColorScheme scheme() {
            boolean dark = YiliTheme.colors().spec.dark;
            if (scheme == null || schemeDark != dark) {
                schemeDark = dark;
                Object contrast = preset.values.get(com.yiligram.config.YiliConfig.CONTRAST);
                int seed = preset.seed();
                com.yiligram.design.color.SchemeVariant variant = preset.variant();
                // Presets that keep dynamic color show the wallpaper colors they will actually use.
                if (!preset.values.containsKey(com.yiligram.config.YiliConfig.DYNAMIC_COLOR)) {
                    com.yiligram.design.color.DynamicColorSource.SystemTheme sys =
                            com.yiligram.design.color.DynamicColorSource.read(getContext());
                    if (sys != null) {
                        seed = sys.seedArgb;
                        variant = sys.variant;
                    }
                }
                scheme = YiliColorScheme.generate(new ColorSchemeSpec(seed, variant, dark,
                        dark && Boolean.TRUE.equals(preset.values.get(com.yiligram.config.YiliConfig.AMOLED)),
                        contrast != null ? (ContrastLevel) contrast : ContrastLevel.STANDARD,
                        ColorSpecVersion.M3_EXPRESSIVE_2025));
            }
            return scheme;
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            YiliColorScheme s = scheme();
            float dp = AndroidUtilities.density;
            float radius = Math.min(28 * dp, 16 * dp * preset.roundness());
            float inset = 3 * dp;
            rect.set(inset, inset, getWidth() - inset, getHeight() - inset);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(s.get(ColorRole.SURFACE_CONTAINER_HIGH));
            canvas.drawRoundRect(rect, radius, radius, paint);

            // Swatch: primary container disc with primary / secondary / tertiary quadrants.
            float cx = getWidth() / 2f;
            float cy = inset + 40 * dp;
            float r = 26 * dp;
            paint.setColor(s.primaryContainer());
            canvas.drawCircle(cx, cy, r, paint);
            float ir = r * 0.62f;
            RectF o = new RectF(cx - ir, cy - ir, cx + ir, cy + ir);
            paint.setColor(s.primary());
            canvas.drawArc(o, 180, 180, true, paint);
            paint.setColor(s.secondaryContainer());
            canvas.drawArc(o, 90, 90, true, paint);
            paint.setColor(s.get(ColorRole.TERTIARY_CONTAINER));
            canvas.drawArc(o, 0, 90, true, paint);

            text.setColor(s.get(ColorRole.ON_SURFACE));
            CharSequence label = TextUtils.ellipsize(LocaleController.getString(preset.name), text,
                    getWidth() - 12 * dp, TextUtils.TruncateAt.END);
            float tw = text.measureText(label, 0, label.length());
            canvas.drawText(label, 0, label.length(), (getWidth() - tw) / 2f, getHeight() - inset - 14 * dp, text);

            if (selected) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.5f * dp);
                paint.setColor(YiliTheme.colors().primary());
                rect.inset(1.25f * dp, 1.25f * dp);
                canvas.drawRoundRect(rect, radius, radius, paint);
            }
        }
    }
}
