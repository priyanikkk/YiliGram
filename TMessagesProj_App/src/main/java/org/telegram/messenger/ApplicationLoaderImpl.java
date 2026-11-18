package org.telegram.messenger;

import com.yiligram.push.NoPushProvider;

import org.telegram.messenger.regular.BuildConfig;

public class ApplicationLoaderImpl extends ApplicationLoader {
    @Override
    public void onCreate() {
        super.onCreate();
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

    // YILI: no Firebase project; see NoPushProvider.
    @Override
    protected PushListenerController.IPushListenerServiceProvider onCreatePushProvider() {
        return NoPushProvider.INSTANCE;
    }
}
