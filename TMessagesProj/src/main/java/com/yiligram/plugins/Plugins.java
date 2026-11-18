package com.yiligram.plugins;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.FileLog;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Entry point used by Telegram code. Every hook site first asks a cheap question
 * ({@link #wantsRequest(String)} etc.) and only then crosses into Python, so the cost without
 * plugins is a volatile read and a set lookup.
 */
public final class Plugins {

    @Nullable
    private static volatile PluginEngine engine;
    private static volatile Set<String> requestHooks = Collections.emptySet();
    private static volatile String[] substringHooks = new String[0];
    private static volatile boolean sendMessageHooks;

    private Plugins() {
    }

    private static final java.util.concurrent.CopyOnWriteArrayList<Runnable> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void setEngine(@Nullable PluginEngine e) {
        engine = e;
    }

    /** UI listeners: called on the main thread when the plugin list or a plugin's state changes. */
    public static void addListener(@NonNull Runnable r) {
        listeners.addIfAbsent(r);
    }

    public static void removeListener(@NonNull Runnable r) {
        listeners.remove(r);
    }

    public static void notifyChanged() {
        org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
            for (Runnable r : listeners) {
                r.run();
            }
        });
    }

    @Nullable
    public static PluginEngine engine() {
        return engine;
    }

    /** Called by the engine whenever plugins register or drop hooks. */
    public static void updateHooks(@NonNull Set<String> names, @NonNull String[] substrings, boolean sendMessage) {
        requestHooks = new HashSet<>(names);
        substringHooks = substrings;
        sendMessageHooks = sendMessage;
    }

    /** True if some plugin hooks the TL object with this class name. */
    public static boolean wants(@NonNull String name) {
        if (engine == null) {
            return false;
        }
        if (requestHooks.contains(name)) {
            return true;
        }
        for (String s : substringHooks) {
            if (name.contains(s)) {
                return true;
            }
        }
        return false;
    }

    public static boolean wantsSendMessage() {
        return engine != null && sendMessageHooks;
    }

    /** TL name as used by exteraGram plugins: the class simple name, e.g. TL_messages_setTyping. */
    @NonNull
    public static String nameOf(@NonNull Object tlObject) {
        return tlObject.getClass().getSimpleName();
    }

    @NonNull
    public static PluginEngine.HookResult preRequest(@NonNull Object request, int account) {
        PluginEngine e = engine;
        if (e == null) {
            return PluginEngine.HookResult.DEFAULT;
        }
        String name = nameOf(request);
        if (!wants(name)) {
            return PluginEngine.HookResult.DEFAULT;
        }
        try {
            return e.onPreRequest(name, account, request);
        } catch (Throwable t) {
            FileLog.e(t);
            return PluginEngine.HookResult.DEFAULT;
        }
    }

    @NonNull
    public static PluginEngine.HookResult postRequest(@NonNull String name, int account, @Nullable Object response, @Nullable Object error) {
        PluginEngine e = engine;
        if (e == null || !wants(name)) {
            return PluginEngine.HookResult.DEFAULT;
        }
        try {
            return e.onPostRequest(name, account, response, error);
        } catch (Throwable t) {
            FileLog.e(t);
            return PluginEngine.HookResult.DEFAULT;
        }
    }

    @NonNull
    public static PluginEngine.HookResult update(@NonNull Object update, int account) {
        PluginEngine e = engine;
        if (e == null) {
            return PluginEngine.HookResult.DEFAULT;
        }
        String name = nameOf(update);
        if (!wants(name)) {
            return PluginEngine.HookResult.DEFAULT;
        }
        try {
            return e.onUpdate(name, account, update);
        } catch (Throwable t) {
            FileLog.e(t);
            return PluginEngine.HookResult.DEFAULT;
        }
    }

    @NonNull
    public static PluginEngine.HookResult updates(@NonNull Object updates, int account) {
        PluginEngine e = engine;
        if (e == null) {
            return PluginEngine.HookResult.DEFAULT;
        }
        String name = nameOf(updates);
        if (!wants(name)) {
            return PluginEngine.HookResult.DEFAULT;
        }
        try {
            return e.onUpdates(name, account, updates);
        } catch (Throwable t) {
            FileLog.e(t);
            return PluginEngine.HookResult.DEFAULT;
        }
    }

    @NonNull
    public static PluginEngine.HookResult sendMessage(@NonNull Object params, int account) {
        PluginEngine e = engine;
        if (e == null || !sendMessageHooks) {
            return PluginEngine.HookResult.DEFAULT;
        }
        try {
            return e.onSendMessage(account, params);
        } catch (Throwable t) {
            FileLog.e(t);
            return PluginEngine.HookResult.DEFAULT;
        }
    }
}
