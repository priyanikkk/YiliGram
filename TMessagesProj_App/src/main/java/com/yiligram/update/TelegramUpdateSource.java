package com.yiligram.update;

import android.content.Context;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Updates delivered through Telegram itself: the newest message in the user's Saved Messages that
 * carries an APK and the tag {@code #yiligram_update vc=<versionCode> v=<versionName>} (posted by
 * {@code tools/release/publish.sh}). The APK is downloaded with Telegram's own FileLoader.
 */
final class TelegramUpdateSource implements NotificationCenter.NotificationCenterDelegate {

    static final String TAG = "#yiligram_update";
    private static final Pattern VC = Pattern.compile("\\bvc=(\\d+)");
    private static final Pattern V = Pattern.compile("\\bv=(\\S+)");

    int account = UserConfig.selectedAccount;
    MessageObject message;
    String versionName;
    int versionCode;
    String notes;

    private String attachName;
    boolean downloading;
    float progress;
    File file;
    Runnable onChanged;

    static int installedVersionCode() {
        try {
            Context c = ApplicationLoader.applicationContext;
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    boolean hasUpdate() {
        return message != null && versionCode > installedVersionCode();
    }

    /** Looks for the newest tagged APK in Saved Messages; {@code done} runs on the UI thread. */
    void check(Runnable done) {
        account = UserConfig.selectedAccount;
        UserConfig uc = UserConfig.getInstance(account);
        if (!uc.isClientActivated()) {
            AndroidUtilities.runOnUIThread(done);
            return;
        }
        TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
        req.peer = MessagesController.getInstance(account).getInputPeer(uc.getClientUserId());
        req.q = TAG;
        req.filter = new TLRPC.TL_inputMessagesFilterDocument();
        req.limit = 10;
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            try {
                if (response instanceof TLRPC.messages_Messages) {
                    pickNewest(((TLRPC.messages_Messages) response).messages);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            done.run();
        }));
    }

    private void pickNewest(java.util.ArrayList<TLRPC.Message> messages) {
        TLRPC.Message best = null;
        int bestCode = 0;
        String bestName = null;
        for (TLRPC.Message m : messages) {
            if (m.message == null || !m.message.contains(TAG)) continue;
            if (!(m.media instanceof TLRPC.TL_messageMediaDocument) || m.media.document == null) continue;
            String fileName = FileLoader.getDocumentFileName(m.media.document);
            if (fileName == null || !fileName.toLowerCase().endsWith(".apk")) continue;
            Matcher c = VC.matcher(m.message);
            if (!c.find()) continue;
            int code = Integer.parseInt(c.group(1));
            if (code > bestCode) {
                bestCode = code;
                best = m;
                Matcher v = V.matcher(m.message);
                bestName = v.find() ? v.group(1) : String.valueOf(code);
            }
        }
        if (best == null) {
            message = null;
            return;
        }
        if (message == null || message.getId() != best.id) {
            file = null;
        }
        message = new MessageObject(account, best, false, false);
        versionCode = bestCode;
        versionName = bestName;
        // Release notes: the caption without the tag line.
        StringBuilder sb = new StringBuilder();
        for (String line : best.message.split("\n")) {
            if (!line.contains(TAG)) sb.append(line).append('\n');
        }
        notes = sb.toString().trim();
    }

    void download() {
        if (message == null || downloading) return;
        TLRPC.Document doc = message.getDocument();
        File existing = FileLoader.getInstance(account).getPathToMessage(message.messageOwner);
        if (existing != null && existing.exists() && existing.length() == doc.size) {
            file = existing;
            changed();
            return;
        }
        attachName = FileLoader.getAttachFileName(doc);
        downloading = true;
        progress = 0;
        NotificationCenter nc = NotificationCenter.getInstance(account);
        nc.addObserver(this, NotificationCenter.fileLoaded);
        nc.addObserver(this, NotificationCenter.fileLoadProgressChanged);
        nc.addObserver(this, NotificationCenter.fileLoadFailed);
        FileLoader.getInstance(account).loadFile(doc, message, FileLoader.PRIORITY_HIGH, 0);
        changed();
    }

    void cancel() {
        if (!downloading || message == null) return;
        FileLoader.getInstance(account).cancelLoadFile(message.getDocument());
        stop();
        changed();
    }

    private void stop() {
        downloading = false;
        NotificationCenter nc = NotificationCenter.getInstance(account);
        nc.removeObserver(this, NotificationCenter.fileLoaded);
        nc.removeObserver(this, NotificationCenter.fileLoadProgressChanged);
        nc.removeObserver(this, NotificationCenter.fileLoadFailed);
    }

    private void changed() {
        if (onChanged != null) onChanged.run();
    }

    @Override
    public void didReceivedNotification(int id, int acc, Object... args) {
        if (args.length == 0 || !(args[0] instanceof String) || !args[0].equals(attachName)) return;
        if (id == NotificationCenter.fileLoadProgressChanged && args.length >= 3) {
            long loaded = (Long) args[1], total = (Long) args[2];
            progress = total > 0 ? Math.min(1f, loaded / (float) total) : 0;
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateLoading);
        } else if (id == NotificationCenter.fileLoaded) {
            stop();
            File f = args.length > 1 && args[1] instanceof File ? (File) args[1]
                    : FileLoader.getInstance(account).getPathToMessage(message.messageOwner);
            file = f != null && f.exists() ? f : null;
            progress = 1f;
            changed();
        } else if (id == NotificationCenter.fileLoadFailed) {
            stop();
            changed();
        }
    }
}
