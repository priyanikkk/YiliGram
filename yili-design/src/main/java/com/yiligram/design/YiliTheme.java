package com.yiligram.design;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import com.yiligram.design.color.ColorSchemeSpec;
import com.yiligram.design.color.ColorSpecVersion;
import com.yiligram.design.color.ContrastLevel;
import com.yiligram.design.color.SchemeVariant;
import com.yiligram.design.color.YiliColorScheme;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Entry point of the YiliGram design system: the current color scheme plus change listeners.
 *
 * <p>Type, shape and motion tokens live in {@link com.yiligram.design.type.YiliType},
 * {@link com.yiligram.design.shape.YiliShapes} and {@link com.yiligram.design.motion.YiliMotion}.
 */
public final class YiliTheme {

    /** YiliGram's default seed: the warm metallic beige of the mascot. */
    public static final int DEFAULT_SEED = 0xFF8C7A5B;

    public interface Listener {
        @MainThread
        void onYiliThemeChanged(@NonNull YiliColorScheme scheme);
    }

    private static volatile YiliColorScheme current = YiliColorScheme.generate(new ColorSchemeSpec(
            DEFAULT_SEED, SchemeVariant.TONAL_SPOT, false, false, ContrastLevel.STANDARD,
            ColorSpecVersion.M3_EXPRESSIVE_2025));
    private static final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    private YiliTheme() {
    }

    @NonNull
    public static YiliColorScheme colors() {
        return current;
    }

    /**
     * Regenerates the scheme if {@code spec} differs from the current one.
     *
     * @return true if the scheme changed (listeners were notified)
     */
    @MainThread
    public static boolean apply(@NonNull ColorSchemeSpec spec) {
        if (spec.equals(current.spec)) {
            return false;
        }
        current = YiliColorScheme.generate(spec);
        for (Listener l : listeners) {
            l.onYiliThemeChanged(current);
        }
        return true;
    }

    public static void addListener(@NonNull Listener listener) {
        listeners.addIfAbsent(listener);
    }

    public static void removeListener(@NonNull Listener listener) {
        listeners.remove(listener);
    }
}
