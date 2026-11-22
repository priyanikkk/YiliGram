package com.yiligram.features;

import static org.telegram.messenger.LocaleController.getString;

import com.yiligram.config.YiliConfig;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;

/** YiliGram settings backup: a message tagged #yiligram_settings in Saved Messages. */
public final class YiliBackup {

    public static final String TAG = "#yiligram_settings";

    private YiliBackup() {
    }

    public static void save(BaseFragment fragment) {
        int account = fragment.getCurrentAccount();
        long self = UserConfig.getInstance(account).getClientUserId();
        String text = TAG + "\n" + YiliConfig.exportJson().toString();
        SendMessagesHelper.getInstance(account).sendMessage(SendMessagesHelper.SendMessageParams.of(text, self));
        BulletinFactory.of(fragment).createSimpleBulletin(R.raw.saved_messages, getString(R.string.YiliBackupSaved)).show();
    }

    public static void restore(BaseFragment fragment, Runnable done) {
        int account = fragment.getCurrentAccount();
        long self = UserConfig.getInstance(account).getClientUserId();
        TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
        req.peer = MessagesController.getInstance(account).getInputPeer(self);
        req.q = TAG;
        req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
        req.limit = 10;
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            TLRPC.Message newest = null;
            if (response instanceof TLRPC.messages_Messages) {
                for (TLRPC.Message m : ((TLRPC.messages_Messages) response).messages) {
                    if (m.message != null && m.message.startsWith(TAG) && (newest == null || m.date > newest.date)) {
                        newest = m;
                    }
                }
            }
            if (newest == null) {
                BulletinFactory.of(fragment).createErrorBulletin(getString(R.string.YiliBackupNotFound)).show();
                return;
            }
            try {
                YiliConfig.importJson(new JSONObject(newest.message.substring(TAG.length()).trim()));
                BulletinFactory.of(fragment).createSimpleBulletin(R.raw.contact_check, getString(R.string.YiliBackupRestored)).show();
                if (done != null) done.run();
            } catch (Exception e) {
                BulletinFactory.of(fragment).createErrorBulletin(getString(R.string.YiliBackupNotFound)).show();
            }
        }));
    }
}
