package com.yiligram.features;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;

import com.yiligram.config.YiliConfig;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.List;

/**
 * Deleted messages and edit history, kept on this device only. Messages are read from Telegram's
 * cache just before it applies the deletion or the edit.
 */
public final class YiliHistory {

    public static final class Entry {
        public long dialogId;
        public int messageId;
        public int date;
        public long fromId;
        public String text;
        public int savedAt;
    }

    private static final SQLiteOpenHelper[] helpers = new SQLiteOpenHelper[UserConfig.MAX_ACCOUNT_COUNT];

    private YiliHistory() {
    }

    private static synchronized SQLiteDatabase db(int account) {
        if (helpers[account] == null) {
            helpers[account] = new SQLiteOpenHelper(ApplicationLoader.applicationContext, "yili_history_" + account + ".db", null, 1) {
                @Override
                public void onCreate(SQLiteDatabase db) {
                    db.execSQL("CREATE TABLE deleted(did INTEGER, mid INTEGER, date INTEGER, from_id INTEGER, text TEXT, saved INTEGER, PRIMARY KEY(did, mid))");
                    db.execSQL("CREATE TABLE edits(did INTEGER, mid INTEGER, date INTEGER, text TEXT, saved INTEGER)");
                    db.execSQL("CREATE INDEX edits_msg ON edits(did, mid)");
                }

                @Override
                public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
                }
            };
        }
        return helpers[account].getWritableDatabase();
    }

    // ---------------------------------------------------------------- capture

    /** {@code channelDialogId}: -channelId for channel updates, 0 for private chats and basic groups. */
    public static void onDeleted(int account, long channelDialogId, ArrayList<Integer> ids) {
        if (ids == null || ids.isEmpty() || !YiliConfig.get(YiliConfig.SAVE_DELETED)) return;
        final ArrayList<Integer> copy = new ArrayList<>(ids);
        MessagesStorage storage = MessagesStorage.getInstance(account);
        storage.getStorageQueue().postRunnable(() -> {
            String where = channelDialogId != 0
                    ? "uid = " + channelDialogId + " AND mid IN (" + TextUtils.join(",", copy) + ")"
                    : "mid IN (" + TextUtils.join(",", copy) + ") AND is_channel = 0";
            for (TLRPC.Message m : read(account, where)) {
                if (m.action != null) continue;
                Entry e = toEntry(account, m);
                if (e == null) continue;
                ContentValues v = new ContentValues();
                v.put("did", e.dialogId);
                v.put("mid", e.messageId);
                v.put("date", e.date);
                v.put("from_id", e.fromId);
                v.put("text", e.text);
                v.put("saved", (int) (System.currentTimeMillis() / 1000));
                try {
                    db(account).insertWithOnConflict("deleted", null, v, SQLiteDatabase.CONFLICT_REPLACE);
                } catch (Exception ex) {
                    FileLog.e(ex);
                }
            }
        });
    }

    /** Called with the new version of a message before Telegram stores it. */
    public static void onEdited(int account, TLRPC.Message edited) {
        if (edited == null || !YiliConfig.get(YiliConfig.SAVE_EDITS)) return;
        final long did = MessageObject.getDialogId(edited);
        final int mid = edited.id;
        final String newText = edited.message;
        MessagesStorage.getInstance(account).getStorageQueue().postRunnable(() -> {
            for (TLRPC.Message old : read(account, "uid = " + did + " AND mid = " + mid)) {
                if (TextUtils.equals(old.message, newText)) continue;
                Entry e = toEntry(account, old);
                if (e == null) continue;
                ContentValues v = new ContentValues();
                v.put("did", did);
                v.put("mid", mid);
                v.put("date", old.edit_date != 0 ? old.edit_date : old.date);
                v.put("text", e.text);
                v.put("saved", (int) (System.currentTimeMillis() / 1000));
                try {
                    db(account).insert("edits", null, v);
                } catch (Exception ex) {
                    FileLog.e(ex);
                }
            }
        });
    }

    private static ArrayList<TLRPC.Message> read(int account, String where) {
        ArrayList<TLRPC.Message> result = new ArrayList<>();
        SQLiteCursor cursor = null;
        try {
            cursor = MessagesStorage.getInstance(account).getDatabase().queryFinalized("SELECT data FROM messages_v2 WHERE " + where);
            while (cursor.next()) {
                NativeByteBuffer data = cursor.byteBufferValue(0);
                if (data == null) continue;
                TLRPC.Message m = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                if (m != null) {
                    m.readAttachPath(data, UserConfig.getInstance(account).clientUserId);
                    result.add(m);
                }
                data.reuse();
            }
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            if (cursor != null) cursor.dispose();
        }
        return result;
    }

    private static Entry toEntry(int account, TLRPC.Message m) {
        Entry e = new Entry();
        e.dialogId = MessageObject.getDialogId(m);
        e.messageId = m.id;
        e.date = m.date;
        e.fromId = m.from_id != null ? DialogObject.getPeerDialogId(m.from_id) : e.dialogId;
        String text = m.message;
        if (TextUtils.isEmpty(text) || m.media != null && !(m.media instanceof TLRPC.TL_messageMediaEmpty) && !(m.media instanceof TLRPC.TL_messageMediaWebPage)) {
            try {
                MessageObject obj = new MessageObject(account, m, false, false);
                CharSequence label = obj.messageText;
                String media = label == null ? "" : label.toString();
                text = TextUtils.isEmpty(m.message) ? media : (TextUtils.isEmpty(media) || media.equals(m.message) ? m.message : "[" + media + "] " + m.message);
            } catch (Exception ignore) {
            }
        }
        if (TextUtils.isEmpty(text)) return null;
        e.text = text;
        return e;
    }

    // ---------------------------------------------------------------- read

    public static List<Entry> deleted(int account, long dialogId) {
        ArrayList<Entry> result = new ArrayList<>();
        try (Cursor c = db(account).rawQuery("SELECT did, mid, date, from_id, text, saved FROM deleted"
                + (dialogId != 0 ? " WHERE did = " + dialogId : "") + " ORDER BY date DESC LIMIT 500", null)) {
            while (c.moveToNext()) {
                Entry e = new Entry();
                e.dialogId = c.getLong(0);
                e.messageId = c.getInt(1);
                e.date = c.getInt(2);
                e.fromId = c.getLong(3);
                e.text = c.getString(4);
                e.savedAt = c.getInt(5);
                result.add(e);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return result;
    }

    /** Earlier versions of a message, oldest first. */
    public static List<Entry> edits(int account, long dialogId, int messageId) {
        ArrayList<Entry> result = new ArrayList<>();
        try (Cursor c = db(account).rawQuery("SELECT date, text FROM edits WHERE did = " + dialogId + " AND mid = " + messageId + " ORDER BY date ASC", null)) {
            while (c.moveToNext()) {
                Entry e = new Entry();
                e.dialogId = dialogId;
                e.messageId = messageId;
                e.date = c.getInt(0);
                e.text = c.getString(1);
                result.add(e);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return result;
    }

    public static boolean hasEdits(int account, long dialogId, int messageId) {
        if (!YiliConfig.get(YiliConfig.SAVE_EDITS)) return false;
        try (Cursor c = db(account).rawQuery("SELECT 1 FROM edits WHERE did = " + dialogId + " AND mid = " + messageId + " LIMIT 1", null)) {
            return c.moveToNext();
        } catch (Exception e) {
            return false;
        }
    }

    public static void clear(int account, long dialogId) {
        try {
            db(account).execSQL("DELETE FROM deleted" + (dialogId != 0 ? " WHERE did = " + dialogId : ""));
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
}
