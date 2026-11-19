package com.yiligram.icons;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.lsposed.hiddenapibypass.HiddenApiBypass;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Resources that answer drawable lookups from the active icon pack. It shares the base Resources'
 * hidden ResourcesImpl (re-synced on every drawable lookup, so configuration changes carry over),
 * so strings, dimensions and everything else behave exactly like the base.
 */
final class IconResources extends Resources {

    private static final WeakHashMap<Resources, IconResources> wrappers = new WeakHashMap<>();
    private static Method getImpl, setImpl;
    private static boolean failed;

    private final Resources base;
    private final SparseArray<Bitmap> icons = new SparseArray<>();
    private final SparseArray<Boolean> misses = new SparseArray<>();
    private Object syncedImpl;

    @SuppressWarnings("deprecation")
    private IconResources(Resources base) {
        // Placeholder state; the real ResourcesImpl is set right after construction.
        super(base.getAssets(), base.getDisplayMetrics(), base.getConfiguration());
        this.base = base;
    }

    static synchronized Resources of(Resources base) {
        if (base instanceof IconResources) return base;
        if (failed || Build.VERSION.SDK_INT < 24) return base;
        IconResources w = wrappers.get(base);
        if (w != null) return w;
        try {
            if (getImpl == null) {
                getImpl = HiddenApiBypass.getDeclaredMethod(Resources.class, "getImpl");
                Class<?> implClass = Class.forName("android.content.res.ResourcesImpl");
                setImpl = HiddenApiBypass.getDeclaredMethod(Resources.class, "setImpl", implClass);
                getImpl.setAccessible(true);
                setImpl.setAccessible(true);
            }
            w = new IconResources(base);
            w.sync();
            wrappers.put(base, w);
            return w;
        } catch (Throwable t) {
            failed = true;
            FileLog.e("icon packs: cannot wrap Resources", t);
            return base;
        }
    }

    private void sync() {
        try {
            Object impl = getImpl.invoke(base);
            if (impl != syncedImpl) {
                setImpl.invoke(this, impl);
                syncedImpl = impl;
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    @Nullable
    private Drawable fromPack(int id, @Nullable Theme theme) {
        if (misses.get(id) != null) return null;
        Bitmap bmp = icons.get(id);
        if (bmp == null) {
            String name;
            try {
                if (!"drawable".equals(base.getResourceTypeName(id))) {
                    misses.put(id, Boolean.TRUE);
                    return null;
                }
                name = base.getResourceEntryName(id);
            } catch (Throwable t) {
                misses.put(id, Boolean.TRUE);
                return null;
            }
            Map<String, File> pack = IconPacks.activeIcons();
            File f = pack.get(name);
            Bitmap raw = f == null ? null : IconPacks.decode(f);
            if (raw == null) {
                misses.put(id, Boolean.TRUE);
                return null;
            }
            // Same size as the icon it replaces.
            int w = raw.getWidth(), h = raw.getHeight();
            try {
                Drawable orig = super.getDrawable(id, theme);
                if (orig != null && orig.getIntrinsicWidth() > 0 && orig.getIntrinsicHeight() > 0) {
                    w = orig.getIntrinsicWidth();
                    h = orig.getIntrinsicHeight();
                }
            } catch (Throwable ignored) {
            }
            bmp = (w == raw.getWidth() && h == raw.getHeight()) ? raw : Bitmap.createScaledBitmap(raw, w, h, true);
            bmp.setDensity(getDisplayMetrics().densityDpi);
            icons.put(id, bmp);
        }
        return new BitmapDrawable(this, bmp);
    }

    @NonNull
    @Override
    @SuppressWarnings("deprecation")
    public Drawable getDrawable(int id) throws NotFoundException {
        return getDrawable(id, null);
    }

    @NonNull
    @Override
    public Drawable getDrawable(int id, @Nullable Theme theme) throws NotFoundException {
        sync();
        Drawable d = fromPack(id, theme);
        return d != null ? d : super.getDrawable(id, theme);
    }

    @Nullable
    @Override
    @SuppressWarnings("deprecation")
    public Drawable getDrawableForDensity(int id, int density) throws NotFoundException {
        return getDrawableForDensity(id, density, null);
    }

    @Nullable
    @Override
    public Drawable getDrawableForDensity(int id, int density, @Nullable Theme theme) {
        sync();
        Drawable d = fromPack(id, theme);
        return d != null ? d : super.getDrawableForDensity(id, density, theme);
    }
}
