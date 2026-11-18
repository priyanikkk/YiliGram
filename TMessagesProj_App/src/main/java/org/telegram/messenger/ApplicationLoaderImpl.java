package org.telegram.messenger;

import com.yiligram.push.NoPushProvider;

import org.telegram.messenger.regular.BuildConfig;

public class ApplicationLoaderImpl extends ApplicationLoader {
    @Override
    protected String onGetApplicationId() {
        return BuildConfig.APPLICATION_ID;
    }

    // YILI: no Firebase project; see NoPushProvider.
    @Override
    protected PushListenerController.IPushListenerServiceProvider onCreatePushProvider() {
        return NoPushProvider.INSTANCE;
    }
}
