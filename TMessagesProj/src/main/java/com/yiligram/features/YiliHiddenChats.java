package com.yiligram.features;

import com.yiligram.config.YiliConfig;

import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Chats hidden from the chat list; they open from YiliGram features → Hidden chats behind the screen lock.
 * Stored as "account:dialogId" entries.
 */
public final class YiliHiddenChats {

    private static String cachedRaw;
    private static Set<String> cached = new HashSet<>();

    private YiliHiddenChats() {
    }

    private static synchronized Set<String> set() {
        String raw = YiliConfig.get(YiliConfig.HIDDEN_CHATS);
        if (!raw.equals(cachedRaw)) {
            cachedRaw = raw;
            cached = new HashSet<>();
            for (String s : raw.split(",")) {
                if (!s.isEmpty()) cached.add(s);
            }
        }
        return cached;
    }

    public static boolean isHidden(int account, long dialogId) {
        return set().contains(account + ":" + dialogId);
    }

    public static synchronized void setHidden(int account, long dialogId, boolean hidden) {
        Set<String> s = new HashSet<>(set());
        String key = account + ":" + dialogId;
        if (hidden) s.add(key); else s.remove(key);
        YiliConfig.set(YiliConfig.HIDDEN_CHATS, String.join(",", s));
    }

    public static ArrayList<Long> list(int account) {
        ArrayList<Long> result = new ArrayList<>();
        String prefix = account + ":";
        for (String s : set()) {
            if (s.startsWith(prefix)) {
                try {
                    result.add(Long.parseLong(s.substring(prefix.length())));
                } catch (NumberFormatException ignore) {
                }
            }
        }
        return result;
    }

    /** The chat list without hidden chats; the same list when nothing is hidden. */
    public static ArrayList<TLRPC.Dialog> filter(int account, ArrayList<TLRPC.Dialog> dialogs) {
        if (dialogs == null || set().isEmpty()) return dialogs;
        ArrayList<TLRPC.Dialog> result = null;
        for (int i = 0; i < dialogs.size(); i++) {
            TLRPC.Dialog d = dialogs.get(i);
            if (d != null && isHidden(account, d.id)) {
                if (result == null) {
                    result = new ArrayList<>(dialogs.subList(0, i));
                }
            } else if (result != null) {
                result.add(d);
            }
        }
        return result != null ? result : dialogs;
    }
}
