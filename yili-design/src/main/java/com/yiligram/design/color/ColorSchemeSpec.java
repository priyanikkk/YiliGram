package com.yiligram.design.color;

import androidx.annotation.NonNull;

import java.util.Objects;

/** Immutable input of color scheme generation. Equal specs produce equal schemes. */
public final class ColorSchemeSpec {

    public final int seedArgb;
    @NonNull public final SchemeVariant variant;
    public final boolean dark;
    /** Pure black surfaces in dark mode (ignored in light mode). */
    public final boolean amoled;
    @NonNull public final ContrastLevel contrast;
    @NonNull public final ColorSpecVersion specVersion;

    public ColorSchemeSpec(int seedArgb, @NonNull SchemeVariant variant, boolean dark, boolean amoled,
                           @NonNull ContrastLevel contrast, @NonNull ColorSpecVersion specVersion) {
        this.seedArgb = seedArgb | 0xFF000000;
        this.variant = variant;
        this.dark = dark;
        this.amoled = amoled;
        this.contrast = contrast;
        this.specVersion = specVersion;
    }

    public ColorSchemeSpec withDark(boolean dark) {
        return new ColorSchemeSpec(seedArgb, variant, dark, amoled, contrast, specVersion);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ColorSchemeSpec)) return false;
        ColorSchemeSpec s = (ColorSchemeSpec) o;
        return seedArgb == s.seedArgb && dark == s.dark && amoled == s.amoled && variant == s.variant
                && contrast == s.contrast && specVersion == s.specVersion;
    }

    @Override
    public int hashCode() {
        return Objects.hash(seedArgb, variant, dark, amoled, contrast, specVersion);
    }

    @NonNull
    @Override
    public String toString() {
        return String.format("ColorSchemeSpec(#%08X, %s, %s%s, %s, %s)", seedArgb, variant,
                dark ? "dark" : "light", amoled && dark ? "+amoled" : "", contrast, specVersion);
    }
}
