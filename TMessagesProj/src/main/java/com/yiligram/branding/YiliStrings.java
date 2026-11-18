package com.yiligram.branding;

import android.text.TextUtils;

import org.telegram.messenger.R;

/**
 * Strings that carry the YiliGram name or the "unofficial client" notice.
 *
 * <p>Telegram downloads translations from its servers ({@code USE_CLOUD_STRINGS}) and those
 * would put the name "Telegram" back. Keys listed here are always taken from the APK
 * resources, never from cloud language packs (Telegram API ToS 2.2–2.3).
 */
public final class YiliStrings {

    private static final String[] LOCAL_ONLY_KEYS = {
            "AppName",
            "AppNameBeta",
            "NotificationHiddenName",
            "TelegramVersion",
            "Page1Title",
            "Page1Message",
    };

    private static final int[] LOCAL_ONLY_RES = {
            R.string.AppName,
            R.string.AppNameBeta,
            R.string.NotificationHiddenName,
            R.string.TelegramVersion,
            R.string.Page1Title,
            R.string.Page1Message,
    };

    private YiliStrings() {
    }

    /** @return true if the string must not be overridden by a cloud language pack. */
    public static boolean isLocalOnly(String key, int res) {
        if (res != 0) {
            for (int id : LOCAL_ONLY_RES) {
                if (id == res) {
                    return true;
                }
            }
        }
        if (!TextUtils.isEmpty(key)) {
            for (String k : LOCAL_ONLY_KEYS) {
                if (k.equals(key)) {
                    return true;
                }
            }
        }
        return false;
    }
}
