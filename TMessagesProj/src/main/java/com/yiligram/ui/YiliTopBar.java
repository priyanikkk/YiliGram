package com.yiligram.ui;

import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import com.yiligram.bridge.ThemeBridge;
import com.yiligram.config.YiliConfig;
import com.yiligram.design.YiliTheme;
import com.yiligram.design.color.ColorRole;

import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;

/**
 * Material 3 top app bar for Telegram's fragments: no drop shadow; when the content under the
 * bar is scrolled, the bar's color moves from surface to surface-container (the "on scroll"
 * state of the M3 top app bar). Only bars with the default plain color are recolored; headers
 * with their own color or a transparent background just lose the shadow.
 */
public final class YiliTopBar {

    private static final int KEY_STATE = 0x59494c4a;

    private static final class State {
        float progress;
        int appliedColor;
        View scrollable;
        boolean searched;
    }

    private YiliTopBar() {
    }

    public static boolean enabled() {
        return YiliConfig.get(YiliConfig.M3_CONTROLS) && Theme.getActiveTheme() != null
                && ThemeBridge.isYiliTheme(Theme.getActiveTheme().getKey());
    }

    /** Called while drawing the content below {@code bar}; returns true when the shadow must be skipped. */
    public static boolean update(ActionBar bar, View content) {
        if (!enabled()) {
            return false;
        }
        State st = (State) bar.getTag(KEY_STATE);
        if (st == null) {
            st = new State();
            bar.setTag(KEY_STATE, st);
        }
        Drawable bg = bar.getBackground();
        if (!(bg instanceof ColorDrawable)) {
            return true;
        }
        final int surface = YiliTheme.colors().get(ColorRole.SURFACE);
        final int container = YiliTheme.colors().get(ColorRole.SURFACE_CONTAINER);
        int current = ((ColorDrawable) bg).getColor();
        if (current != surface && current != st.appliedColor && current != Theme.getColor(Theme.key_actionBarDefault)) {
            return true; // a header with its own color
        }
        if (!st.searched || st.scrollable == null || !st.scrollable.isAttachedToWindow()) {
            st.scrollable = findScrollable(content, 0);
            st.searched = true;
        }
        boolean scrolled = st.scrollable != null && st.scrollable.canScrollVertically(-1);
        float target = scrolled ? 1f : 0f;
        if (st.progress != target) {
            st.progress += (target > st.progress ? 1 : -1) * 0.12f;
            st.progress = Math.max(0f, Math.min(1f, st.progress));
            content.postInvalidateOnAnimation();
        }
        int color = ColorUtils.blendARGB(surface, container, st.progress);
        if (color != current) {
            st.appliedColor = color;
            ((ColorDrawable) bg).setColor(color);
            bar.invalidate();
        }
        return true;
    }

    private static View findScrollable(View v, int depth) {
        if (v == null || v.getVisibility() != View.VISIBLE || depth > 5) {
            return null;
        }
        if (v instanceof RecyclerView || v instanceof android.widget.ScrollView || v instanceof android.widget.AbsListView
                || v instanceof androidx.core.widget.NestedScrollView) {
            return v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View r = findScrollable(g.getChildAt(i), depth + 1);
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }
}
