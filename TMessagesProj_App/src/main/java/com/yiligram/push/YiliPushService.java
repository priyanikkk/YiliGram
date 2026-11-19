package com.yiligram.push;

import androidx.annotation.NonNull;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.PushListenerController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.unifiedpush.android.connector.FailedReason;
import org.unifiedpush.android.connector.PushService;
import org.unifiedpush.android.connector.data.PushEndpoint;
import org.unifiedpush.android.connector.data.PushMessage;

import java.nio.charset.StandardCharsets;

/** Receives UnifiedPush events (registered in the app manifest). */
public class YiliPushService extends PushService {

    @Override
    public void onNewEndpoint(@NonNull PushEndpoint endpoint, @NonNull String instance) {
        try {
            JSONObject token = new JSONObject();
            token.put("endpoint", endpoint.getUrl());
            if (endpoint.getPubKeySet() != null) {
                JSONObject keys = new JSONObject();
                keys.put("p256dh", endpoint.getPubKeySet().getPubKey());
                keys.put("auth", endpoint.getPubKeySet().getAuth());
                token.put("keys", keys);
            }
            UnifiedPushProvider.log("new endpoint" + (endpoint.getTemporary() ? " (temporary)" : ""));
            UnifiedPushProvider.prefs().edit().putBoolean("registered", true).apply();
            PushListenerController.sendRegistrationToServer(PushListenerController.PUSH_TYPE_WEBPUSH, token.toString());
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    @Override
    public void onMessage(@NonNull PushMessage message, @NonNull String instance) {
        long now = System.currentTimeMillis();
        String encryptedForTelegram = null;
        if (message.getDecrypted()) {
            try {
                JSONObject json = new JSONObject(new String(message.getContent(), StandardCharsets.UTF_8));
                // Telegram encrypts the notification with the device secret (MTProto v2) and sends
                // it as "p", like FCM; anything else still wakes the connection below.
                encryptedForTelegram = json.optString("p", null);
                if (encryptedForTelegram == null && json.has("data")) {
                    encryptedForTelegram = json.optJSONObject("data") != null ? json.getJSONObject("data").optString("p", null) : null;
                }
            } catch (Exception e) {
                UnifiedPushProvider.log("payload is not JSON: " + e.getMessage());
            }
        } else {
            UnifiedPushProvider.log("message could not be decrypted");
        }
        if (encryptedForTelegram != null) {
            PushListenerController.processRemoteMessage(PushListenerController.PUSH_TYPE_WEBPUSH, encryptedForTelegram, now);
        } else {
            wakeAllAccounts();
        }
    }

    /** Connects every logged-in account so pending updates arrive over MTProto. */
    private static void wakeAllAccounts() {
        AndroidUtilities.runOnUIThread(() -> {
            ApplicationLoader.postInitApplication();
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (UserConfig.getInstance(a).isClientActivated()) {
                    ConnectionsManager.onInternalPushReceived(a);
                    ConnectionsManager.getInstance(a).resumeNetworkMaybe();
                }
            }
        });
    }

    @Override
    public void onRegistrationFailed(@NonNull FailedReason reason, @NonNull String instance) {
        UnifiedPushProvider.log("registration failed: " + reason);
        UnifiedPushProvider.prefs().edit().putBoolean("registered", false).apply();
        PushListenerController.sendRegistrationToServer(PushListenerController.PUSH_TYPE_WEBPUSH, null);
    }

    @Override
    public void onUnregistered(@NonNull String instance) {
        UnifiedPushProvider.log("unregistered");
        UnifiedPushProvider.prefs().edit().putBoolean("registered", false).apply();
        PushListenerController.sendRegistrationToServer(PushListenerController.PUSH_TYPE_WEBPUSH, null);
    }
}
