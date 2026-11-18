package com.yiligram.design.color;

import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.yiligram.design.mcu.hct.Hct;

import org.json.JSONObject;

/**
 * Reads the seed color and style the system uses for Material You (Android 12+).
 *
 * <p>Order of sources:
 * <ol>
 *   <li>{@code Settings.Secure.theme_customization_overlay_packages}: the exact seed
 *       ({@code system_palette}) and style ({@code theme_style}) chosen in "Wallpaper &amp;
 *       style", readable without permissions;</li>
 *   <li>the hue of {@code system_accent1_500}, which carries the seed hue on every vendor;</li>
 *   <li>the wallpaper's primary color.</li>
 * </ol>
 */
public final class DynamicColorSource {

    private static final String OVERLAY_SETTING = "theme_customization_overlay_packages";
    private static final String KEY_PALETTE = "android.theme.customization.system_palette";
    private static final String KEY_STYLE = "android.theme.customization.theme_style";

    /** Result of reading the system theme. */
    public static final class SystemTheme {
        public final int seedArgb;
        @NonNull public final SchemeVariant variant;

        SystemTheme(int seedArgb, @NonNull SchemeVariant variant) {
            this.seedArgb = seedArgb;
            this.variant = variant;
        }
    }

    private DynamicColorSource() {
    }

    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    /** @return the system seed and style, or null below Android 12 or if nothing could be read. */
    @Nullable
    public static SystemTheme read(@NonNull Context context) {
        if (!isSupported()) {
            return null;
        }
        SchemeVariant variant = SchemeVariant.TONAL_SPOT;
        Integer seed = null;
        try {
            String json = Settings.Secure.getString(context.getContentResolver(), OVERLAY_SETTING);
            if (!TextUtils.isEmpty(json)) {
                JSONObject o = new JSONObject(json);
                variant = SchemeVariant.fromSystemThemeStyle(o.optString(KEY_STYLE, null));
                String palette = o.optString(KEY_PALETTE, null);
                if (!TextUtils.isEmpty(palette)) {
                    seed = (int) Long.parseLong(palette.replace("#", ""), 16) | 0xFF000000;
                }
            }
        } catch (Exception ignored) {
            // Malformed or vendor-specific value: fall through to the next source.
        }
        if (seed == null) {
            seed = accentSeed(context);
        }
        if (seed == null) {
            seed = wallpaperSeed(context);
        }
        return seed == null ? null : new SystemTheme(seed, variant);
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @Nullable
    private static Integer accentSeed(Context context) {
        try {
            int accent = context.getColor(android.R.color.system_accent1_500);
            Hct hct = Hct.fromInt(accent);
            // Keep the hue, use a chroma that every scheme variant can reshape.
            return Hct.from(hct.getHue(), Math.max(hct.getChroma(), 48.0), 40.0).toInt();
        } catch (Exception e) {
            return null;
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @Nullable
    private static Integer wallpaperSeed(Context context) {
        try {
            WallpaperColors colors = WallpaperManager.getInstance(context)
                    .getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
            return colors == null ? null : colors.getPrimaryColor().toArgb();
        } catch (Exception e) {
            return null;
        }
    }
}
