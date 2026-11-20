package com.yiligram.badges;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.HashMap;
import java.util.Map;

/**
 * YiliGram badges next to user names: the team ({@link Type#ADMIN}) and beta testers
 * ({@link Type#BETA}). The list is a JSON message tagged {@code #yiligram_badges}
 * ({"admin":[ids],"beta":[ids]}) read from the account's Saved Messages, where granting from a
 * profile posts the updated list. A public channel can later carry the same message so everyone's
 * app shows the badges.
 */
public final class YiliBadges {

    public enum Type { ADMIN, BETA }

    public static final String TAG = "#yiligram_badges";

    private static final Map<Long, Type> badges = new HashMap<>();
    private static boolean loaded, fetching;
    private static long lastFetch;

    private YiliBadges() {
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("yili_badges", Context.MODE_PRIVATE);
    }

    private static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        parse(prefs().getString("json", null));
    }

    private static void parse(@Nullable String json) {
        badges.clear();
        if (json == null) return;
        try {
            JSONObject o = new JSONObject(json);
            for (Type t : Type.values()) {
                JSONArray a = o.optJSONArray(t.name().toLowerCase());
                for (int i = 0; a != null && i < a.length(); i++) {
                    badges.put(a.getLong(i), t);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    @NonNull
    private static String toJson() {
        try {
            JSONObject o = new JSONObject();
            for (Type t : Type.values()) {
                JSONArray a = new JSONArray();
                for (Map.Entry<Long, Type> e : badges.entrySet()) {
                    if (e.getValue() == t) a.put(e.getKey());
                }
                o.put(t.name().toLowerCase(), a);
            }
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    @Nullable
    public static synchronized Type get(long userId) {
        ensureLoaded();
        refreshIfStale();
        return badges.get(userId);
    }

    /** Grants (or with null removes) a badge and posts the new list to Saved Messages. */
    public static synchronized void set(long userId, @Nullable Type type) {
        ensureLoaded();
        if (type == null) badges.remove(userId); else badges.put(userId, type);
        String json = toJson();
        prefs().edit().putString("json", json).apply();
        publish(json);
        NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_NAME);
    }

    private static void publish(String json) {
        int account = UserConfig.selectedAccount;
        UserConfig uc = UserConfig.getInstance(account);
        if (!uc.isClientActivated()) return;
        String text = TAG + "\n" + json;
        AndroidUtilities.runOnUIThread(() -> SendMessagesHelper.getInstance(account).sendMessage(
                SendMessagesHelper.SendMessageParams.of(text, uc.getClientUserId())));
    }

    private static void refreshIfStale() {
        if (fetching || System.currentTimeMillis() - lastFetch < 30 * 60 * 1000L) return;
        int account = UserConfig.selectedAccount;
        UserConfig uc = UserConfig.getInstance(account);
        if (!uc.isClientActivated()) return;
        fetching = true;
        lastFetch = System.currentTimeMillis();
        TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
        req.peer = MessagesController.getInstance(account).getInputPeer(uc.getClientUserId());
        req.q = TAG;
        req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
        req.limit = 5;
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            fetching = false;
            if (!(response instanceof TLRPC.messages_Messages)) return;
            TLRPC.Message newest = null;
            for (TLRPC.Message m : ((TLRPC.messages_Messages) response).messages) {
                if (m.message != null && m.message.startsWith(TAG) && (newest == null || m.date > newest.date)) newest = m;
            }
            if (newest == null) return;
            String json = newest.message.substring(TAG.length()).trim();
            synchronized (YiliBadges.class) {
                String before = toJson();
                parse(json);
                prefs().edit().putString("json", toJson()).apply();
                if (!before.equals(toJson())) {
                    NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_NAME);
                }
            }
        }));
    }

    /** Badge drawable for a user, or null; {@code sizeDp} is the drawable's height. */
    @Nullable
    public static YiliBadgeDrawable drawable(long userId, int sizeDp) {
        Type t = get(userId);
        return t == null ? null : new YiliBadgeDrawable(t, sizeDp);
    }

    static AccountInstance account() {
        return AccountInstance.getInstance(UserConfig.selectedAccount);
    }
}
