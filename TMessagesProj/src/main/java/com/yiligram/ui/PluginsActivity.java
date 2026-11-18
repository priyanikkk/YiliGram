package com.yiligram.ui;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.view.View;

import com.yiligram.config.YiliConfig;
import com.yiligram.plugins.PluginApi;
import com.yiligram.plugins.PluginEngine;
import com.yiligram.plugins.Plugins;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Settings → Plugins: installed plugins, install from file, engine options. */
public class PluginsActivity extends UniversalFragment {

    private static final int ID_ENGINE = 1;
    private static final int ID_DEV_MODE = 2;
    private static final int ID_INSTALL = 3;
    private static final int ID_PLUGIN_BASE = 1000;
    private static final int REQUEST_INSTALL = 7302;

    private final Runnable onChanged = () -> {
        if (listView != null) {
            listView.adapter.update(true);
        }
    };
    private List<PluginEngine.PluginInfo> plugins = new ArrayList<>();

    @Override
    public boolean onFragmentCreate() {
        Plugins.addListener(onChanged);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        Plugins.removeListener(onChanged);
        super.onFragmentDestroy();
    }

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.YiliPlugins);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        PluginEngine engine = Plugins.engine();
        items.add(UItem.asButtonCheck(ID_ENGINE, getString(R.string.YiliPluginsEngine), getString(R.string.YiliPluginsEngineInfo))
                .setChecked(YiliConfig.get(YiliConfig.PLUGINS_ENABLED)));
        items.add(UItem.asButtonCheck(ID_DEV_MODE, getString(R.string.YiliPluginsDevMode), getString(R.string.YiliPluginsDevModeInfo))
                .setChecked(YiliConfig.get(YiliConfig.PLUGINS_DEV_MODE)));
        items.add(UItem.asShadow(getString(R.string.YiliPluginsWarning)));

        if (engine == null) {
            return;
        }
        items.add(UItem.asButton(ID_INSTALL, R.drawable.msg_addbot, getString(R.string.YiliPluginsInstall)).accent());
        items.add(UItem.asShadow(null));

        if (!engine.isStarted()) {
            items.add(UItem.asShadow(getString(R.string.YiliPluginsStarting)));
            return;
        }
        plugins = engine.getPlugins();
        items.add(UItem.asHeader(getString(R.string.YiliPluginsList)));
        if (plugins.isEmpty()) {
            items.add(UItem.asShadow(getString(R.string.YiliPluginsEmpty)));
            return;
        }
        for (int i = 0; i < plugins.size(); i++) {
            PluginEngine.PluginInfo p = plugins.get(i);
            String sub;
            if (!TextUtils.isEmpty(p.error)) {
                sub = "⚠ " + p.error;
            } else {
                sub = "v" + p.version + (TextUtils.isEmpty(p.author) ? "" : " · " + formatString(R.string.YiliPluginBy, p.author))
                        + (TextUtils.isEmpty(p.description) ? "" : "\n" + p.description);
            }
            items.add(UItem.asButtonCheck(ID_PLUGIN_BASE + i, p.name, sub).setChecked(p.enabled));
        }
        items.add(UItem.asShadow(null));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_ENGINE) {
            YiliConfig.set(YiliConfig.PLUGINS_ENABLED, !YiliConfig.get(YiliConfig.PLUGINS_ENABLED));
            listView.adapter.update(true);
            return;
        }
        if (item.id == ID_DEV_MODE) {
            YiliConfig.set(YiliConfig.PLUGINS_DEV_MODE, !YiliConfig.get(YiliConfig.PLUGINS_DEV_MODE));
            listView.adapter.update(true);
            return;
        }
        if (item.id == ID_INSTALL) {
            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            pick.setType("*/*");
            startActivityForResult(pick, REQUEST_INSTALL);
            return;
        }
        int index = item.id - ID_PLUGIN_BASE;
        if (index < 0 || index >= plugins.size()) {
            return;
        }
        PluginEngine.PluginInfo p = plugins.get(index);
        // The switch area toggles; the rest of the row opens the plugin's page.
        if (x > view.getWidth() - AndroidUtilities.dp(76)) {
            toggle(p);
        } else {
            presentFragment(new PluginSettingsActivity(p.id, p.name));
        }
    }

    private void toggle(PluginEngine.PluginInfo p) {
        PluginEngine engine = Plugins.engine();
        if (engine == null) {
            return;
        }
        boolean enable = !p.enabled;
        PluginApi.PLUGINS_QUEUE.postRunnable(() -> {
            String error = engine.setEnabled(p.id, enable);
            AndroidUtilities.runOnUIThread(() -> {
                if (error != null) {
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.error, error).show();
                }
                listView.adapter.update(true);
            });
        });
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_INSTALL || resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        installFromUri(this, data.getData());
    }

    /** Copies a picked/shared file to the cache and installs it (also used for files from chats). */
    static void installFromUri(UniversalFragment fragment, Uri uri) {
        PluginEngine engine = Plugins.engine();
        Context ctx = fragment.getParentActivity();
        if (engine == null || ctx == null) {
            return;
        }
        PluginApi.PLUGINS_QUEUE.postRunnable(() -> {
            String message;
            int icon;
            try {
                File tmp = new File(ApplicationLoader.applicationContext.getCacheDir(), "plugin_install.tmp");
                try (InputStream in = ctx.getContentResolver().openInputStream(uri);
                     FileOutputStream out = new FileOutputStream(tmp)) {
                    byte[] buf = new byte[16384];
                    int total = 0, n;
                    while (in != null && (n = in.read(buf)) > 0) {
                        total += n;
                        if (total > 2 * 1024 * 1024) {
                            throw new IllegalArgumentException("file is larger than 2 MB");
                        }
                        out.write(buf, 0, n);
                    }
                }
                String id = engine.install(tmp);
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
                message = formatString(R.string.YiliPluginsInstalled, id);
                icon = R.raw.contact_check;
            } catch (Exception e) {
                FileLog.e(e);
                message = formatString(R.string.YiliPluginsInstallFailed, e.getMessage());
                icon = R.raw.error;
            }
            final String m = message;
            final int ic = icon;
            AndroidUtilities.runOnUIThread(() -> BulletinFactory.of(fragment).createSimpleBulletin(ic, m).show());
        });
    }
}
