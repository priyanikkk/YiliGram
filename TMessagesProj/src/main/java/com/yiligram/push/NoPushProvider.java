package com.yiligram.push;

import org.telegram.messenger.PushListenerController;

/**
 * Push provider used until UnifiedPush support lands.
 *
 * <p>YiliGram has no Firebase project, so FCM tokens cannot be obtained. Reporting "no services"
 * makes Telegram fall back to its keep-alive background connection, exactly as on devices
 * without Google Play Services.
 */
public final class NoPushProvider implements PushListenerController.IPushListenerServiceProvider {

    public static final NoPushProvider INSTANCE = new NoPushProvider();

    private NoPushProvider() {
    }

    @Override
    public boolean hasServices() {
        return false;
    }

    @Override
    public String getLogTitle() {
        return "YiliGram (no push service)";
    }

    @Override
    public void onRequestPushToken() {
    }

    @Override
    public int getPushType() {
        return PushListenerController.PUSH_TYPE_FIREBASE;
    }
}
