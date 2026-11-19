package com.yiligram.icons;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yiligram.config.YiliConfig;

import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * User icon packs. A pack is a ZIP of PNG / WebP / JPG images named after Telegram's drawables
 * ({@code msg_delete.png}, {@code msg_settings_old.webp}, …), in the root or any folder, plus an
 * optional {@code pack.json} ({@code {"name": "...", "author": "..."}}). Images replace the drawable of
 * the same name everywhere the app loads it through Resources; they are scaled to the original
 * icon's size and tinted by the app like the originals, so white-on-transparent icons work best.
 * Packs live in {@code files/iconpacks/<id>/}; the active one is {@link YiliConfig#ICON_PACK}.
 */
public final class IconPacks {

    public static final class Pack {
        public final String id;
        public final String name;
        public final String author;
        public final int count;

        Pack(String id, String name, String author, int count) {
            this.id = id;
            this.name = name;
            this.author = author;
            this.count = count;
        }
    }

    private static final long MAX_ZIP_BYTES = 64L << 20;
    private static final int MAX_FILES = 5000;

    private static Map<String, File> active;
    private static String activeId;

    private IconPacks() {
    }

    static File root() {
        File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), "iconpacks");
        dir.mkdirs();
        return dir;
    }

    /** Installed packs. */
    @NonNull
    public static List<Pack> list() {
        List<Pack> out = new ArrayList<>();
        File[] dirs = root().listFiles(File::isDirectory);
        if (dirs == null) return out;
        for (File d : dirs) {
            String name = d.getName(), author = "";
            try {
                File meta = new File(d, "pack.json");
                if (meta.exists()) {
                    JSONObject o = new JSONObject(new String(java.nio.file.Files.readAllBytes(meta.toPath()), StandardCharsets.UTF_8));
                    name = o.optString("name", name);
                    author = o.optString("author", "");
                }
            } catch (Exception ignored) {
            }
            String[] icons = d.list((dir, n) -> isImage(n));
            out.add(new Pack(d.getName(), name, author, icons == null ? 0 : icons.length));
        }
        return out;
    }

    private static boolean isImage(String n) {
        String l = n.toLowerCase(Locale.US);
        return l.endsWith(".png") || l.endsWith(".webp") || l.endsWith(".jpg") || l.endsWith(".jpeg");
    }

    /** Installs a ZIP; returns the new pack, or throws with a readable message. */
    @NonNull
    public static Pack install(@NonNull InputStream in, @Nullable String fileName) throws Exception {
        String base = fileName == null ? "pack" : fileName.replaceAll("(?i)\\.zip$", "");
        String id = base.replaceAll("[^A-Za-z0-9._-]", "_");
        if (id.isEmpty() || id.startsWith(".")) id = "pack";
        File dir = new File(root(), id);
        for (int i = 2; dir.exists(); i++) {
            dir = new File(root(), id + "_" + i);
        }
        File tmp = new File(root(), "." + dir.getName() + ".tmp");
        deleteRecursive(tmp);
        tmp.mkdirs();
        int files = 0;
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry e;
            byte[] buf = new byte[16384];
            while ((e = zip.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String entry = e.getName();
                String leaf = entry.substring(entry.lastIndexOf('/') + 1);
                boolean meta = leaf.equals("pack.json");
                if (!meta && !isImage(leaf)) continue;
                if (!meta && !leaf.matches("[a-z0-9_]+\\.[A-Za-z]+")) continue; // drawable names only
                if (++files > MAX_FILES) throw new IllegalArgumentException("too many files in the pack");
                File out = new File(tmp, leaf);
                try (FileOutputStream os = new FileOutputStream(out)) {
                    int n;
                    while ((n = zip.read(buf)) > 0) {
                        total += n;
                        if (total > MAX_ZIP_BYTES) throw new IllegalArgumentException("the pack is larger than 64 MB");
                        os.write(buf, 0, n);
                    }
                }
            }
        } catch (Exception ex) {
            deleteRecursive(tmp);
            throw ex;
        }
        String[] icons = tmp.list((d, n) -> isImage(n));
        if (icons == null || icons.length == 0) {
            deleteRecursive(tmp);
            throw new IllegalArgumentException("no icons named like Telegram drawables (e.g. msg_delete.png) in the ZIP");
        }
        if (!tmp.renameTo(dir)) {
            deleteRecursive(tmp);
            throw new IllegalStateException("cannot store the pack");
        }
        for (Pack p : list()) {
            if (p.id.equals(dir.getName())) return p;
        }
        return new Pack(dir.getName(), dir.getName(), "", icons.length);
    }

    public static void delete(@NonNull String id) {
        if (id.equals(YiliConfig.get(YiliConfig.ICON_PACK))) {
            YiliConfig.set(YiliConfig.ICON_PACK, "");
        }
        deleteRecursive(new File(root(), id));
    }

    private static void deleteRecursive(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteRecursive(k);
        f.delete();
    }

    /** Drawable name -> image file of the active pack; empty when no pack is active. */
    @NonNull
    static synchronized Map<String, File> activeIcons() {
        String id = YiliConfig.get(YiliConfig.ICON_PACK);
        if (active != null && TextUtils.equals(id, activeId)) return active;
        Map<String, File> map = new HashMap<>();
        if (!TextUtils.isEmpty(id)) {
            File[] files = new File(root(), id).listFiles();
            if (files != null) {
                for (File f : files) {
                    String n = f.getName();
                    if (isImage(n)) map.put(n.substring(0, n.lastIndexOf('.')), f);
                }
            }
        }
        active = map;
        activeId = id;
        return map;
    }

    @Nullable
    static Bitmap decode(File f) {
        try {
            return BitmapFactory.decodeFile(f.getAbsolutePath());
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        }
    }

    /** {@code base} wrapped so that drawables come from the active pack (or {@code base} itself). */
    @NonNull
    public static Resources wrap(@NonNull Resources base) {
        if (!YiliConfig.isInitialized() || ApplicationLoader.applicationContext == null || activeIcons().isEmpty()) return base;
        return IconResources.of(base);
    }
}
