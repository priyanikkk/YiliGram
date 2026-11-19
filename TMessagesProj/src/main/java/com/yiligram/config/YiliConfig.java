package com.yiligram.config;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.yiligram.design.YiliTheme;
import com.yiligram.design.color.ColorSpecVersion;
import com.yiligram.design.color.ContrastLevel;
import com.yiligram.design.color.SchemeVariant;
import com.yiligram.design.motion.YiliMotion;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * All YiliGram settings in one place: typed keys, one SharedPreferences file, change listeners,
 * JSON export/import.
 *
 * <pre>
 *   boolean amoled = YiliConfig.get(YiliConfig.AMOLED);
 *   YiliConfig.set(YiliConfig.AMOLED, true);   // listeners are notified on the main thread
 * </pre>
 */
public final class YiliConfig {

    /** Bump when a key changes meaning; {@link #importJson} migrates older exports. */
    public static final int SCHEMA_VERSION = 1;
    private static final String PREFS = "yiliconfig";

    // ---------------------------------------------------------------- keys

    public static final class Key<T> {
        public final String name;
        public final Class<T> type;
        public final T defaultValue;

        private Key(String name, Class<T> type, T defaultValue) {
            this.name = name;
            this.type = type;
            this.defaultValue = defaultValue;
            KEYS.put(name, this);
        }

        @NonNull
        @Override
        public String toString() {
            return name;
        }
    }

    private static final Map<String, Key<?>> KEYS = new LinkedHashMap<>();

    // Color
    public static final Key<Boolean> DYNAMIC_COLOR = new Key<>("color.dynamic", Boolean.class, true);
    public static final Key<Integer> SEED_COLOR = new Key<>("color.seed", Integer.class, YiliTheme.DEFAULT_SEED);
    /** With dynamic color: take the scheme style from the system ("Wallpaper &amp; style"). */
    public static final Key<Boolean> FOLLOW_SYSTEM_STYLE = new Key<>("color.followSystemStyle", Boolean.class, true);
    public static final Key<SchemeVariant> SCHEME_VARIANT = new Key<>("color.variant", SchemeVariant.class, SchemeVariant.TONAL_SPOT);
    public static final Key<ContrastLevel> CONTRAST = new Key<>("color.contrast", ContrastLevel.class, ContrastLevel.STANDARD);
    public static final Key<Boolean> AMOLED = new Key<>("color.amoled", Boolean.class, false);
    public static final Key<ColorSpecVersion> COLOR_SPEC = new Key<>("color.spec", ColorSpecVersion.class, ColorSpecVersion.M3_EXPRESSIVE_2025);

    // Shape
    public static final Key<Float> SHAPE_ROUNDNESS = new Key<>("shape.roundness", Float.class, 1f);

    // Type
    public static final Key<Float> FONT_SCALE = new Key<>("type.scale", Float.class, 1f);
    public static final Key<Integer> FONT_WEIGHT_DELTA = new Key<>("type.weightDelta", Integer.class, 0);
    /** UI font; applied at startup (YiliFonts). */
    /** Interface scale, 0.8–1.25 (restart). */
    public static final Key<Float> UI_SCALE = new Key<>("layout.scale", Float.class, 1f);
    public static final Key<FontFamily> FONT_FAMILY = new Key<>("type.family", FontFamily.class, FontFamily.SYSTEM);

    // Motion
    public static final Key<Boolean> ANIMATIONS = new Key<>("motion.enabled", Boolean.class, true);
    public static final Key<YiliMotion.Scheme> MOTION_SCHEME = new Key<>("motion.scheme", YiliMotion.Scheme.class, YiliMotion.Scheme.EXPRESSIVE);
    public static final Key<Float> MOTION_SPEED = new Key<>("motion.speed", Float.class, 1f);
    public static final Key<Float> MOTION_BOUNCE = new Key<>("motion.bounce", Float.class, 1f);

    // Plugins
    /** Message context menu items to hide: comma-separated {@link YiliMenus.MessageItem} names. */
    public static final Key<String> HIDDEN_MESSAGE_ITEMS = new Key<>("menu.message.hidden", String.class, "");

    public static final Key<Boolean> HIDE_CONTACTS_TAB = new Key<>("nav.hideContacts", Boolean.class, false);
    public static final Key<Boolean> TAB_LABELS = new Key<>("nav.labels", Boolean.class, true);
    public static final Key<Boolean> HIDE_PROFILE_TAB = new Key<>("nav.hideProfile", Boolean.class, false);

    /** Call button in a private chat's header; when off, Call moves to the ⋮ menu (Telegram's own fallback). */
    public static final Key<Boolean> HEADER_CALL_BUTTON = new Key<>("header.call", Boolean.class, true);
    public static final Key<Boolean> HIDE_GIFT_BUTTON = new Key<>("input.hideGift", Boolean.class, false);
    public static final Key<Boolean> VOICE_ONLY = new Key<>("input.voiceOnly", Boolean.class, false);

    public static final Key<Boolean> PLUGINS_ENABLED = new Key<>("plugins.enabled", Boolean.class, true);
    public static final Key<Boolean> PLUGINS_DEV_MODE = new Key<>("plugins.devMode", Boolean.class, false);
    public static final Key<Boolean> PLUGINS_UNKNOWN_SOURCES = new Key<>("plugins.unknownSources", Boolean.class, false);

    // ---------------------------------------------------------------- storage

    public interface Listener {
        /** Called on the main thread with the keys whose values changed. */
        void onYiliConfigChanged(@NonNull Set<Key<?>> changed);
    }

