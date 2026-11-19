package org.telegram.messenger;

import com.yiligram.push.PushBridge;
import com.yiligram.push.UnifiedPushProvider;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.view.ViewGroup;

import com.yiligram.update.YiliUpdater;

import org.telegram.messenger.regular.BuildConfig;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.UpdateAppAlertDialog;
import org.telegram.ui.Components.UpdateLayout;
import org.telegram.ui.IUpdateLayout;

import java.io.File;

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

    // ---- YILI: in-app updates from GitHub Releases (com.yiligram.update.YiliUpdater) ----

    @Override
    public boolean isCustomUpdate() {
        return YiliUpdater.isConfigured() && com.yiligram.config.YiliConfig.get(com.yiligram.config.YiliConfig.AUTO_UPDATE_CHECK);
    }

    @Override
    public BetaUpdate getUpdate() {
        return YiliUpdater.getInstance().getUpdate();
    }

    @Override
    public void checkUpdate(boolean force, Runnable whenDone) {
        if (YiliUpdater.isConfigured()) {
            YiliUpdater.getInstance().checkForUpdate(force, whenDone);
        } else if (whenDone != null) {
            whenDone.run();
        }
    }

    @Override
    public void downloadUpdate() {
        YiliUpdater.getInstance().downloadUpdate();
    }

    @Override
    public void cancelDownloadingUpdate() {
        YiliUpdater.getInstance().cancelDownload();
    }

    @Override
    public boolean isDownloadingUpdate() {
        return YiliUpdater.getInstance().isDownloading();
    }

    @Override
    public float getDownloadingUpdateProgress() {
        return YiliUpdater.getInstance().getProgress();
    }

    @Override
    public File getDownloadedUpdateFile() {
        return YiliUpdater.getInstance().getDownloadedFile();
    }

    @Override
    public IUpdateLayout takeUpdateLayout(Activity activity, ViewGroup sideMenuContainer) {
        return isCustomUpdate() ? new UpdateLayout(activity, sideMenuContainer) : null;
    }

    @Override
    public boolean showCustomUpdateAppPopup(Context context, BetaUpdate update, int account) {
        try {
            new UpdateAppAlertDialog(context, update, account).show();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return true;
    }

    @Override
    public boolean checkApkInstallPermissions(final Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.getPackageManager().canRequestPackageInstalls()) {
            AlertsCreator.createApkRestrictedDialog(context, null).show();
            return false;
        }
        return true;
    }
}
