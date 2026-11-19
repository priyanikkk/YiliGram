package com.yiligram.plugins.python;

import android.content.Context;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.yiligram.config.YiliConfig;
import com.yiligram.plugins.PluginApi;
import com.yiligram.plugins.PluginEngine;
import com.yiligram.plugins.Plugins;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * {@link PluginEngine} backed by CPython 3.11 (Chaquopy). The Python side lives in
 * src/main/python (yili_engine + the exteraGram-compatible SDK modules).
 */
@Keep
public final class PythonPluginEngine implements PluginEngine {

    private final Context context;
    private volatile PyObject engine;
    private volatile boolean started;

    public PythonPluginEngine(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public void start() {
        PluginApi.PLUGINS_QUEUE.postRunnable(() -> {
            try {
                if (!Python.isStarted()) {
                    Python.start(new AndroidPlatform(context));
                }
                PyObject module = Python.getInstance().getModule("yili_engine");
                File dir = new File(context.getFilesDir(), "plugins");
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
                engine = module;
                String report = module.callAttr("init", dir.getAbsolutePath(), this,
                        YiliConfig.get(YiliConfig.PLUGINS_DEV_MODE)).toString();
                started = true;
                onHooksChanged();
                Plugins.notifyChanged();
                FileLog.d("YiliGram plugins started: " + report);
            } catch (Throwable t) {
                FileLog.e(t);
            }
        });
    }

    @Override
    public boolean isStarted() {
        return started;
    }

    // ---------------------------------------------------------------- called from Python

    @Keep
    public void onHooksChanged() {
        PyObject e = engine;
        if (e == null) {
            return;
        }
        try {
            JSONObject o = new JSONObject(e.callAttr("hook_summary").toString());
            Set<String> names = new HashSet<>();
            JSONArray a = o.getJSONArray("names");
            for (int i = 0; i < a.length(); i++) {
                names.add(a.getString(i));
            }
            JSONArray subs = o.getJSONArray("substrings");
            String[] s = new String[subs.length()];
            for (int i = 0; i < s.length; i++) {
                s[i] = subs.getString(i);
            }
            Plugins.updateHooks(names, s, o.getBoolean("send_message"));
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    @Keep
    public void onPluginsChanged() {
        Plugins.notifyChanged();
    }

    // ---------------------------------------------------------------- management

    @NonNull
    @Override
    public List<PluginInfo> getPlugins() {
        PyObject e = engine;
        if (e == null) {
            return Collections.emptyList();
        }
        List<PluginInfo> list = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(e.callAttr("list_plugins").toString());
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                PluginInfo p = new PluginInfo();
                p.id = o.getString("id");
                p.name = o.optString("name", p.id);
                p.description = o.optString("description", "");
                p.author = o.optString("author", "");
                p.version = o.optString("version", "");
                p.icon = o.isNull("icon") ? null : o.optString("icon");
                p.enabled = o.optBoolean("enabled");
                p.loaded = o.optBoolean("loaded");
                p.hasSettings = o.optBoolean("has_settings");
                p.error = o.isNull("error") ? null : o.optString("error");
                p.warning = o.isNull("warning") ? null : o.optString("warning");
                list.add(p);
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
        return list;
    }

    @Nullable
    @Override
    public String setEnabled(@NonNull String id, boolean enabled) {
        PyObject e = engine;
        if (e == null) {
            return "Plugin engine is not running";
        }
        PyObject r = e.callAttr("set_enabled", id, enabled);
        Plugins.notifyChanged();
        return r == null ? null : r.toString();
    }

    @NonNull
    @Override
    public String install(@NonNull File file) throws Exception {
        PyObject e = engine;
        if (e == null) {
            throw new IllegalStateException("Plugin engine is not running");
        }
        JSONObject o = new JSONObject(e.callAttr("install", file.getAbsolutePath()).toString());
        if (!o.isNull("error")) {
            throw new IllegalArgumentException(o.getString("error"));
        }
        Plugins.notifyChanged();
        return o.getString("id");
    }

    @Override
    public void delete(@NonNull String id) {
        PyObject e = engine;
        if (e != null) {
            e.callAttr("delete", id);
            Plugins.notifyChanged();
        }
    }

    @NonNull
    @Override
    public String inspect(@NonNull File file) {
        PyObject e = engine;
        return e == null ? "{\"error\":\"Plugin engine is not running\"}" : e.callAttr("inspect", file.getAbsolutePath()).toString();
    }

    @NonNull
    @Override
    public String settingsJson(@NonNull String id) {
        PyObject e = engine;
        return e == null ? "[]" : e.callAttr("settings_json", id).toString();
    }

    @Override
    public void settingsAction(@NonNull String id, int index, @NonNull String action, @NonNull String valueJson) {
        PyObject e = engine;
        if (e != null) {
            e.callAttr("settings_action", id, index, action, valueJson);
        }
    }

    // ---------------------------------------------------------------- hooks

    @NonNull
    private static HookResult toResult(@Nullable PyObject r) {
        if (r == null) {
            return HookResult.DEFAULT;
        }
        List<PyObject> l = r.asList();
        int code = l.get(0).toInt();
        if (code == RESULT_DEFAULT) {
            return HookResult.DEFAULT;
        }
        PyObject v = l.get(1);
        return new HookResult(code, v == null ? null : v.toJava(Object.class));
    }

    @NonNull
    @Override
    public HookResult onPreRequest(@NonNull String name, int account, @NonNull Object request) {
        PyObject e = engine;
        return e == null ? HookResult.DEFAULT : toResult(e.callAttr("dispatch_pre_request", name, account, request));
    }

    @NonNull
    @Override
    public HookResult onPostRequest(@NonNull String name, int account, @Nullable Object response, @Nullable Object error) {
        PyObject e = engine;
        return e == null ? HookResult.DEFAULT : toResult(e.callAttr("dispatch_post_request", name, account, response, error));
    }

    @NonNull
    @Override
    public HookResult onUpdate(@NonNull String name, int account, @NonNull Object update) {
        PyObject e = engine;
        return e == null ? HookResult.DEFAULT : toResult(e.callAttr("dispatch_update", name, account, update));
    }

    @NonNull
    @Override
    public HookResult onUpdates(@NonNull String name, int account, @NonNull Object updates) {
        PyObject e = engine;
        return e == null ? HookResult.DEFAULT : toResult(e.callAttr("dispatch_updates", name, account, updates));
    }

    @NonNull
    @Override
    public HookResult onSendMessage(int account, @NonNull Object params) {
        PyObject e = engine;
        return e == null ? HookResult.DEFAULT : toResult(e.callAttr("dispatch_send_message", account, params));
    }

    @Override
    public void onAppEvent(@NonNull String event) {
        PyObject e = engine;
        if (e != null) {
            PluginApi.PLUGINS_QUEUE.postRunnable(() -> e.callAttr("app_event", event));
        }
    }

    @Override
    public boolean onIntent(boolean before, @NonNull android.content.Intent intent) {
        PyObject e = engine;
        if (e == null) {
            return false;
        }
        PyObject r = e.callAttr("dispatch_intent", before, intent);
        return r != null && r.toBoolean();
    }

    @NonNull
    @Override
    public java.util.List<MenuItem> getMenuItems(@NonNull String menuType) {
        PyObject e = engine;
        java.util.ArrayList<MenuItem> out = new java.util.ArrayList<>();
        if (e == null) {
            return out;
        }
        try {
            JSONArray a = new JSONArray(e.callAttr("menu_items_json", menuType).toString());
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new MenuItem(o.getString("id"), o.getString("text"),
                        o.isNull("subtext") ? null : o.optString("subtext"),
                        o.isNull("icon") ? null : o.optString("icon")));
            }
        } catch (Exception ex) {
            FileLog.e(ex);
        }
        return out;
    }

    @Override
    public void clickMenuItem(@NonNull String itemId, @NonNull java.util.Map<String, Object> context) {
        PyObject e = engine;
        if (e != null) {
            e.callAttr("click_menu_item_java", itemId, context);
        }
    }

    @NonNull
    @Override
    public String reposCatalog(boolean refresh, boolean force) {
        PyObject e = engine;
        return e == null ? "[]" : e.callAttr("repos_catalog", refresh, force).toString();
    }

    @Nullable
    @Override
    public String reposAdd(@NonNull String url) {
        PyObject e = engine;
        if (e == null) {
            return "plugin engine is not running";
        }
        try {
            JSONObject o = new JSONObject(e.callAttr("repos_add", url).toString());
            return o.isNull("error") ? null : o.getString("error");
        } catch (Exception ex) {
            return ex.getMessage();
        }
    }

    @Override
    public void reposRemove(@NonNull String url) {
        PyObject e = engine;
        if (e != null) {
            e.callAttr("repos_remove", url);
        }
    }

    @NonNull
    @Override
    public String reposInstall(@NonNull String url, @NonNull String pluginId) throws Exception {
        PyObject e = engine;
        if (e == null) {
            throw new IllegalStateException("plugin engine is not running");
        }
        JSONObject o = new JSONObject(e.callAttr("repos_install", url, pluginId).toString());
        if (!o.isNull("error")) {
            throw new IllegalArgumentException(o.getString("error"));
        }
        return o.getString("id");
    }
}
