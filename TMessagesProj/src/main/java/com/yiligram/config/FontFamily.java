package com.yiligram.config;

/** App UI font (Settings → YiliGram → Text). */
public enum FontFamily {
    /** The device's default sans-serif (Telegram's behaviour). */
    SYSTEM(null),
    /** Roboto Flex, the Material 3 Expressive typeface; Latin, Cyrillic, Greek. */
    ROBOTO_FLEX("fonts/yili_robotoflex.ttf"),
    /** Google Sans Flex; Latin and Greek only, other scripts fall back to the system font. */
    GOOGLE_SANS_FLEX("fonts/yili_googlesansflex.ttf"),
    /** A .ttf/.otf file the user picked (copied to files/yili_font.ttf). */
    CUSTOM(null);

    public final String asset;

    FontFamily(String asset) {
        this.asset = asset;
    }
}
