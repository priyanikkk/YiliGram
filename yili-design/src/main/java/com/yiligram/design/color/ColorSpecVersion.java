package com.yiligram.design.color;

import com.yiligram.design.mcu.dynamiccolor.ColorSpec.SpecVersion;

/** Which revision of the M3 color spec to generate schemes with. */
public enum ColorSpecVersion {
    /** Original Material You (Android 12–15). */
    M3_2021(SpecVersion.SPEC_2021),
    /** Material 3 Expressive (Android 16). The YiliGram default. */
    M3_EXPRESSIVE_2025(SpecVersion.SPEC_2025),
    /** Latest revision shipped by material-color-utilities. */
    M3_2026(SpecVersion.SPEC_2026);

    public final SpecVersion mcu;

    ColorSpecVersion(SpecVersion mcu) {
        this.mcu = mcu;
    }
}
