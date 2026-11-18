package com.yiligram.plugins;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DispatchQueue2;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;

/**
 * Small, stable Java surface for the Python plugin SDK (client_utils, android_utils, ui.*).
 * Telegram's own signatures change between releases; keeping plugin-facing calls here means one
 * place to adapt after an upstream merge.
 */
@Keep
public final class PluginApi {

    public static final DispatchQueue2 PLUGINS_QUEUE = new DispatchQueue2("pluginsQueue");

    private PluginApi() {
    }

    // ---------------------------------------------------------------- threads

    @Nullable
    public static DispatchQueue2 queue(String name) {
        switch (name) {
            case "stageQueue": return Utilities.stageQueue;
            case "globalQueue": return Utilities.globalQueue;
            case "cacheClearQueue": return Utilities.cacheClearQueue;
            case "searchQueue": return Utilities.searchQueue;
            case "phoneBookQueue": return Utilities.phoneBookQueue;
            case "themeQueue": return Utilities.themeQueue;
            case "externalNetworkQueue": return Utilities.externalNetworkQueue;
            case "pluginsQueue": return PLUGINS_QUEUE;
            default: return null;
        }
    }

    public static void runOnUiThread(Runnable runnable, long delayMs) {
        AndroidUtilities.runOnUIThread(runnable, Math.max(0, delayMs));
    }

    // ---------------------------------------------------------------- accounts & screens

    public static int selectedAccount() {
        return UserConfig.selectedAccount;
    }

    @Nullable
    public static BaseFragment lastFragment() {
        return LaunchActivity.getSafeLastFragment();
    }

    @Nullable
    public static Context context() {
        BaseFragment f = lastFragment();
        if (f != null && f.getParentActivity() != null) {
            return f.getParentActivity();
        }
        return LaunchActivity.instance != null ? LaunchActivity.instance : ApplicationLoader.applicationContext;
    }

    // ---------------------------------------------------------------- sending

    public static void sendText(int account, long peer, String text, @Nullable ArrayList<TLRPC.MessageEntity> entities,
                                @Nullable MessageObject replyTo, boolean notify, int scheduleDate) {
        SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(
                text, peer, replyTo, null, null, true, entities, null, null, notify, scheduleDate, 0, null, false);
        AndroidUtilities.runOnUIThread(() -> SendMessagesHelper.getInstance(account).sendMessage(params));
    }

    public static void sendMessageParams(int account, SendMessagesHelper.SendMessageParams params) {
        AndroidUtilities.runOnUIThread(() -> SendMessagesHelper.getInstance(account).sendMessage(params));
    }

    public static void sendPhoto(int account, long peer, String path, @Nullable String caption,
                                 @Nullable ArrayList<TLRPC.MessageEntity> entities, @Nullable MessageObject replyTo) {
        AndroidUtilities.runOnUIThread(() -> SendMessagesHelper.prepareSendingPhoto(
                AccountInstance.getInstance(account), path, null, peer, replyTo, null, null,
                caption, entities, null, null, 0, null, true, 0, 0, null));
    }

    public static void sendDocument(int account, long peer, String path, @Nullable String caption,
                                    @Nullable MessageObject replyTo) {
        AndroidUtilities.runOnUIThread(() -> SendMessagesHelper.prepareSendingDocument(
                AccountInstance.getInstance(account), path, path, null, caption, null, peer, replyTo, null,
                null, null, null, true, 0, null, null, false));
    }

    // ---------------------------------------------------------------- UI helpers

    public static void copyToClipboard(String text) {
        AndroidUtilities.runOnUIThread(() -> {
            ClipboardManager cm = (ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("label", text));
            BulletinFactory f = bulletinFactory(null);
            if (f != null) {
                f.createCopyBulletin(org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.TextCopied)).show();
            }
        });
    }

    /** Bulletin factory for a fragment, falling back to the visible screen or a global one. */
    @Nullable
    public static BulletinFactory bulletinFactory(@Nullable BaseFragment fragment) {
        BaseFragment f = fragment != null ? fragment : lastFragment();
        if (f != null) {
            return BulletinFactory.of(f);
        }
        return BulletinFactory.global();
    }

    public static void showBulletin(@Nullable BaseFragment fragment, int iconRes, String title,
                                    @Nullable String subtitle, @Nullable String button,
                                    @Nullable Runnable onButton, int durationMs) {
        AndroidUtilities.runOnUIThread(() -> {
            BulletinFactory f = bulletinFactory(fragment);
            if (f == null) {
                return;
            }
            Bulletin b;
            if (button != null) {
                b = f.createSimpleBulletin(iconRes, title, button, onButton == null ? () -> {} : onButton);
            } else if (subtitle != null) {
                b = f.createSimpleBulletin(iconRes, title, subtitle);
            } else {
                b = f.createSimpleBulletin(iconRes, title);
            }
            b.setDuration(durationMs).show();
        });
    }
}
