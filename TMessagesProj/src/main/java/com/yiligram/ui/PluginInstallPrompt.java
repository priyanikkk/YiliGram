package com.yiligram.ui;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;

import com.yiligram.plugins.PluginApi;
import com.yiligram.plugins.PluginEngine;
import com.yiligram.plugins.Plugins;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.LaunchActivity;

import java.io.File;

/** Tapping a .plugin file in a chat: show its metadata and offer to install (and enable) it. */
public final class PluginInstallPrompt {

    private PluginInstallPrompt() {
    }

    /** @return true if the file was handled (a .plugin file and the engine is running). */
    public static boolean handle(Activity activity, File file, String fileName, Theme.ResourcesProvider resources) {
        PluginEngine engine = Plugins.engine();
        if (engine == null || !engine.isStarted() || fileName == null || !fileName.toLowerCase().endsWith(".plugin")) {
            return false;
        }
        PluginApi.PLUGINS_QUEUE.postRunnable(() -> {
            String json = engine.inspect(file);
            AndroidUtilities.runOnUIThread(() -> show(activity, engine, file, json, resources));
        });
        return true;
    }

    private static void show(Activity activity, PluginEngine engine, File file, String json, Theme.ResourcesProvider resources) {
        JSONObject meta;
        try {
            meta = new JSONObject(json);
        } catch (Exception e) {
            FileLog.e(e);
            return;
        }
        if (!meta.isNull("error")) {
            new AlertDialog.Builder(activity, resources)
                    .setMessage(formatString(R.string.YiliPluginsInstallFailed, meta.optString("error")))
                    .setPositiveButton(getString(R.string.OK), null)
                    .show();
            return;
        }
        String name = meta.optString("name", meta.optString("id"));
        String installed = meta.isNull("installed_version") ? null : meta.optString("installed_version");
        SpannableStringBuilder text = new SpannableStringBuilder();
        text.append(AndroidUtilities.replaceTags("**" + name + "** v" + meta.optString("version")));
        if (!TextUtils.isEmpty(meta.optString("author"))) {
            text.append("\n").append(formatString(R.string.YiliPluginBy, meta.optString("author")));
        }
        if (installed != null) {
            text.append("\n").append(formatString(R.string.YiliPluginUpdateFrom, installed));
        }
        if (!TextUtils.isEmpty(meta.optString("description"))) {
            text.append("\n\n").append(AndroidUtilities.replaceTags(meta.optString("description")));
        }
        text.append("\n\n").append(getString(R.string.YiliPluginsWarning));
        new AlertDialog.Builder(activity, resources)
                .setTitle(getString(R.string.YiliPluginInstallTitle))
                .setMessage(text)
                .setPositiveButton(getString(installed != null ? R.string.YiliPluginUpdate : R.string.YiliPluginInstall),
                        (d, w) -> install(engine, file))
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    private static void install(PluginEngine engine, File file) {
        PluginApi.PLUGINS_QUEUE.postRunnable(() -> {
            String message;
            int icon;
            try {
                String id = engine.install(file);
                String error = engine.setEnabled(id, true);
                message = error == null ? formatString(R.string.YiliPluginsInstalled, id)
                        : formatString(R.string.YiliPluginsInstallFailed, error);
                icon = error == null ? R.raw.contact_check : R.raw.error;
            } catch (Exception e) {
                message = formatString(R.string.YiliPluginsInstallFailed, e.getMessage());
                icon = R.raw.error;
            }
            final String m = message;
            final int ic = icon;
            AndroidUtilities.runOnUIThread(() -> {
                BaseFragment f = LaunchActivity.getSafeLastFragment();
                BulletinFactory bf = f != null ? BulletinFactory.of(f) : BulletinFactory.global();
                if (bf != null) {
                    bf.createSimpleBulletin(ic, m).show();
                }
            });
        });
    }
}
