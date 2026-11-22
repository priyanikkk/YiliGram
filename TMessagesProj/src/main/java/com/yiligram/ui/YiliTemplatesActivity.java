package com.yiligram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.text.InputType;
import android.view.View;
import android.widget.FrameLayout;

import com.yiligram.features.YiliTemplates;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/** YiliGram features → Reply templates: add, edit, delete. */
public class YiliTemplatesActivity extends UniversalFragment {

    private static final int ID_ADD = 1;
    private static final int ID_FIRST = 100;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.YiliTemplates);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        ArrayList<String> templates = YiliTemplates.list();
        items.add(UItem.asHeader(getString(R.string.YiliTemplates)));
        for (int i = 0; i < templates.size(); i++) {
            items.add(UItem.asButton(ID_FIRST + i, templates.get(i)));
        }
        items.add(UItem.asButton(ID_ADD, R.drawable.msg_add, getString(R.string.YiliTemplateAdd)).accent());
        items.add(UItem.asShadow(getString(R.string.YiliTemplatesInfo)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_ADD) {
            edit(-1, "");
        } else if (item.id >= ID_FIRST) {
            int index = item.id - ID_FIRST;
            ArrayList<String> l = YiliTemplates.list();
            if (index < l.size()) edit(index, l.get(index));
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        if (item.id < ID_FIRST) return false;
        int index = item.id - ID_FIRST;
        new AlertDialog.Builder(getContext(), getResourceProvider())
                .setMessage(getString(R.string.YiliTemplateDelete))
                .setPositiveButton(getString(R.string.Delete), (d, w) -> {
                    YiliTemplates.remove(index);
                    listView.adapter.update(true);
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .makeRed(AlertDialog.BUTTON_POSITIVE)
                .show();
        return true;
    }

    private void edit(int index, String text) {
        EditTextBoldCursor field = new EditTextBoldCursor(getContext());
        field.setText(text);
        field.setHint(getString(R.string.YiliTemplateHint));
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        field.setMaxLines(6);
        field.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, getResourceProvider()));
        field.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint, getResourceProvider()));
        field.setLineColors(Theme.getColor(Theme.key_dialogInputField, getResourceProvider()),
                Theme.getColor(Theme.key_dialogInputFieldActivated, getResourceProvider()),
                Theme.getColor(Theme.key_text_RedRegular, getResourceProvider()));
        field.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack, getResourceProvider()));
        FrameLayout box = new FrameLayout(getContext());
        box.addView(field, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 24, 0, 24, 0));
        AlertDialog dialog = new AlertDialog.Builder(getContext(), getResourceProvider())
                .setTitle(getString(index < 0 ? R.string.YiliTemplateAdd : R.string.YiliTemplateEdit))
                .setView(box)
                .setPositiveButton(getString(R.string.Save), (d, w) -> {
                    String value = field.getText().toString().trim();
                    if (value.isEmpty()) return;
                    ArrayList<String> l = YiliTemplates.list();
                    if (index < 0 || index >= l.size()) {
                        l.add(value);
                    } else {
                        l.set(index, value);
                    }
                    YiliTemplates.save(l);
                    listView.adapter.update(true);
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .create();
        showDialog(dialog);
        AndroidUtilities.runOnUIThread(() -> {
            field.requestFocus();
            AndroidUtilities.showKeyboard(field);
        }, 200);
    }
}
