package com.yiligram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.yiligram.features.YiliHistory;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;
import java.util.List;

/** Deleted messages of one chat (or of all chats with dialogId 0), saved on this device. */
public class YiliDeletedActivity extends UniversalFragment {

    private static final int ID_CLEAR = 1;
    private final long dialogId;

    public YiliDeletedActivity(long dialogId) {
        this.dialogId = dialogId;
    }

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.YiliDeletedMessages);
    }

    private String peerName(long id) {
        MessagesController mc = getMessagesController();
        if (id > 0) {
            TLRPC.User u = mc.getUser(id);
            return u != null ? UserObject.getUserName(u) : String.valueOf(id);
        }
        TLRPC.Chat c = mc.getChat(-id);
        return c != null ? c.title : String.valueOf(id);
    }

    private View entryView(Context context, YiliHistory.Entry e) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(10), AndroidUtilities.dp(21), AndroidUtilities.dp(10));
        box.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, getResourceProvider()));

        TextView title = new TextView(context);
        String who = peerName(e.fromId);
        if (dialogId == 0 && e.fromId != e.dialogId) {
            who = who + " → " + peerName(e.dialogId);
        }
        title.setText(who + " · " + LocaleController.formatDateChat(e.date) + " " + LocaleController.getInstance().getFormatterDay().format(e.date * 1000L));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, getResourceProvider()));
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        box.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView text = new TextView(context);
        text.setText(e.text);
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        text.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, getResourceProvider()));
        text.setTextIsSelectable(true);
        box.addView(text, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));
        return box;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        List<YiliHistory.Entry> list = YiliHistory.deleted(currentAccount, dialogId);
        if (list.isEmpty()) {
            items.add(UItem.asShadow(getString(R.string.YiliDeletedEmpty)));
            return;
        }
        items.add(UItem.asHeader(dialogId != 0 ? peerName(dialogId) : getString(R.string.YiliDeletedAll)));
        for (YiliHistory.Entry e : list) {
            items.add(UItem.asCustom(entryView(getContext(), e)));
        }
        items.add(UItem.asShadow(null));
        items.add(UItem.asButton(ID_CLEAR, R.drawable.msg_delete, getString(R.string.YiliDeletedClear)).red());
        items.add(UItem.asShadow(getString(R.string.YiliDeletedInfo)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_CLEAR) {
            new AlertDialog.Builder(getContext(), getResourceProvider())
                    .setMessage(getString(R.string.YiliDeletedClearConfirm))
                    .setPositiveButton(getString(R.string.Delete), (d, w) -> {
                        YiliHistory.clear(currentAccount, dialogId);
                        listView.adapter.update(true);
                        BulletinFactory.of(this).createSimpleBulletin(R.raw.ic_delete, getString(R.string.YiliDeletedCleared)).show();
                    })
                    .setNegativeButton(getString(R.string.Cancel), null)
                    .makeRed(AlertDialog.BUTTON_POSITIVE)
                    .show();
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
