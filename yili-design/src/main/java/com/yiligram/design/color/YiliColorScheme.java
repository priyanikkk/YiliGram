package com.yiligram.design.color;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import com.yiligram.design.mcu.dynamiccolor.DynamicScheme;
import com.yiligram.design.mcu.dynamiccolor.MaterialDynamicColors;
import com.yiligram.design.mcu.hct.Hct;
import com.yiligram.design.mcu.palettes.TonalPalette;
import com.yiligram.design.mcu.scheme.SchemeContent;
import com.yiligram.design.mcu.scheme.SchemeExpressive;
import com.yiligram.design.mcu.scheme.SchemeFidelity;
import com.yiligram.design.mcu.scheme.SchemeFruitSalad;
import com.yiligram.design.mcu.scheme.SchemeMonochrome;
import com.yiligram.design.mcu.scheme.SchemeNeutral;
import com.yiligram.design.mcu.scheme.SchemeRainbow;
import com.yiligram.design.mcu.scheme.SchemeTonalSpot;
import com.yiligram.design.mcu.scheme.SchemeVibrant;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A fully resolved Material 3 color scheme: one ARGB value per {@link ColorRole}.
 *
 * <p>Generated with material-color-utilities from a {@link ColorSchemeSpec}; immutable.
 */
public final class YiliColorScheme {

    private static final MaterialDynamicColors DYNAMIC_COLORS = new MaterialDynamicColors();

    @NonNull public final ColorSchemeSpec spec;
    private final int[] colors;
    private final TonalPalette neutralPalette;
    private final TonalPalette neutralVariantPalette;
    private final TonalPalette primaryPalette;

    private YiliColorScheme(@NonNull ColorSchemeSpec spec, int[] colors, TonalPalette neutralPalette,
                            TonalPalette neutralVariantPalette, TonalPalette primaryPalette) {
        this.spec = spec;
        this.colors = colors;
        this.neutralPalette = neutralPalette;
        this.neutralVariantPalette = neutralVariantPalette;
        this.primaryPalette = primaryPalette;
    }

    @NonNull
    public static YiliColorScheme generate(@NonNull ColorSchemeSpec spec) {
        DynamicScheme scheme = createDynamicScheme(spec);
        int[] colors = new int[ColorRole.values().length];
        for (ColorRole role : ColorRole.values()) {
            colors[role.ordinal()] = scheme.getArgb(role.resolver.apply(DYNAMIC_COLORS));
        }
        if (spec.dark && spec.amoled) {
            applyAmoled(colors, scheme.neutralPalette);
        }
        return new YiliColorScheme(spec, colors, scheme.neutralPalette, scheme.neutralVariantPalette,
                scheme.primaryPalette);
    }

    static DynamicScheme createDynamicScheme(ColorSchemeSpec spec) {
        Hct seed = Hct.fromInt(spec.seedArgb);
        double c = spec.contrast.value;
        boolean d = spec.dark;
        var v = spec.specVersion.mcu;
        var p = DynamicScheme.DEFAULT_PLATFORM;
        switch (spec.variant) {
            case NEUTRAL: return new SchemeNeutral(seed, d, c, v, p);
            case VIBRANT: return new SchemeVibrant(seed, d, c, v, p);
            case EXPRESSIVE: return new SchemeExpressive(seed, d, c, v, p);
            case FIDELITY: return new SchemeFidelity(seed, d, c, v, p);
            case CONTENT: return new SchemeContent(seed, d, c, v, p);
            case MONOCHROME: return new SchemeMonochrome(seed, d, c, v, p);
            case RAINBOW: return new SchemeRainbow(seed, d, c, v, p);
            case FRUIT_SALAD: return new SchemeFruitSalad(seed, d, c, v, p);
            case TONAL_SPOT:
            default: return new SchemeTonalSpot(seed, d, c, v, p);
        }
    }

    /**
     * AMOLED: the base surface becomes pure black and the container hierarchy is rebuilt from
     * the neutral palette starting just above black, so cards and sheets stay distinguishable.
     */
    private static void applyAmoled(int[] colors, TonalPalette neutral) {
        colors[ColorRole.BACKGROUND.ordinal()] = 0xFF000000;
        colors[ColorRole.SURFACE.ordinal()] = 0xFF000000;
        colors[ColorRole.SURFACE_DIM.ordinal()] = 0xFF000000;
        colors[ColorRole.SURFACE_CONTAINER_LOWEST.ordinal()] = 0xFF000000;
        colors[ColorRole.SURFACE_CONTAINER_LOW.ordinal()] = neutral.tone(4);
        colors[ColorRole.SURFACE_CONTAINER.ordinal()] = neutral.tone(6);
        colors[ColorRole.SURFACE_CONTAINER_HIGH.ordinal()] = neutral.tone(10);
        colors[ColorRole.SURFACE_CONTAINER_HIGHEST.ordinal()] = neutral.tone(15);
        colors[ColorRole.SURFACE_BRIGHT.ordinal()] = neutral.tone(20);
    }

    @ColorInt
    public int get(@NonNull ColorRole role) {
        return colors[role.ordinal()];
    }

    /** Arbitrary tone (0..100) of the neutral palette, for surfaces between the M3 roles. */
    @ColorInt
    public int neutralTone(int tone) {
        return neutralPalette.tone(tone);
    }

    /** Arbitrary tone (0..100) of the neutral variant palette (slightly tinted greys). */
    @ColorInt
    public int neutralVariantTone(int tone) {
        return neutralVariantPalette.tone(tone);
    }

    /** Arbitrary tone (0..100) of the primary palette. */
    @ColorInt
    public int primaryTone(int tone) {
        return primaryPalette.tone(tone);
    }

    /** All roles as {@code key → ARGB}, in declaration order (for export and debugging). */
    @NonNull
    public Map<String, Integer> toMap() {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (ColorRole role : ColorRole.values()) {
            map.put(role.key, colors[role.ordinal()]);
        }
        return map;
    }

    // Shorthands for the roles used most often.
    @ColorInt public int primary() { return get(ColorRole.PRIMARY); }
    @ColorInt public int onPrimary() { return get(ColorRole.ON_PRIMARY); }
    @ColorInt public int primaryContainer() { return get(ColorRole.PRIMARY_CONTAINER); }
    @ColorInt public int onPrimaryContainer() { return get(ColorRole.ON_PRIMARY_CONTAINER); }
    @ColorInt public int secondaryContainer() { return get(ColorRole.SECONDARY_CONTAINER); }
    @ColorInt public int onSecondaryContainer() { return get(ColorRole.ON_SECONDARY_CONTAINER); }
    @ColorInt public int surface() { return get(ColorRole.SURFACE); }
    @ColorInt public int onSurface() { return get(ColorRole.ON_SURFACE); }
    @ColorInt public int onSurfaceVariant() { return get(ColorRole.ON_SURFACE_VARIANT); }
    @ColorInt public int surfaceContainer() { return get(ColorRole.SURFACE_CONTAINER); }
    @ColorInt public int outline() { return get(ColorRole.OUTLINE); }
    @ColorInt public int outlineVariant() { return get(ColorRole.OUTLINE_VARIANT); }
}
