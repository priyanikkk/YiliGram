package com.yiligram.config;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.yiligram.design.color.ContrastLevel;
import com.yiligram.design.color.SchemeVariant;
import com.yiligram.design.motion.YiliMotion;

import org.telegram.messenger.R;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Built-in appearance presets. A preset covers color, shape and motion: applying one first resets
 * those keys to their defaults, then sets the preset's own values. Fonts and plugin settings are
 * never touched (a custom font file would otherwise be lost).
 */
public final class YiliPresets {

    public static final class Preset {
        public final String id;
        @StringRes
        public final int name;
        public final Map<YiliConfig.Key<?>, Object> values = new LinkedHashMap<>();

        Preset(String id, @StringRes int name) {
            this.id = id;
            this.name = name;
        }

        <T> Preset with(YiliConfig.Key<T> key, T value) {
            values.put(key, value);
            return this;
        }

        /** Seed color shown on the preset card. */
        public int seed() {
            Object s = values.get(YiliConfig.SEED_COLOR);
            return s != null ? (Integer) s : YiliConfig.SEED_COLOR.defaultValue;
        }

        public SchemeVariant variant() {
            Object v = values.get(YiliConfig.SCHEME_VARIANT);
            return v != null ? (SchemeVariant) v : YiliConfig.SCHEME_VARIANT.defaultValue;
        }

        public float roundness() {
            Object r = values.get(YiliConfig.SHAPE_ROUNDNESS);
            return r != null ? (Float) r : YiliConfig.SHAPE_ROUNDNESS.defaultValue;
        }
    }

    public static final Preset[] ALL = {
            new Preset("yiligram", R.string.YiliPresetDefault),
            new Preset("classic", R.string.YiliPresetClassic)
                    .with(YiliConfig.DYNAMIC_COLOR, false)
                    .with(YiliConfig.SEED_COLOR, 0xFF3390EC)
                    .with(YiliConfig.SCHEME_VARIANT, SchemeVariant.FIDELITY)
                    .with(YiliConfig.SHAPE_ROUNDNESS, 0.6f)
                    .with(YiliConfig.MOTION_SCHEME, YiliMotion.Scheme.STANDARD)
                    .with(YiliConfig.MOTION_BOUNCE, 0.3f),
            new Preset("expressive", R.string.YiliPresetExpressive)
                    .with(YiliConfig.DYNAMIC_COLOR, false)
                    .with(YiliConfig.SEED_COLOR, 0xFF6750A4)
                    .with(YiliConfig.SCHEME_VARIANT, SchemeVariant.EXPRESSIVE)
                    .with(YiliConfig.SHAPE_ROUNDNESS, 1.5f)
                    .with(YiliConfig.BUBBLE_TAILS, false)
                    .with(YiliConfig.MOTION_BOUNCE, 1f),
            new Preset("calm", R.string.YiliPresetCalm)
                    .with(YiliConfig.DYNAMIC_COLOR, false)
                    .with(YiliConfig.SEED_COLOR, 0xFF5B7E6E)
                    .with(YiliConfig.SCHEME_VARIANT, SchemeVariant.NEUTRAL)
                    .with(YiliConfig.SHAPE_ROUNDNESS, 0.85f)
                    .with(YiliConfig.MOTION_SCHEME, YiliMotion.Scheme.STANDARD)
                    .with(YiliConfig.MOTION_SPEED, 0.9f)
                    .with(YiliConfig.MOTION_BOUNCE, 0.2f),
            new Preset("vivid", R.string.YiliPresetVivid)
                    .with(YiliConfig.DYNAMIC_COLOR, false)
                    .with(YiliConfig.SEED_COLOR, 0xFFD0507A)
                    .with(YiliConfig.SCHEME_VARIANT, SchemeVariant.VIBRANT)
                    .with(YiliConfig.SHAPE_ROUNDNESS, 1.25f),
            new Preset("amoled", R.string.YiliPresetAmoled)
                    .with(YiliConfig.DYNAMIC_COLOR, false)
                    .with(YiliConfig.SEED_COLOR, 0xFF5B5B7E)
                    .with(YiliConfig.SCHEME_VARIANT, SchemeVariant.MONOCHROME)
                    .with(YiliConfig.AMOLED, true),
            new Preset("contrast", R.string.YiliPresetContrast)
                    .with(YiliConfig.DYNAMIC_COLOR, false)
                    .with(YiliConfig.SEED_COLOR, 0xFF1B6EF3)
                    .with(YiliConfig.CONTRAST, ContrastLevel.HIGH)
                    .with(YiliConfig.SHAPE_ROUNDNESS, 0.75f)
                    .with(YiliConfig.ANIMATIONS, false),
    };

    private YiliPresets() {
    }

    private static boolean covered(YiliConfig.Key<?> key) {
        return key.name.startsWith("color.") || key.name.startsWith("shape.") || key.name.startsWith("motion.");
    }

    /** Defaults for color/shape/motion, then the preset's own values. */
    @NonNull
    public static Map<YiliConfig.Key<?>, Object> resolve(@NonNull Preset p) {
        Map<YiliConfig.Key<?>, Object> out = new LinkedHashMap<>();
        for (YiliConfig.Key<?> key : YiliConfig.keys().values()) {
            if (covered(key)) {
                out.put(key, key.defaultValue);
            }
        }
        out.putAll(p.values);
        return out;
    }

    public static void apply(@NonNull Preset p) {
        YiliConfig.setAll(resolve(p));
    }

    /** The preset the current configuration matches exactly, or null. */
    public static Preset current() {
        for (Preset p : ALL) {
            boolean match = true;
            for (Map.Entry<YiliConfig.Key<?>, Object> e : resolve(p).entrySet()) {
                if (!e.getValue().equals(YiliConfig.get(e.getKey()))) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return p;
            }
        }
        return null;
    }
}
