package com.yiligram.config;

import android.graphics.Typeface;
import android.os.Build;

import androidx.annotation.Nullable;

import org.lsposed.hiddenapibypass.HiddenApiBypass;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Applies the chosen UI font once at startup (a change needs an app restart).
 *
 * <p>Telegram draws bold text with {@link AndroidUtilities#bold()} and regular text with the
 * platform default typeface, so both are replaced: the bold typeface directly, the default by
 * swapping {@code Typeface.DEFAULT}/{@code SANS_SERIF}/{@code DEFAULT_BOLD}, the default
 * style table and the "sans-serif" family entries. Scripts a font lacks fall back to the system
 * font. Variable fonts get their weights through the {@code wght} axis.
 */
public final class YiliFonts {
    public static final String CUSTOM_FILE = "yili_font.ttf";
    private static final int REGULAR = 400;
    private static final int MEDIUM = 500;

    private YiliFonts() {
    }

    public static File customFile() {
        return new File(ApplicationLoader.getFilesDirFixed(), CUSTOM_FILE);
    }

    public static void apply() {
        FontFamily family = YiliConfig.get(YiliConfig.FONT_FAMILY);
        if (family == FontFamily.SYSTEM || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        int delta = YiliConfig.get(YiliConfig.FONT_WEIGHT_DELTA);
        Typeface regular = build(family, clamp(REGULAR + delta));
        Typeface medium = build(family, clamp(MEDIUM + delta));
        if (regular == null || medium == null) {
            return;
        }
        AndroidUtilities.mediumTypeface = medium;
        replaceDefaults(regular, medium);
    }

    private static int clamp(int w) {
        return Math.max(100, Math.min(900, w));
    }

    @Nullable
    private static Typeface build(FontFamily family, int weight) {
        try {
            Typeface.Builder b;
            if (family == FontFamily.CUSTOM) {
                File f = customFile();
                if (!f.isFile()) {
                    return null;
                }
                b = new Typeface.Builder(f);
            } else {
                b = new Typeface.Builder(ApplicationLoader.applicationContext.getAssets(), family.asset);
            }
            b.setFontVariationSettings("'wght' " + weight);
            b.setWeight(weight);
            b.setFallback("sans-serif");
            return b.build();
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        }
    }

    @SuppressWarnings({"unchecked", "JavaReflectionMemberAccess"})
    private static void replaceDefaults(Typeface regular, Typeface medium) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                HiddenApiBypass.addHiddenApiExemptions("Landroid/graphics/Typeface;");
            }
            setStatic("DEFAULT", regular);
            setStatic("SANS_SERIF", regular);
            setStatic("DEFAULT_BOLD", medium);
            setStatic("sDefaultTypeface", regular);
            Field defaults = field("sDefaults");
            if (defaults != null) {
                Typeface[] table = ((Typeface[]) defaults.get(null)).clone();
                table[Typeface.NORMAL] = regular;
                table[Typeface.BOLD] = medium;
                defaults.set(null, table);
            }
            Field map = field("sSystemFontMap");
            if (map != null) {
                Map<String, Typeface> fonts = new HashMap<>((Map<String, Typeface>) map.get(null));
                fonts.put("sans-serif", regular);
                fonts.put("sans-serif-medium", medium);
                map.set(null, fonts);
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    @Nullable
    private static Field field(String name) {
        try {
            Field f = Typeface.class.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void setStatic(String name, Object value) {
        Field f = field(name);
        if (f != null) {
            try {
                f.set(null, value);
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }
    }
}
