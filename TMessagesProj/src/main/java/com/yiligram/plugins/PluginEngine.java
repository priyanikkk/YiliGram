package com.yiligram.plugins;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.List;

/**
 * Plugin runtime, implemented by the app module (Python through Chaquopy). TMessagesProj only
 * talks to this interface, so the library builds without the Python toolchain.
 */
public interface PluginEngine {

    /** Hook results: keep going, drop the operation, or use the returned object. */
    int RESULT_DEFAULT = 0;
    int RESULT_CANCEL = 1;
    int RESULT_MODIFIED = 2;

    final class HookResult {
        public final int code;
        @Nullable public final Object value;

        public HookResult(int code, @Nullable Object value) {
            this.code = code;
            this.value = value;
        }

        public static final HookResult DEFAULT = new HookResult(RESULT_DEFAULT, null);
    }

    final class PluginInfo {
        public String id;
        public String name;
        public String description;
        public String author;
        public String version;
        public String icon;
        public boolean enabled;
        public boolean loaded;
        public boolean hasSettings;
        @Nullable public String error;
    }

    /** Starts the runtime and loads enabled plugins (background thread). */
    void start();

    boolean isStarted();

    @NonNull
    List<PluginInfo> getPlugins();

    /** @return error message, or null on success */
    @Nullable
    String setEnabled(@NonNull String id, boolean enabled);

    /** Installs or updates a plugin file. @return the plugin id; throws with a user message on failure */
    @NonNull
    String install(@NonNull File file) throws Exception;

    void delete(@NonNull String id);

    /** Plugin metadata (JSON: id, name, version, author, description, error, installed_version), nothing runs. */
    @NonNull
    String inspect(@NonNull File file);

    /**
     * JSON array describing the plugin's create_settings() items: {kind, text, subtext, key,
     * value, items, hint, accent, red, clickable}. kind: header, divider, switch, selector,
     * input, edit_text, text, custom.
     */
    @NonNull
    String settingsJson(@NonNull String id);

    /** User action on settings item {@code index}: action "set" (value as JSON) or "click". */
    void settingsAction(@NonNull String id, int index, @NonNull String action, @NonNull String valueJson);

    @NonNull
    HookResult onPreRequest(@NonNull String name, int account, @NonNull Object request);

    @NonNull
    HookResult onPostRequest(@NonNull String name, int account, @Nullable Object response, @Nullable Object error);

    @NonNull
    HookResult onUpdate(@NonNull String name, int account, @NonNull Object update);

    @NonNull
    HookResult onUpdates(@NonNull String name, int account, @NonNull Object updates);

    @NonNull
    HookResult onSendMessage(int account, @NonNull Object params);

    void onAppEvent(@NonNull String event);
}
