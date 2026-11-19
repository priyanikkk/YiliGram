package com.yiligram.push;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.PushListenerController;
import org.unifiedpush.android.connector.UnifiedPush;

import java.util.List;

/**
 * Push through UnifiedPush (https://unifiedpush.org): a distributor app (ntfy, NextPush, …)
 * keeps one connection for all apps and wakes YiliGram. The endpoint is registered with Telegram
 * as a Web Push subscription (token_type 10); the connector generates the P-256 keys and
 * decrypts incoming messages (RFC 8291). Without a distributor, Telegram's keep-alive
 * background connection is used, as before.
 */
public final class UnifiedPushProvider implements PushListenerController.IPushListenerServiceProvider, PushBridge.Impl {

    public static final UnifiedPushProvider INSTANCE = new UnifiedPushProvider();
    static final String INSTANCE_NAME = "default";
    private static final String PREFS = "yili_push";

    private UnifiedPushProvider() {
    }

    private static Context ctx() {
        return ApplicationLoader.applicationContext;
    }

    static SharedPreferences prefs() {
        return ctx().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---------------------------------------------------------------- IPushListenerServiceProvider

    @Override
    public boolean hasServices() {
        try {
            return UnifiedPush.getAckDistributor(ctx()) != null || UnifiedPush.getSavedDistributor(ctx()) != null;
        } catch (Throwable t) {
            FileLog.e(t);
            return false;
        }
    }

    @Override
    public String getLogTitle() {
        return "UnifiedPush";
    }

    @Override
    public void onRequestPushToken() {
        try {
            UnifiedPush.register(ctx(), INSTANCE_NAME, "YiliGram", null);
        } catch (Throwable t) {
            FileLog.e(t);
            PushListenerController.sendRegistrationToServer(getPushType(), null);
        }
    }

    @Override
    public int getPushType() {
        return PushListenerController.PUSH_TYPE_WEBPUSH;
    }

    // ---------------------------------------------------------------- PushBridge.Impl

    @NonNull
    @Override
    public List<String> distributors() {
        return UnifiedPush.getDistributors(ctx());
    }

    @Nullable
    @Override
    public String current() {
        String d = UnifiedPush.getAckDistributor(ctx());
        return d != null ? d : UnifiedPush.getSavedDistributor(ctx());
    }

    @Override
    public boolean registered() {
        return prefs().getBoolean("registered", false);
    }

    @Override
    public void use(@Nullable String distributor, @NonNull Activity activity, @NonNull Runnable done) {
        try {
            if (distributor == null) {
                UnifiedPush.unregister(ctx(), INSTANCE_NAME);
                UnifiedPush.removeDistributor(ctx());
                prefs().edit().putBoolean("registered", false).apply();
                PushListenerController.sendRegistrationToServer(getPushType(), null);
            } else {
                UnifiedPush.saveDistributor(ctx(), distributor);
                onRequestPushToken();
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
        AndroidUtilities.runOnUIThread(done, 500);
    }

    @NonNull
    @Override
    public String label(@NonNull String packageName) {
        try {
            PackageManager pm = ctx().getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            return pm.getApplicationLabel(info).toString();
        } catch (Exception e) {
            return packageName;
        }
    }

    static void log(String message) {
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("UnifiedPush: " + message);
        }
    }
}
