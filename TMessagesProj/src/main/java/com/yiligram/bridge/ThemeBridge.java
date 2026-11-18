package com.yiligram.bridge;

import android.util.SparseArray;
import android.util.SparseIntArray;

import androidx.annotation.NonNull;

import com.yiligram.config.YiliAppearance;
import com.yiligram.design.YiliTheme;
import com.yiligram.design.color.ColorRole;
import com.yiligram.design.color.ColorSchemeSpec;
import com.yiligram.design.color.YiliColorScheme;
import com.yiligram.design.mcu.blend.Blend;
import com.yiligram.design.mcu.hct.Hct;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeColors;

import java.util.HashMap;
import java.util.Map;

/**
 * Bridge between Material 3 color roles and Telegram's {@link Theme} color keys.
 *
 * <p>YiliGram registers two built-in themes ("YiliGram M3" light and dark) whose asset names
 * are virtual: {@link Theme#getThemeFileValues} asks this class instead of reading a file.
 * Every one of the ~870 keys gets a value in two steps:
 * <ol>
 *   <li><b>Tonal transfer.</b> The key's color in Telegram's own reference theme (Blue for
 *       light, Night for dark) is converted to HCT. Neutral colors are re-taken from the M3
 *       neutral palettes, Telegram-blue accents (and every chromatic color of outgoing bubbles)
 *       from the primary palette, both at the same tone; other hues (online green, red,
 *       avatar colors) are harmonized towards primary. Alpha is preserved. This keeps the
 *       original contrast relationships of the thousands of places that draw with these keys.</li>
 *   <li><b>Role overrides.</b> The keys that define the look (surfaces, text, top app bar,
 *       bubbles, inputs, controls, FAB, menus, snackbars) are pinned to exact M3 roles.</li>
 * </ol>
 * Custom {@code .attheme} themes are untouched: they never go through this class.
 */
public final class ThemeBridge {

    public static final String THEME_LIGHT = "YiliGram M3";
    public static final String THEME_DARK = "YiliGram M3 Dark";
    public static final String ASSET_LIGHT = "yili_m3_light.attheme";
    public static final String ASSET_DARK = "yili_m3_dark.attheme";

    private static final String REFERENCE_DARK_ASSET = "night.attheme";

    /** Hue (HCT) of Telegram's brand blue; chromatic colors near it are treated as accents. */
    private static final double TELEGRAM_HUE = Hct.fromInt(ThemeColors.TELEGRAM_COLOR).getHue();
    private static final double ACCENT_HUE_RANGE = 40.0;

    private static int[] referenceLight;
    private static int[] referenceDark;
    private static final Map<ColorSchemeSpec, SparseIntArray> cache = new HashMap<>();

    private ThemeBridge() {
    }

    public static boolean isYiliAsset(String assetName) {
        return ASSET_LIGHT.equals(assetName) || ASSET_DARK.equals(assetName);
    }

    public static boolean isYiliTheme(String themeName) {
        return THEME_LIGHT.equals(themeName) || THEME_DARK.equals(themeName);
    }

    public static boolean isDarkTheme(String themeName) {
        return THEME_DARK.equals(themeName);
    }

    /** Colors for a virtual YiliGram asset, in the format {@link Theme#getThemeFileValues} returns. */
    @NonNull
    public static SparseIntArray getThemeFileValues(String assetName) {
        boolean dark = ASSET_DARK.equals(assetName);
        try {
            return buildColors(scheme(dark));
        } catch (Throwable t) {
            // Never break theme loading: fall back to Telegram's defaults (empty map).
            FileLog.e(t);
            return new SparseIntArray();
        }
    }

    /** Current scheme for light or dark mode, from the user's settings. */
    @NonNull
    public static YiliColorScheme scheme(boolean dark) {
        ColorSchemeSpec spec = YiliAppearance.colorSpec(ApplicationLoader.applicationContext, dark);
        YiliColorScheme current = YiliTheme.colors();
        if (current.spec.equals(spec)) {
            return current;
        }
        return YiliColorScheme.generate(spec);
    }

    /** Preview colors (background, incoming bubble, outgoing bubble) for theme pickers. */
    @NonNull
    public static int[] previewColors(boolean dark) {
        try {
            YiliColorScheme s = scheme(dark);
            return new int[]{s.surface(), s.get(ColorRole.SURFACE_CONTAINER_HIGH), s.primaryContainer()};
        } catch (Throwable t) {
            return dark ? new int[]{0xFF141218, 0xFF2B2930, 0xFF4F378B} : new int[]{0xFFFEF7FF, 0xFFECE6F0, 0xFFEADDFF};
        }
    }

    @NonNull
    static synchronized SparseIntArray buildColors(@NonNull YiliColorScheme s) {
        SparseIntArray cached = cache.get(s.spec);
        if (cached != null) {
            return cached.clone();
        }
        int[] ref = reference(s.spec.dark);
        SparseArray<String> names = ThemeColors.createColorKeysMap();
        int primary = s.primary();
        SparseIntArray out = new SparseIntArray(ref.length);
        for (int key = 0; key < ref.length; key++) {
            String name = names.get(key);
            if (name == null || key == Theme.key_wallpaperFileOffset) {
                continue;
            }
            out.put(key, transfer(ref[key], name.startsWith("chat_out"), s, primary));
        }
        RoleOverrides.apply(out, s, names);
        if (cache.size() > 8) {
            cache.clear();
        }
        cache.put(s.spec, out.clone());
        return out;
    }

    private static int[] reference(boolean dark) {
        if (dark) {
            if (referenceDark == null) {
                int[] r = ThemeColors.createDefaultColors();
                SparseIntArray night = Theme.getThemeFileValues(null, REFERENCE_DARK_ASSET, null);
                for (int i = 0; i < night.size(); i++) {
                    int key = night.keyAt(i);
                    if (key >= 0 && key < r.length) {
                        r[key] = night.valueAt(i);
                    }
                }
                referenceDark = r;
            }
            return referenceDark;
        }
        if (referenceLight == null) {
            referenceLight = ThemeColors.createDefaultColors();
        }
        return referenceLight;
    }

    /** Tonal transfer of one reference color onto the M3 palettes (see class docs). */
    static int transfer(int argb, boolean outgoingBubble, YiliColorScheme s, int primary) {
        int alpha = argb >>> 24;
        if (alpha == 0) {
            return argb;
        }
        Hct hct = Hct.fromInt(argb | 0xFF000000);
        int tone = (int) Math.round(hct.getTone());
        double chroma = hct.getChroma();
        int rgb;
        if (chroma < 6) {
            rgb = s.neutralTone(tone);
        } else if (chroma < 16) {
            rgb = s.neutralVariantTone(tone);
        } else if (outgoingBubble || hueDistance(hct.getHue(), TELEGRAM_HUE) < ACCENT_HUE_RANGE) {
            rgb = s.primaryTone(tone);
        } else {
            rgb = Blend.harmonize(argb | 0xFF000000, primary);
        }
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }

    private static double hueDistance(double a, double b) {
        double d = Math.abs(a - b) % 360.0;
        return d > 180 ? 360 - d : d;
    }
}