    private static SharedPreferences prefs;
    private static final Map<Key<?>, Object> cache = new LinkedHashMap<>();
    private static final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private YiliConfig() {
    }

    public static synchronized void init(@NonNull Context context) {
        if (prefs == null) {
            prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        }
    }

    @NonNull
    public static Map<String, Key<?>> keys() {
        return Collections.unmodifiableMap(KEYS);
    }

    @SuppressWarnings("unchecked")
    @NonNull
    public static synchronized <T> T get(@NonNull Key<T> key) {
        Object v = cache.get(key);
        if (v == null) {
            v = read(key);
            cache.put(key, v);
        }
        return (T) v;
    }

    public static <T> void set(@NonNull Key<T> key, @NonNull T value) {
        Map<Key<?>, Object> one = new LinkedHashMap<>();
        one.put(key, value);
        setAll(one);
    }

    /** Applies several values atomically; listeners get one notification. */
    public static void setAll(@NonNull Map<Key<?>, Object> values) {
        Set<Key<?>> changed = new HashSet<>();
        synchronized (YiliConfig.class) {
            SharedPreferences.Editor e = prefs.edit();
            for (Map.Entry<Key<?>, Object> entry : values.entrySet()) {
                Key<?> key = entry.getKey();
                Object value = entry.getValue();
                if (value == null || !key.type.isInstance(value)) {
                    throw new IllegalArgumentException(key + " expects " + key.type.getSimpleName());
                }
                if (value.equals(get(key))) {
                    continue;
                }
                write(e, key, value);
                cache.put(key, value);
                changed.add(key);
            }
            e.apply();
        }
        if (!changed.isEmpty()) {
            notifyListeners(changed);
        }
    }

    public static void reset(@NonNull Key<?> key) {
        Map<Key<?>, Object> one = new LinkedHashMap<>();
        one.put(key, key.defaultValue);
        setAll(one);
    }

    public static void addListener(@NonNull Listener l) {
        listeners.addIfAbsent(l);
    }

    public static void removeListener(@NonNull Listener l) {
        listeners.remove(l);
    }

    private static void notifyListeners(Set<Key<?>> changed) {
        Runnable r = () -> {
            for (Listener l : listeners) {
                l.onYiliConfigChanged(changed);
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run();
        } else {
            mainHandler.post(r);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object read(Key<?> key) {
        if (prefs == null || !prefs.contains(key.name)) {
            return key.defaultValue;
        }
        try {
            Class<?> t = key.type;
            if (t == Boolean.class) return prefs.getBoolean(key.name, (Boolean) key.defaultValue);
            if (t == Integer.class) return prefs.getInt(key.name, (Integer) key.defaultValue);
            if (t == Float.class) return prefs.getFloat(key.name, (Float) key.defaultValue);
            if (t == String.class) return prefs.getString(key.name, (String) key.defaultValue);
            if (t.isEnum()) return Enum.valueOf((Class) t, prefs.getString(key.name, ((Enum) key.defaultValue).name()));
        } catch (Exception ignored) {
            // Wrong type or removed enum constant: fall back to the default.
        }
        return key.defaultValue;
    }

    private static void write(SharedPreferences.Editor e, Key<?> key, Object value) {
        if (value instanceof Boolean) e.putBoolean(key.name, (Boolean) value);
        else if (value instanceof Integer) e.putInt(key.name, (Integer) value);
        else if (value instanceof Float) e.putFloat(key.name, (Float) value);
        else if (value instanceof String) e.putString(key.name, (String) value);
        else if (value instanceof Enum) e.putString(key.name, ((Enum<?>) value).name());
    }

    // ---------------------------------------------------------------- export / import

    /** Every key with its current value, plus the schema version. */
    @NonNull
    public static JSONObject exportJson() {
        JSONObject root = new JSONObject();
        try {
            root.put("yiligram", SCHEMA_VERSION);
            JSONObject values = new JSONObject();
            for (Key<?> key : KEYS.values()) {
                Object v = get(key);
                values.put(key.name, v instanceof Enum ? ((Enum<?>) v).name() : v);
            }
            root.put("values", values);
        } catch (JSONException ignored) {
        }
        return root;
    }

    /**
     * Imports an export produced by {@link #exportJson()}. Unknown keys and invalid values are
     * skipped and returned, so the UI can tell the user what was not applied.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @NonNull
    public static ArrayList<String> importJson(@NonNull JSONObject root) {
        ArrayList<String> skipped = new ArrayList<>();
        JSONObject values = root.optJSONObject("values");
        if (values == null) {
            skipped.add("values");
            return skipped;
        }
        Map<Key<?>, Object> parsed = new LinkedHashMap<>();
        for (java.util.Iterator<String> it = values.keys(); it.hasNext(); ) {
            String name = it.next();
            Key<?> key = KEYS.get(name);
            if (key == null) {
                skipped.add(name);
                continue;
            }
            try {
                Object raw = values.get(name);
                Object v;
                Class<?> t = key.type;
                if (t == Boolean.class) v = (Boolean) raw;
                else if (t == Integer.class) v = ((Number) raw).intValue();
                else if (t == Float.class) v = ((Number) raw).floatValue();
                else if (t == String.class) v = (String) raw;
                else if (t.isEnum()) v = Enum.valueOf((Class) t, (String) raw);
                else throw new IllegalArgumentException();
                parsed.put(key, v);
            } catch (Exception e) {
                skipped.add(name);
            }
        }
        setAll(parsed);
        return skipped;
    }
}
