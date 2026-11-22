package com.yiligram.features;

import com.yiligram.config.YiliConfig;

import org.json.JSONArray;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * Quick reply templates. Variables: {name} — the chat partner's first name, {time} and {date} — now.
 */
public final class YiliTemplates {

    private YiliTemplates() {
    }

    public static ArrayList<String> list() {
        ArrayList<String> result = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(YiliConfig.get(YiliConfig.TEMPLATES));
            for (int i = 0; i < a.length(); i++) {
                String s = a.optString(i, "");
                if (!s.isEmpty()) result.add(s);
            }
        } catch (Exception ignore) {
        }
        return result;
    }

    public static void save(ArrayList<String> templates) {
        JSONArray a = new JSONArray();
        for (String s : templates) a.put(s);
        YiliConfig.set(YiliConfig.TEMPLATES, a.toString());
    }

    public static void add(String text) {
        ArrayList<String> l = list();
        l.add(text);
        save(l);
    }

    public static void remove(int index) {
        ArrayList<String> l = list();
        if (index >= 0 && index < l.size()) {
            l.remove(index);
            save(l);
        }
    }

    public static String expand(String template, TLRPC.User user, TLRPC.Chat chat) {
        String name = user != null ? UserObject.getFirstName(user) : chat != null ? chat.title : "";
        Date now = new Date();
        return template
                .replace("{name}", name == null ? "" : name)
                .replace("{time}", new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now))
                .replace("{date}", new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(now));
    }
}
