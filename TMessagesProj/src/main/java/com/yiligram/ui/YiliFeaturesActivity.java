package com.yiligram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.view.View;

import com.yiligram.config.YiliConfig;

import org.telegram.messenger.R;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/** Settings → YiliGram features: chats, privacy, messages, voice. */
public class YiliFeaturesActivity extends UniversalFragment {

    private static final class Toggle {
        final int id;
        final YiliConfig.Key<Boolean> key;
        final int title;

        Toggle(int id, YiliConfig.Key<Boolean> key, int title) {
            this.id = id;
            this.key = key;
            this.title = title;
        }
    }

    private static final int[] HAPTIC_TITLES = {
            R.string.YiliHapticsOff, R.string.YiliHapticsSystem, R.string.YiliHapticsLight,
            R.string.YiliHapticsMedium, R.string.YiliHapticsStrong
    };

    private final ArrayList<Toggle> toggles = new ArrayList<>();

    private void check(ArrayList<UItem> items, int id, YiliConfig.Key<Boolean> key, int title) {
        toggles.add(new Toggle(id, key, title));
        items.add(UItem.asCheck(id, getString(title)).setChecked(YiliConfig.get(key)));
    }

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.YiliFeatures);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        toggles.clear();
        items.add(UItem.asHeader(getString(R.string.YiliFeaturesTheme)));
        items.add(UItem.asButton(100, R.drawable.msg_theme, getString(R.string.YiliThemeSchedule)));
        items.add(UItem.asShadow(getString(R.string.YiliThemeScheduleInfo)));

        items.add(UItem.asHeader(getString(R.string.YiliFeaturesHaptics)));
        items.add(UItem.asButton(101, R.drawable.msg_customize, getString(R.string.YiliHaptics),
                getString(HAPTIC_TITLES[YiliConfig.get(YiliConfig.HAPTICS).ordinal()])));
        items.add(UItem.asShadow(getString(R.string.YiliHapticsInfo)));

        items.add(UItem.asHeader(getString(R.string.YiliHiddenChats)));
        items.add(UItem.asButton(106, R.drawable.msg_archive_hide, getString(R.string.YiliHiddenChats)));
        items.add(UItem.asShadow(getString(R.string.YiliHiddenChatsHowTo)));

        items.add(UItem.asHeader(getString(R.string.YiliGhost)));
        check(items, 20, YiliConfig.GHOST_MODE, R.string.YiliGhostEnabled);
        check(items, 21, YiliConfig.GHOST_READ, R.string.YiliGhostRead);
        check(items, 22, YiliConfig.GHOST_TYPING, R.string.YiliGhostTyping);
        check(items, 23, YiliConfig.GHOST_ONLINE, R.string.YiliGhostOnline);
        check(items, 24, YiliConfig.GHOST_STORIES, R.string.YiliGhostStories);
        check(items, 25, YiliConfig.GHOST_BUTTON, R.string.YiliGhostButton);
        items.add(UItem.asShadow(YiliConfig.get(YiliConfig.GHOST_MODE)
                ? org.telegram.messenger.LocaleController.formatString(R.string.YiliGhostCounter, com.yiligram.features.YiliGhost.hidden.get()) + "\n\n" + getString(R.string.YiliGhostInfo)
                : getString(R.string.YiliGhostInfo)));

        items.add(UItem.asHeader(getString(R.string.YiliFeaturesHistory)));
        check(items, 40, YiliConfig.SAVE_DELETED, R.string.YiliSaveDeleted);
        check(items, 41, YiliConfig.SAVE_EDITS, R.string.YiliSaveEdits);
        items.add(UItem.asButton(105, R.drawable.msg_delete, getString(R.string.YiliDeletedAll)));
        items.add(UItem.asShadow(getString(R.string.YiliHistoryInfo)));

        items.add(UItem.asHeader(getString(R.string.YiliTemplates)));
        items.add(UItem.asButton(104, R.drawable.msg_text_outlined, getString(R.string.YiliTemplatesManage)));
        check(items, 30, YiliConfig.TEMPLATES_BUTTON, R.string.YiliTemplatesButton);
        items.add(UItem.asShadow(getString(R.string.YiliTemplatesInfo)));

        items.add(UItem.asHeader(getString(R.string.YiliBackup)));
        items.add(UItem.asButton(102, R.drawable.msg_saved, getString(R.string.YiliBackupSave)));
        items.add(UItem.asButton(103, R.drawable.msg_reset, getString(R.string.YiliBackupRestore)));
        items.add(UItem.asShadow(getString(R.string.YiliBackupInfo)));

        items.add(UItem.asHeader(getString(R.string.YiliFeaturesProfiles)));
        check(items, 1, YiliConfig.SHOW_IDS, R.string.YiliShowIds);
        items.add(UItem.asShadow(getString(R.string.YiliShowIdsInfo)));

        items.add(UItem.asHeader(getString(R.string.YiliFeaturesVoice)));
        check(items, 10, YiliConfig.VOICE_SKIP_SILENCE, R.string.YiliVoiceSkipSilence);
        check(items, 11, YiliConfig.VOICE_RESUME, R.string.YiliVoiceResume);
        check(items, 12, YiliConfig.VOICE_SPEED_3X, R.string.YiliVoiceSpeed3x);
        items.add(UItem.asShadow(null));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == 101) {
            com.yiligram.features.YiliHaptics.Strength[] values = com.yiligram.features.YiliHaptics.Strength.values();
            CharSequence[] names = new CharSequence[values.length];
            for (int i = 0; i < values.length; i++) {
                names[i] = getString(HAPTIC_TITLES[i]);
            }
            new org.telegram.ui.ActionBar.AlertDialog.Builder(getContext(), getResourceProvider())
                    .setTitle(getString(R.string.YiliHaptics))
                    .setItems(names, (d, which) -> {
                        YiliConfig.set(YiliConfig.HAPTICS, values[which]);
                        com.yiligram.features.YiliHaptics.click(view);
                        listView.adapter.update(true);
                    })
                    .show();
            return;
        }
        if (item.id == 106) {
            presentFragment(new YiliHiddenChatsActivity());
            return;
        }
        if (item.id == 105) {
            presentFragment(new YiliDeletedActivity(0));
            return;
        }
        if (item.id == 104) {
            presentFragment(new YiliTemplatesActivity());
            return;
        }
        if (item.id == 102) {
            com.yiligram.features.YiliBackup.save(this);
            return;
        }
        if (item.id == 103) {
            com.yiligram.features.YiliBackup.restore(this, () -> listView.adapter.update(true));
            return;
        }
        if (item.id == 100) {
            presentFragment(new org.telegram.ui.ThemeActivity(org.telegram.ui.ThemeActivity.THEME_TYPE_NIGHT));
            return;
        }
        for (Toggle t : toggles) {
            if (t.id == item.id) {
                if (t.key == YiliConfig.GHOST_MODE) {
                    com.yiligram.features.YiliGhost.setEnabled(!YiliConfig.get(t.key));
                    listView.adapter.update(true);
                    return;
                }
                YiliConfig.set(t.key, !YiliConfig.get(t.key));
                listView.adapter.update(true);
                return;
            }
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
