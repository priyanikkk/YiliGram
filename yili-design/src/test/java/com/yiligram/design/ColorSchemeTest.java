package com.yiligram.design;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.yiligram.design.color.ColorRole;
import com.yiligram.design.color.ColorSchemeSpec;
import com.yiligram.design.color.ColorSpecVersion;
import com.yiligram.design.color.ContrastLevel;
import com.yiligram.design.color.SchemeVariant;
import com.yiligram.design.color.YiliColorScheme;
import com.yiligram.design.mcu.hct.Hct;

import org.junit.Test;

public class ColorSchemeTest {

    private static final int[] SEEDS = {0xFF6750A4, 0xFF8C7A5B, 0xFF006A6A, 0xFFB3261E, 0xFF000000, 0xFFFFFFFF};

    private static double luminance(int argb) {
        double[] c = new double[3];
        for (int i = 0; i < 3; i++) {
            double v = ((argb >> (16 - 8 * i)) & 0xFF) / 255.0;
            c[i] = v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
    }

    private static double contrast(int a, int b) {
        double la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static YiliColorScheme scheme(int seed, SchemeVariant v, boolean dark, boolean amoled,
                                          ContrastLevel c, ColorSpecVersion spec) {
        return YiliColorScheme.generate(new ColorSchemeSpec(seed, v, dark, amoled, c, spec));
    }

    @Test
    public void everyCombinationProducesOpaqueColors() {
        for (ColorSpecVersion spec : ColorSpecVersion.values()) {
            for (SchemeVariant v : SchemeVariant.values()) {
                for (int seed : SEEDS) {
                    for (boolean dark : new boolean[]{false, true}) {
                        for (ContrastLevel c : ContrastLevel.values()) {
                            YiliColorScheme s = scheme(seed, v, dark, false, c, spec);
                            for (ColorRole role : ColorRole.values()) {
                                if (role == ColorRole.SCRIM || role == ColorRole.SHADOW) continue;
                                assertEquals(spec + " " + v + " " + role, 0xFF, s.get(role) >>> 24);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    public void textOnContainersIsReadable() {
        for (SchemeVariant v : SchemeVariant.values()) {
            for (boolean dark : new boolean[]{false, true}) {
                YiliColorScheme s = scheme(0xFF8C7A5B, v, dark, false, ContrastLevel.STANDARD,
                        ColorSpecVersion.M3_EXPRESSIVE_2025);
                assertTrue(v + " onSurface", contrast(s.onSurface(), s.surface()) >= 7.0);
                assertTrue(v + " onPrimary", contrast(s.onPrimary(), s.primary()) >= 4.5);
                assertTrue(v + " onPrimaryContainer",
                        contrast(s.onPrimaryContainer(), s.primaryContainer()) >= 4.5);
                assertTrue(v + " onSecondaryContainer",
                        contrast(s.onSecondaryContainer(), s.secondaryContainer()) >= 4.5);
            }
        }
    }

    @Test
    public void highContrastIncreasesContrast() {
        for (boolean dark : new boolean[]{false, true}) {
            YiliColorScheme std = scheme(0xFF6750A4, SchemeVariant.TONAL_SPOT, dark, false,
                    ContrastLevel.STANDARD, ColorSpecVersion.M3_EXPRESSIVE_2025);
            YiliColorScheme high = scheme(0xFF6750A4, SchemeVariant.TONAL_SPOT, dark, false,
                    ContrastLevel.HIGH, ColorSpecVersion.M3_EXPRESSIVE_2025);
            assertTrue(contrast(high.onSurfaceVariant(), high.surface())
                    > contrast(std.onSurfaceVariant(), std.surface()));
            assertTrue(contrast(high.outline(), high.surface()) >= 3.0);
        }
    }

    @Test
    public void darkSchemeIsDark() {
        YiliColorScheme light = scheme(0xFF6750A4, SchemeVariant.TONAL_SPOT, false, false,
                ContrastLevel.STANDARD, ColorSpecVersion.M3_EXPRESSIVE_2025);
        YiliColorScheme dark = light.spec.withDark(true) == null ? null
                : YiliColorScheme.generate(light.spec.withDark(true));
        assertNotNull(dark);
        assertTrue(luminance(dark.surface()) < 0.05);
        assertTrue(luminance(light.surface()) > 0.8);
    }

    @Test
    public void amoledMakesSurfacesBlackAndKeepsHierarchy() {
        YiliColorScheme s = scheme(0xFF6750A4, SchemeVariant.TONAL_SPOT, true, true,
                ContrastLevel.STANDARD, ColorSpecVersion.M3_EXPRESSIVE_2025);
        assertEquals(0xFF000000, s.surface());
        assertEquals(0xFF000000, s.get(ColorRole.BACKGROUND));
        ColorRole[] ladder = {ColorRole.SURFACE_CONTAINER_LOWEST, ColorRole.SURFACE_CONTAINER_LOW,
                ColorRole.SURFACE_CONTAINER, ColorRole.SURFACE_CONTAINER_HIGH,
                ColorRole.SURFACE_CONTAINER_HIGHEST, ColorRole.SURFACE_BRIGHT};
        for (int i = 1; i < ladder.length; i++) {
            assertTrue(ladder[i] + " brighter than " + ladder[i - 1],
                    luminance(s.get(ladder[i])) > luminance(s.get(ladder[i - 1])));
        }
        YiliColorScheme lightAmoled = scheme(0xFF6750A4, SchemeVariant.TONAL_SPOT, false, true,
                ContrastLevel.STANDARD, ColorSpecVersion.M3_EXPRESSIVE_2025);
        assertTrue("AMOLED is ignored in light mode", lightAmoled.surface() != 0xFF000000);
    }

    @Test
    public void monochromeHasNoChroma() {
        YiliColorScheme s = scheme(0xFFB3261E, SchemeVariant.MONOCHROME, false, false,
                ContrastLevel.STANDARD, ColorSpecVersion.M3_EXPRESSIVE_2025);
        // CAM16 reports a small chroma even for neutral greys near white, so check RGB directly.
        for (ColorRole role : new ColorRole[]{ColorRole.PRIMARY, ColorRole.SECONDARY,
                ColorRole.TERTIARY, ColorRole.SURFACE, ColorRole.SURFACE_CONTAINER, ColorRole.OUTLINE}) {
            int c = s.get(role);
            int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
            assertTrue(role + " is grey", Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) <= 1);
        }
    }

    @Test
    public void primaryFollowsSeedHueForFidelity() {
        int seed = 0xFF006A6A;
        YiliColorScheme s = scheme(seed, SchemeVariant.FIDELITY, false, false,
                ContrastLevel.STANDARD, ColorSpecVersion.M3_EXPRESSIVE_2025);
        double dh = Math.abs(Hct.fromInt(s.primary()).getHue() - Hct.fromInt(seed).getHue());
        assertTrue("hue delta " + dh, Math.min(dh, 360 - dh) < 15);
    }

    @Test
    public void systemThemeStyleMapping() {
        assertEquals(SchemeVariant.NEUTRAL, SchemeVariant.fromSystemThemeStyle("SPRITZ"));
        assertEquals(SchemeVariant.MONOCHROME, SchemeVariant.fromSystemThemeStyle("MONOCHROMATIC"));
        assertEquals(SchemeVariant.EXPRESSIVE, SchemeVariant.fromSystemThemeStyle("EXPRESSIVE"));
        assertEquals(SchemeVariant.TONAL_SPOT, SchemeVariant.fromSystemThemeStyle(null));
        assertEquals(SchemeVariant.TONAL_SPOT, SchemeVariant.fromSystemThemeStyle("UNKNOWN"));
    }
}
