package com.yiligram.ui;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;

import com.yiligram.plugins.PluginApi;
import com.yiligram.plugins.PluginEngine;
import com.yiligram.plugins.Plugins;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;
import java.util.List;

/**
 * Plugins → Catalog: plugins listed by Git repositories (extera-plugins.json, exteraGram format).
 * Network work runs on the plugins queue; the list is rebuilt from the engine's JSON catalog.
 */
public class PluginCatalogActivity extends UniversalFragment {

    private static final int ID_ADD = 1;
    private static final int ID_REFRESH = 2;
    private static final int ID_REPO_BASE = 1000;
    private static final int ID_PLUGIN_BASE = 100000;

    private static final class Entry {
        String repoUrl, repoName, id, name, version, installed;
        boolean update;
    }

    private JSONArray catalog = new JSONArray();
    private final List<String> repoUrls = new ArrayList<>();
    private final List<Entry> entries = new ArrayList<>();
    private boolean loading;

    @Override
    public boolean onFragmentCreate() {
        load(true, false);
        return super.onFragmentCreate();
    }

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.YiliPluginsCatalog);
    }

    private void load(boolean refresh, boolean force) {
        PluginEngine engine = Plugins.engine();
        if (engine == null) {
            return;
        }
        loading = true;
        PluginApi.PLUGINS_QUEUE.postRunnable(() -> {
            JSONArray result;
            try {
                result = new JSONArray(engine.reposCatalog(refresh, force));
            } catch (Exception e) {
                FileLog.e(e);
                result = new JSONArray();
            }
            final JSONArray r = result;
            AndroidUtilities.runOnUIThread(() -> {
                catalog = r;
                loading = false;
                if (listView != null) {
                    listView.adapter.update(true);
                }
            });
        });
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        repoUrls.clear();
        entries.clear();
        items.add(UItem.asButton(ID_ADD, R.drawable.msg_add, getString(R.string.YiliReposAdd)).accent());
        items.add(UItem.asButton(ID_REFRESH, R.drawable.msg_retry, getString(R.string.YiliReposRefresh)));
        items.add(UItem.asShadow(getString(R.string.YiliPluginsWarning)));
        if (catalog.length() == 0) {
            items.add(UItem.asShadow(loading ? getString(R.string.Loading) : getString(R.string.YiliReposEmpty)));
            return;
        }
        for (int i = 0; i < catalog.length(); i++) {
            JSONObject repo = catalog.optJSONObject(i);
            if (repo == null) {
                continue;
            }
            String url = repo.optString("url");
            String name = repo.optString("name", url);
            repoUrls.add(url);
            items.add(UItem.asHeader(ID_REPO_BASE + repoUrls.size() - 1, name));
            JSONArray plugins = repo.optJSONArray("plugins");
            for (int j = 0; plugins != null && j < plugins.length(); j++) {
                JSONObject p = plugins.optJSONObject(j);
                if (p == null) {
                    continue;
                }
                Entry e = new Entry();
                e.repoUrl = url;
                e.repoName = name;
                e.id = p.optString("id");
                e.name = p.optString("name", e.id);
                e.version = p.optString("version");
                e.installed = p.isNull("installed") ? null : p.optString("installed");
                e.update = p.optBoolean("update");
                entries.add(e);
                String status = e.installed == null ? getString(R.string.YiliRepoInstall)
                        : e.update ? formatString(R.string.YiliRepoUpdate, e.version) : getString(R.string.YiliRepoInstalled);
                UItem row = UItem.asButton(ID_PLUGIN_BASE + entries.size() - 1, e.name + "  " + e.version, status);
                if (e.installed == null || e.update) {
                    row.accent();
                }
                items.add(row);
            }
            StringBuilder footer = new StringBuilder();
            if (!repo.isNull("error")) {
                footer.append(formatString(R.string.YiliReposError, repo.optString("error")));
            } else if (!TextUtils.isEmpty(repo.optString("description"))) {
                footer.append(repo.optString("description"));
            }
            footer.append(footer.length() > 0 ? "\n" : "").append(url);
            items.add(UItem.asShadow(footer));
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_ADD) {
            askRepositoryUrl();
        } else if (item.id == ID_REFRESH) {
            load(true, true);
        } else if (item.id >= ID_PLUGIN_BASE) {
            int index = item.id - ID_PLUGIN_BASE;
            if (index < entries.size()) {
                Entry e = entries.get(index);
                if (e.installed == null || e.update) {
                    confirmInstall(e);
                }
            }
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        if (item.id >= ID_REPO_BASE && item.id < ID_PLUGIN_BASE) {
            int index = item.id - ID_REPO_BASE;
            if (index < repoUrls.size()) {
                confirmRemove(repoUrls.get(index));
                return true;
            }
        }
        return false;
    }

    private void askRepositoryUrl() {
        Context ctx = getParentActivity();
        if (ctx == null) {
            return;
        }
        EditTextBoldCursor input = new EditTextBoldCursor(ctx);
        input.setTextSize(16);
        input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider));
        input.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint, resourceProvider));
        input.setHint(getString(R.string.YiliReposAddHint));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setSingleLine(true);
        input.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider));
        input.setBackground(Theme.createEditTextDrawable(ctx, true));
        FrameLayout box = new FrameLayout(ctx);
        box.addView(input, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 24, 8, 24, 0));
        new AlertDialog.Builder(ctx, resourceProvider)
                .setTitle(getString(R.string.YiliReposAdd))
                .setView(box)
                .setPositiveButton(getString(R.string.Add), (d, w) -> addRepository(input.getText().toString().trim()))
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
        input.requestFocus();
        AndroidUtilities.showKeyboard(input);
    }

    private void addRepository(String url) {
        PluginEngine engine = Plugins.engine();
        if (engine == null || url.isEmpty()) {
            return;
        }
        PluginApi.PLUGINS_QUEUE.postRunnable(() -> {
            String error = engine.reposAdd(url);
            AndroidUtilities.runOnUIThread(() -> {
                if (error != null) {
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.error, formatString(R.string.YiliReposError, error)).show();
                } else {
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, getString(R.string.YiliReposAdded)).show();
                }
                load(false, false);
            });
        });
    }

    private void confirmRemove(String url) {
        if (getParentActivity() == null) {
            return;
        }
        new AlertDialog.Builder(getParentActivity(), resourceProvider)
                .setTitle(getString(R.string.YiliReposRemove))
                .setMessage(formatString(R.string.YiliReposRemoveConfirm, url))
                .setPositiveButton(getString(R.string.Delete), (d, w) -> {
                    PluginEngine engine = Plugins.engine();
                    if (engine != null) {
                        PluginApi.PLUGINS_QUEUE.postRunnable(() -> {
                            engine.reposRemove(url);
                            AndroidUtilities.runOnUIThread(() -> load(false, false));
                        });
                    }
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    private void confirmInstall(Entry e) {
        if (getParentActivity() == null) {
            return;
        }
        new AlertDialog.Builder(getParentActivity(), resourceProvider)
                .setTitle(e.name)
                .setMessage(formatString(R.string.YiliRepoInstallConfirm, e.name, e.version, e.repoName))
                .setPositiveButton(e.installed == null ? getString(R.string.YiliRepoInstall) : formatString(R.string.YiliRepoUpdate, e.version),
                        (d, w) -> install(e))
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    private void install(Entry e) {
        PluginEngine engine = Plugins.engine();
        if (engine == null) {
            return;
        }
        PluginApi.PLUGINS_QUEUE.postRunnable(() -> {
            String message;
            int icon;
            try {
                String id = engine.reposInstall(e.repoUrl, e.id);
                message = formatString(R.string.YiliPluginsInstalled, id);
                icon = R.raw.contact_check;
            } catch (Exception ex) {
                FileLog.e(ex);
                message = formatString(R.string.YiliPluginsInstallFailed, ex.getMessage());
                icon = R.raw.error;
            }
            final String m = message;
            final int ic = icon;
            AndroidUtilities.runOnUIThread(() -> {
                BulletinFactory.of(this).createSimpleBulletin(ic, m).show();
                load(false, false);
            });
        });
    }
}
