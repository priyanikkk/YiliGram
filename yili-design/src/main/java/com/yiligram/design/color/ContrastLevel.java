package com.yiligram.design.color;

/** M3 contrast levels; the value is the material-color-utilities contrast level (-1..1). */
public enum ContrastLevel {
    STANDARD(0.0),
    MEDIUM(0.5),
    HIGH(1.0);

    public final double value;

    ContrastLevel(double value) {
        this.value = value;
    }
}
