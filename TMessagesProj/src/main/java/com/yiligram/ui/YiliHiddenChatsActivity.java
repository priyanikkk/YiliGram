package com.yiligram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.yiligram.config.YiliConfig;
import com.yiligram.features.YiliHiddenChats;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/** Hidden chats: shown after the screen lock (PIN, pattern or fingerprint) is confirmed. */
public class YiliHiddenChatsActivity extends UniversalFragment {

    private static final int REQUEST_UNLOCK = 0x5A1;
    private static final int ID_LOCK = 1;

    private boolean unlocked;

    @Override
    public boolean onFragmentCreate() {
        unlocked = !YiliConfig.get(YiliConfig.HIDDEN_CHATS_LOCK);
        return super.onFragmentCreate();
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        super.onTransitionAnimationEnd(isOpen, backward);
        if (isOpen && !backward && !unlocked) {
            requestUnlock();
        }
    }

    private void requestUnlock() {
        Activity activity = getParentActivity();
        if (activity == null) return;
        KeyguardManager km = (KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE);
        if (km == null || !km.isKeyguardSecure()) {
            unlocked = true;
            listView.adapter.update(true);
            return;
        }
        Intent intent = km.createConfirmDeviceCredentialIntent(getString(R.string.YiliHiddenChats), getString(R.string.YiliHiddenChatsUnlock));
        if (intent == null) {
            unlocked = true;
            listView.adapter.update(true);
            return;
        }
        activity.startActivityForResult(intent, REQUEST_UNLOCK);
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_UNLOCK) {
            if (resultCode == Activity.RESULT_OK) {
                unlocked = true;
                listView.adapter.update(true);
            } else {
                finishFragment();
            }
        }
    }

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.YiliHiddenChats);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (!unlocked) {
            items.add(UItem.asShadow(getString(R.string.YiliHiddenChatsUnlock)));
            return;
        }
        ArrayList<Long> chats = YiliHiddenChats.list(currentAccount);
        items.add(UItem.asHeader(getString(R.string.YiliHiddenChats)));
        if (chats.isEmpty()) {
            items.add(UItem.asShadow(getString(R.string.YiliHiddenChatsEmpty)));
        } else {
            for (long did : chats) {
                UItem chat = UItem.asAddChat(did);
                chat.selectable = true;
                items.add(chat);
            }
            items.add(UItem.asShadow(getString(R.string.YiliHiddenChatsHint)));
        }
        items.add(UItem.asCheck(ID_LOCK, getString(R.string.YiliHiddenChatsLock)).setChecked(YiliConfig.get(YiliConfig.HIDDEN_CHATS_LOCK)));
        items.add(UItem.asShadow(getString(R.string.YiliHiddenChatsInfo)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_LOCK) {
            YiliConfig.set(YiliConfig.HIDDEN_CHATS_LOCK, !YiliConfig.get(YiliConfig.HIDDEN_CHATS_LOCK));
            listView.adapter.update(true);
            return;
        }
        if (item.viewType == org.telegram.ui.Components.UniversalAdapter.VIEW_TYPE_USER_ADD) {
            long did = item.dialogId;
            Bundle args = new Bundle();
            if (did > 0) args.putLong("user_id", did); else args.putLong("chat_id", -did);
            presentFragment(new ChatActivity(args));
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        if (item.viewType != org.telegram.ui.Components.UniversalAdapter.VIEW_TYPE_USER_ADD) return false;
        long did = item.dialogId;
        new AlertDialog.Builder(getContext(), getResourceProvider())
                .setMessage(getString(R.string.YiliUnhideChatConfirm))
                .setPositiveButton(getString(R.string.YiliUnhideChat), (d, w) -> {
                    YiliHiddenChats.setHidden(currentAccount, did, false);
                    getNotificationCenter().postNotificationName(org.telegram.messenger.NotificationCenter.dialogsNeedReload);
                    listView.adapter.update(true);
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
        return true;
    }
}
