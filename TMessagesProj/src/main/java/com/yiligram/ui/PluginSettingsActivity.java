package com.yiligram.ui;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.text.InputType;
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
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/** A plugin's page: its create_settings() items rendered as M3 list items, plus delete. */
public class PluginSettingsActivity extends UniversalFragment {

    private static final int ID_DELETE = -2;

    private final String pluginId;
    private final String pluginName;
    private JSONArray settings = new JSONArray();

    public PluginSettingsActivity(String pluginId, String pluginName) {
        this.pluginId = pluginId;
        this.pluginName = pluginName;
    }

    @Override
    public boolean onFragmentCreate() {
        reload();
        return super.onFragmentCreate();
    }

    private void reload() {
        PluginEngine engine = Plugins.engine();
        if (engine == null) {
            return;
        }
        PluginApi.PLUGINS_QUEUE.postRunnable(() -> {
            String json = engine.settingsJson(pluginId);
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    settings = new JSONArray(json);
                } catch (Exception e) {
                    FileLog.e(e);
                }
                if (listView != null) {
                    listView.adapter.update(true);
                }
            });
        });
    }

    @Override
    protected CharSequence getTitle() {
        return pluginName;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (settings.length() == 0) {
            items.add(UItem.asShadow(getString(R.string.YiliPluginNoSettings)));
        }
        for (int i = 0; i < settings.length(); i++) {
            JSONObject o = settings.optJSONObject(i);
            if (o == null) {
                continue;
            }
            String text = o.optString("text", "");
            String sub = o.isNull("subtext") ? null : o.optString("subtext", null);
            switch (o.optString("kind")) {
                case "header":
                    items.add(UItem.asHeader(text));
                    break;
                case "divider":
                    items.add(UItem.asShadow(o.has("text") ? text : null));
                    break;
                case "switch":
                    UItem sw = sub != null ? UItem.asButtonCheck(i, text, sub) : UItem.asCheck(i, text);
                    items.add(sw.setChecked(o.optBoolean("value")));
                    break;
                case "selector": {
                    JSONArray opts = o.optJSONArray("items");
                    int sel = o.optInt("value", 0);
                    String value = opts != null && sel >= 0 && sel < opts.length() ? opts.optString(sel) : "";
                    items.add(UItem.asButton(i, text, value));
                    break;
                }
                case "input":
                case "edit_text":
                    items.add(UItem.asButton(i, o.has("text") ? text : o.optString("hint"),
                            o.isNull("value") ? "" : o.optString("value", "")));
                    break;
                case "text": {
                    UItem t = UItem.asButton(i, text, sub == null ? "" : sub);
                    if (o.optBoolean("red")) {
                        t.red();
                    } else if (o.optBoolean("accent")) {
                        t.accent();
                    }
                    items.add(t);
                    break;
                }
                default:
                    break;
            }
        }
        items.add(UItem.asShadow(null));
        items.add(UItem.asButton(ID_DELETE, R.drawable.msg_delete, getString(R.string.YiliPluginDelete)).red());
        items.add(UItem.asShadow(null));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_DELETE) {
            new AlertDialog.Builder(getContext(), getResourceProvider())
                    .setMessage(formatString(R.string.YiliPluginDeleteConfirm, pluginName))
                    .setPositiveButton(getString(R.string.Delete), (d, w) -> {
                        PluginEngine engine = Plugins.engine();
                        if (engine != null) {
                            PluginApi.PLUGINS_QUEUE.postRunnable(() -> engine.delete(pluginId));
                        }
                        finishFragment();
                    })
                    .setNegativeButton(getString(R.string.Cancel), null)
                    .makeRed(AlertDialog.BUTTON_POSITIVE)
                    .show();
            return;
        }
        JSONObject o = settings.optJSONObject(item.id);
        if (o == null) {
            return;
        }
        switch (o.optString("kind")) {
            case "switch":
                action(item.id, "set", String.valueOf(!o.optBoolean("value")));
                break;
            case "selector": {
                JSONArray opts = o.optJSONArray("items");
                if (opts == null) {
                    return;
                }
                CharSequence[] names = new CharSequence[opts.length()];
                for (int i = 0; i < names.length; i++) {
                    names[i] = opts.optString(i);
                }
                new AlertDialog.Builder(getContext(), getResourceProvider())
                        .setTitle(o.optString("text"))
                        .setItems(names, (d, which) -> action(item.id, "set", String.valueOf(which)))
                        .show();
                break;
            }
            case "input":
            case "edit_text":
                showInput(item.id, o);
                break;
            case "text":
                action(item.id, "click", "null");
                break;
            default:
                break;
        }
    }

    private void showInput(int index, JSONObject o) {
        EditTextBoldCursor edit = new EditTextBoldCursor(getContext());
        edit.setText(o.isNull("value") ? "" : o.optString("value", ""));
        edit.setHint(o.optString("hint", o.optString("text")));
        edit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        edit.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        edit.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack));
        edit.setLineColors(Theme.getColor(Theme.key_dialogInputField), Theme.getColor(Theme.key_dialogInputFieldActivated), Theme.getColor(Theme.key_text_RedBold));
        boolean multiline = o.optBoolean("multiline");
        edit.setInputType(InputType.TYPE_CLASS_TEXT | (multiline ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        edit.setSingleLine(!multiline);
        FrameLayout container = new FrameLayout(getContext());
        container.addView(edit, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 24, 8, 24, 0));
        new AlertDialog.Builder(getContext(), getResourceProvider())
                .setTitle(o.optString("text", o.optString("hint")))
                .setView(container)
                .setPositiveButton(getString(R.string.OK), (d, w) -> action(index, "set", JSONObject.quote(edit.getText().toString())))
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
        AndroidUtilities.runOnUIThread(() -> {
            edit.requestFocus();
            AndroidUtilities.showKeyboard(edit);
        }, 200);
    }

    private void action(int index, String action, String valueJson) {
        PluginEngine engine = Plugins.engine();
        if (engine == null) {
            return;
        }
        PluginApi.PLUGINS_QUEUE.postRunnable(() -> {
            engine.settingsAction(pluginId, index, action, valueJson);
            reload();
        });
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
