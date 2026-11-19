package com.yiligram.update;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;

import java.io.File;

/**
 * Debug builds only (registered in src/debug/AndroidManifest.xml, guarded by the DUMP permission so
 * only adb can send it): sends an APK to the account's Saved Messages with the update tag, for
 * {@code tools/release/publish.sh}. Phones signed into the same account then see the update.
 *
 * <pre>adb shell am broadcast -a com.yiligram.update.PUBLISH -n &lt;pkg&gt;/com.yiligram.update.PublishReceiver \
 *     --es path /sdcard/Android/data/&lt;pkg&gt;/files/share/YiliGram.apk --es caption_file /sdcard/.../caption.txt</pre>
 */
public class PublishReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String path = intent.getStringExtra("path");
        String caption = intent.getStringExtra("caption");
        String captionFile = intent.getStringExtra("caption_file");
        if (captionFile != null) {
            try {
                caption = new String(java.nio.file.Files.readAllBytes(new File(captionFile).toPath()), java.nio.charset.StandardCharsets.UTF_8).trim();
            } catch (Exception e) {
                setResultData("error: cannot read " + captionFile);
                return;
            }
        }
        if (caption != null && caption.length() > 1000) {
            caption = caption.substring(0, 1000);
        }
        if (TextUtils.isEmpty(path) || TextUtils.isEmpty(caption) || !caption.contains(TelegramUpdateSource.TAG)) {
            setResultData("error: path and a caption with " + TelegramUpdateSource.TAG + " are required");
            return;
        }
        File f = new File(path);
        if (!f.isFile() || !path.endsWith(".apk")) {
            setResultData("error: no APK at " + path);
            return;
        }
        int account = UserConfig.selectedAccount;
        UserConfig uc = UserConfig.getInstance(account);
        if (!uc.isClientActivated()) {
            setResultData("error: not logged in");
            return;
        }
        long self = uc.getClientUserId();
        final String text = caption;
        AndroidUtilities.runOnUIThread(() -> {
            try {
                SendMessagesHelper.prepareSendingDocument(AccountInstance.getInstance(account), path, path, null, text,
                        "application/vnd.android.package-archive", self, null, null, null, null, null, true, 0, null, null, false);
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
        setResultData("sending " + f.getName() + " (" + f.length() + " bytes) to Saved Messages");
    }
}
