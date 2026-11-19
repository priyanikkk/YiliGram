package com.yiligram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.view.View;

import com.yiligram.config.YiliMenus;

import org.telegram.messenger.R;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;
import java.util.Set;

/** YiliGram appearance → Message menu: which items the message context menu shows. */
public class YiliMenusActivity extends UniversalFragment {

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.YiliMenuMessage);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        Set<YiliMenus.MessageItem> hidden = YiliMenus.hiddenMessageItems();
        items.add(UItem.asHeader(getString(R.string.YiliMenuMessageHeader)));
        for (YiliMenus.MessageItem item : YiliMenus.MessageItem.values()) {
            items.add(UItem.asCheck(item.ordinal() + 1, getString(item.title)).setChecked(!hidden.contains(item)));
        }
        items.add(UItem.asShadow(getString(R.string.YiliMenuMessageInfo)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        YiliMenus.MessageItem[] values = YiliMenus.MessageItem.values();
        int index = item.id - 1;
        if (index >= 0 && index < values.length) {
            YiliMenus.setHidden(values[index], !YiliMenus.isHidden(values[index]));
            listView.adapter.update(true);
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
