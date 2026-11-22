package com.yiligram.features;

import static org.telegram.messenger.LocaleController.getString;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;

/** User and chat ids in profiles, in the Bot API form (-100… for channels). */
public final class YiliIds {

    private YiliIds() {
    }

    public static String userId(long userId) {
        return String.valueOf(userId);
    }

    public static String chatId(TLRPC.Chat chat) {
        if (chat == null) return "";
        return ChatObject.isChannel(chat) ? "-100" + chat.id : "-" + chat.id;
    }

    public static void copy(BaseFragment fragment, String id) {
        AndroidUtilities.addToClipboard(id);
        BulletinFactory.of(fragment).createCopyBulletin(getString(R.string.YiliIdCopied)).show();
    }
}
