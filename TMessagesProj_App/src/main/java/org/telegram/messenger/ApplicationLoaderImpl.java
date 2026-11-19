package org.telegram.messenger;

import com.yiligram.push.PushBridge;
import com.yiligram.push.UnifiedPushProvider;

import org.telegram.messenger.regular.BuildConfig;

public class ApplicationLoaderImpl extends ApplicationLoader {
    @Override
    public void onCreate() {
        super.onCreate();
        PushBridge.set(UnifiedPushProvider.INSTANCE);
        // YILI: Python plugin engine (not in the isolated widget process).
        if (!org.telegram.utils.twidget.WidgetEngineService.isEngineProcess()
                && com.yiligram.config.YiliConfig.get(com.yiligram.config.YiliConfig.PLUGINS_ENABLED)) {
            com.yiligram.plugins.python.PythonPluginEngine engine =
                    new com.yiligram.plugins.python.PythonPluginEngine(this);
            com.yiligram.plugins.Plugins.setEngine(engine);
            engine.start();
        }
    }

    @Override
    protected String onGetApplicationId() {
        return BuildConfig.APPLICATION_ID;
    }

    // YILI: no Firebase project. UnifiedPush when a distributor is chosen, otherwise Telegram's
    // keep-alive background connection (hasServices() == false).
    @Override
    protected PushListenerController.IPushListenerServiceProvider onCreatePushProvider() {
        return UnifiedPushProvider.INSTANCE;
    }
}
