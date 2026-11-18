package com.yiligram.design.elevation;

/**
 * M3 elevation levels. In M3 elevation is expressed mostly through surface container roles
 * (see {@link com.yiligram.design.color.ColorRole}); shadows are reserved for floating
 * elements (FAB, menus, dialogs, snackbars).
 */
public final class YiliElevation {

    public enum Level {
        LEVEL0(0f), LEVEL1(1f), LEVEL2(3f), LEVEL3(6f), LEVEL4(8f), LEVEL5(12f);

        public final float dp;

        Level(float dp) {
            this.dp = dp;
        }
    }

    private YiliElevation() {
    }
}
