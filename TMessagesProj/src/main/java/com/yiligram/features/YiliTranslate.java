package com.yiligram.features;

import com.yiligram.config.YiliConfig;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Locale;

/** Translates your own text before sending, with Telegram's translation (messages.translateText). */
public final class YiliTranslate {

    /** Language codes offered for outgoing text. */
    public static final String[] LANGUAGES = {"en", "ru", "uk", "de", "es", "fr", "it", "pt", "pl", "tr", "zh", "ja", "ko", "ar", "hi"};

    public interface Callback {
        void run(String translated, boolean error);
    }

    private YiliTranslate() {
    }

    public static String languageName(String code) {
        Locale l = new Locale(code);
        String name = l.getDisplayLanguage(Locale.getDefault());
        return name.isEmpty() ? code : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    public static String lastLanguage() {
        return YiliConfig.get(YiliConfig.TRANSLATE_OUT_LANG);
    }

    public static void translate(int account, String text, String toLang, Callback callback) {
        YiliConfig.set(YiliConfig.TRANSLATE_OUT_LANG, toLang);
        TLRPC.TL_messages_translateText req = new TLRPC.TL_messages_translateText();
        req.flags |= 2;
        TLRPC.TL_textWithEntities t = new TLRPC.TL_textWithEntities();
        t.text = text;
        t.entities = new ArrayList<>();
        req.text.add(t);
        req.to_lang = toLang;
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response instanceof TLRPC.TL_messages_translateResult && !((TLRPC.TL_messages_translateResult) response).result.isEmpty()) {
                callback.run(((TLRPC.TL_messages_translateResult) response).result.get(0).text, false);
            } else {
                callback.run(null, true);
            }
        }));
    }
}
