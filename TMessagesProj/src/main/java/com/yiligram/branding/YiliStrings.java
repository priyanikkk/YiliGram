package com.yiligram.branding;

import android.content.Context;
import android.text.TextUtils;
import android.util.SparseBooleanArray;

import org.telegram.messenger.R;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

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

    /**
     * Strings where "Telegram" names the app itself (permission prompts, call-based code
     * confirmation, storage errors) rather than the service. In those the word is replaced
     * with "YiliGram" in every language, including cloud language packs. Strings about the
     * service ("Telegram Premium", "anyone on Telegram", "your Telegram account") are untouched.
     */
    private static final String[] APP_NAME_KEY_PREFIXES = {
            "Permission",
            "AllowRead",
            "AllowFillNumber",
            "VoipNeed",
            "QRCodePermission",
            "ContactsPermission",
            "NotificationsPermission",
    };

    private static final Set<String> APP_NAME_KEYS = new HashSet<>(Arrays.asList(
            "SentCallCode",
            "CallText",
            "SdCardErrorDescription",
            "BrowserExternalRestricted",
            "BotLocationPermissionRequestDeniedApp",
            "SMSFee2Text"
    ));

    private static final SparseBooleanArray appNameResCache = new SparseBooleanArray();

    private YiliStrings() {
    }

    /** Applies YiliGram naming to a resolved string; returns {@code value} when not applicable. */
    public static String rebrand(Context context, String key, int res, String value) {
        if (value == null || value.indexOf("Telegram") < 0 || !refersToApp(context, key, res)) {
            return value;
        }
        return value.replace("Telegram", "YiliGram");
    }

    private static boolean refersToApp(Context context, String key, int res) {
        if (TextUtils.isEmpty(key) && res != 0) {
            synchronized (appNameResCache) {
                int i = appNameResCache.indexOfKey(res);
                if (i >= 0) {
                    return appNameResCache.valueAt(i);
                }
            }
            boolean result;
            try {
                result = context != null && keyRefersToApp(context.getResources().getResourceEntryName(res));
            } catch (Exception e) {
                result = false;
            }
            synchronized (appNameResCache) {
                appNameResCache.put(res, result);
            }
            return result;
        }
        return keyRefersToApp(key);
    }

    static boolean keyRefersToApp(String key) {
        if (TextUtils.isEmpty(key)) {
            return false;
        }
        if (APP_NAME_KEYS.contains(key)) {
            return true;
        }
        for (String prefix : APP_NAME_KEY_PREFIXES) {
            if (key.startsWith(prefix)) {
                return true;
            }
        }
        return false;
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
