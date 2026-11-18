package com.yiligram.design.color;

import com.yiligram.design.mcu.dynamiccolor.Variant;

/** Color scheme styles offered to the user, mapped onto material-color-utilities variants. */
public enum SchemeVariant {
    TONAL_SPOT(Variant.TONAL_SPOT),
    NEUTRAL(Variant.NEUTRAL),
    VIBRANT(Variant.VIBRANT),
    EXPRESSIVE(Variant.EXPRESSIVE),
    FIDELITY(Variant.FIDELITY),
    CONTENT(Variant.CONTENT),
    MONOCHROME(Variant.MONOCHROME),
    RAINBOW(Variant.RAINBOW),
    FRUIT_SALAD(Variant.FRUIT_SALAD);

    public final Variant mcu;

    SchemeVariant(Variant mcu) {
        this.mcu = mcu;
    }

    /**
     * Maps the {@code theme_style} value Android writes into
     * {@code Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES}.
     */
    public static SchemeVariant fromSystemThemeStyle(String style) {
        if (style == null) {
            return TONAL_SPOT;
        }
        switch (style) {
            case "SPRITZ":
                return NEUTRAL;
            case "VIBRANT":
                return VIBRANT;
            case "EXPRESSIVE":
                return EXPRESSIVE;
            case "RAINBOW":
                return RAINBOW;
            case "FRUIT_SALAD":
                return FRUIT_SALAD;
            case "CONTENT":
                return CONTENT;
            case "MONOCHROMATIC":
                return MONOCHROME;
            case "TONAL_SPOT":
            default:
                return TONAL_SPOT;
        }
    }
}
