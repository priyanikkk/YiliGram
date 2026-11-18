package com.yiligram.design.type;

import androidx.annotation.NonNull;

/** One entry of the M3 type scale. */
public final class TypeStyle {
    public final float sizeSp;
    public final float lineHeightSp;
    /** Letter spacing in em, as {@link android.widget.TextView#setLetterSpacing(float)} expects. */
    public final float letterSpacingEm;
    /** Font weight 100..1000 (400 regular, 500 medium, 700 bold). */
    public final int weight;
    /** Brand (display/headline) typeface vs plain (body/label) typeface. */
    public final boolean brand;

    public TypeStyle(float sizeSp, float lineHeightSp, float letterSpacingEm, int weight, boolean brand) {
        this.sizeSp = sizeSp;
        this.lineHeightSp = lineHeightSp;
        this.letterSpacingEm = letterSpacingEm;
        this.weight = weight;
        this.brand = brand;
    }

    @NonNull
    @Override
    public String toString() {
        return sizeSp + "/" + lineHeightSp + "sp w" + weight;
    }
}
