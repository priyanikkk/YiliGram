package com.yiligram.design.color;

import com.yiligram.design.mcu.dynamiccolor.DynamicColor;
import com.yiligram.design.mcu.dynamiccolor.MaterialDynamicColors;

import java.util.function.Function;

/** Every Material 3 color role. Generated list; keep in sync with MaterialDynamicColors. */
public enum ColorRole {
    PRIMARY("primary", MaterialDynamicColors::primary),
    ON_PRIMARY("onPrimary", MaterialDynamicColors::onPrimary),
    PRIMARY_CONTAINER("primaryContainer", MaterialDynamicColors::primaryContainer),
    ON_PRIMARY_CONTAINER("onPrimaryContainer", MaterialDynamicColors::onPrimaryContainer),
    INVERSE_PRIMARY("inversePrimary", MaterialDynamicColors::inversePrimary),
    PRIMARY_FIXED("primaryFixed", MaterialDynamicColors::primaryFixed),
    PRIMARY_FIXED_DIM("primaryFixedDim", MaterialDynamicColors::primaryFixedDim),
    ON_PRIMARY_FIXED("onPrimaryFixed", MaterialDynamicColors::onPrimaryFixed),
    ON_PRIMARY_FIXED_VARIANT("onPrimaryFixedVariant", MaterialDynamicColors::onPrimaryFixedVariant),
    SECONDARY("secondary", MaterialDynamicColors::secondary),
    ON_SECONDARY("onSecondary", MaterialDynamicColors::onSecondary),
    SECONDARY_CONTAINER("secondaryContainer", MaterialDynamicColors::secondaryContainer),
    ON_SECONDARY_CONTAINER("onSecondaryContainer", MaterialDynamicColors::onSecondaryContainer),
    SECONDARY_FIXED("secondaryFixed", MaterialDynamicColors::secondaryFixed),
    SECONDARY_FIXED_DIM("secondaryFixedDim", MaterialDynamicColors::secondaryFixedDim),
    ON_SECONDARY_FIXED("onSecondaryFixed", MaterialDynamicColors::onSecondaryFixed),
    ON_SECONDARY_FIXED_VARIANT("onSecondaryFixedVariant", MaterialDynamicColors::onSecondaryFixedVariant),
    TERTIARY("tertiary", MaterialDynamicColors::tertiary),
    ON_TERTIARY("onTertiary", MaterialDynamicColors::onTertiary),
    TERTIARY_CONTAINER("tertiaryContainer", MaterialDynamicColors::tertiaryContainer),
    ON_TERTIARY_CONTAINER("onTertiaryContainer", MaterialDynamicColors::onTertiaryContainer),
    TERTIARY_FIXED("tertiaryFixed", MaterialDynamicColors::tertiaryFixed),
    TERTIARY_FIXED_DIM("tertiaryFixedDim", MaterialDynamicColors::tertiaryFixedDim),
    ON_TERTIARY_FIXED("onTertiaryFixed", MaterialDynamicColors::onTertiaryFixed),
    ON_TERTIARY_FIXED_VARIANT("onTertiaryFixedVariant", MaterialDynamicColors::onTertiaryFixedVariant),
    ERROR("error", MaterialDynamicColors::error),
    ON_ERROR("onError", MaterialDynamicColors::onError),
    ERROR_CONTAINER("errorContainer", MaterialDynamicColors::errorContainer),
    ON_ERROR_CONTAINER("onErrorContainer", MaterialDynamicColors::onErrorContainer),
    BACKGROUND("background", MaterialDynamicColors::background),
    ON_BACKGROUND("onBackground", MaterialDynamicColors::onBackground),
    SURFACE("surface", MaterialDynamicColors::surface),
    SURFACE_DIM("surfaceDim", MaterialDynamicColors::surfaceDim),
    SURFACE_BRIGHT("surfaceBright", MaterialDynamicColors::surfaceBright),
    SURFACE_CONTAINER_LOWEST("surfaceContainerLowest", MaterialDynamicColors::surfaceContainerLowest),
    SURFACE_CONTAINER_LOW("surfaceContainerLow", MaterialDynamicColors::surfaceContainerLow),
    SURFACE_CONTAINER("surfaceContainer", MaterialDynamicColors::surfaceContainer),
    SURFACE_CONTAINER_HIGH("surfaceContainerHigh", MaterialDynamicColors::surfaceContainerHigh),
    SURFACE_CONTAINER_HIGHEST("surfaceContainerHighest", MaterialDynamicColors::surfaceContainerHighest),
    ON_SURFACE("onSurface", MaterialDynamicColors::onSurface),
    SURFACE_VARIANT("surfaceVariant", MaterialDynamicColors::surfaceVariant),
    ON_SURFACE_VARIANT("onSurfaceVariant", MaterialDynamicColors::onSurfaceVariant),
    INVERSE_SURFACE("inverseSurface", MaterialDynamicColors::inverseSurface),
    INVERSE_ON_SURFACE("inverseOnSurface", MaterialDynamicColors::inverseOnSurface),
    OUTLINE("outline", MaterialDynamicColors::outline),
    OUTLINE_VARIANT("outlineVariant", MaterialDynamicColors::outlineVariant),
    SHADOW("shadow", MaterialDynamicColors::shadow),
    SCRIM("scrim", MaterialDynamicColors::scrim),
    SURFACE_TINT("surfaceTint", MaterialDynamicColors::surfaceTint);

    /** Role name as used in M3 docs, token files and exported YiliGram configs. */
    public final String key;
    final Function<MaterialDynamicColors, DynamicColor> resolver;

    ColorRole(String key, Function<MaterialDynamicColors, DynamicColor> resolver) {
        this.key = key;
        this.resolver = resolver;
    }

    public static ColorRole fromKey(String key) {
        for (ColorRole role : values()) {
            if (role.key.equals(key)) {
                return role;
            }
        }
        return null;
    }
}
