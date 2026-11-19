package com.yiligram.update;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BetaUpdate;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.web.HttpGetFileTask;
import org.telegram.ui.web.HttpGetTask;

import java.io.File;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * In-app updates from the GitHub Releases of the repository in {@code YILI_RELEASES_URL}.
 *
 * <p>The latest release's tag (e.g. {@code v0.2.0}) is compared with this build's versionName;
 * when it is newer, Telegram's update sheet offers it and the APK asset matching this device's ABI
 * (else a universal one) is downloaded and handed to the system installer. Android itself refuses
 * an APK signed with another key, so only official builds of the same flavor can update.
 */
public final class YiliUpdater {

    private static final long CHECK_INTERVAL = 6 * 60 * 60 * 1000L;

    private static YiliUpdater instance;

    public static YiliUpdater getInstance() {
        if (instance == null) {
            instance = new YiliUpdater();
        }
        return instance;
    }

    private String version;
    private String changelog;
    private String fileUrl;
    private String path;
    private long lastCheck;
    private boolean checking;
    private boolean firstCheck = true;

    private boolean downloading;
    private float progress;
    private HttpGetFileTask downloadTask;

    private YiliUpdater() {
        SharedPreferences p = prefs();
        version = p.getString("version", null);
        changelog = p.getString("changelog", null);
        fileUrl = p.getString("fileUrl", null);
        path = p.getString("path", null);
        lastCheck = p.getLong("lastCheck", 0);
        if (version != null && !isNewer(version, currentVersion())) {
            clear(); // updated meanwhile
        }
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("yili_update", Activity.MODE_PRIVATE);
    }

    private void save() {
        prefs().edit()
                .putString("version", version).putString("changelog", changelog)
                .putString("fileUrl", fileUrl).putString("path", path)
                .putLong("lastCheck", lastCheck).apply();
    }

    private void clear() {
        if (!TextUtils.isEmpty(path)) {
            new File(path).delete();
        }
        version = changelog = fileUrl = path = null;
        save();
    }

    /** {@code https://api.github.com/repos/{owner}/{repo}/releases/latest}, or null if the URL is not a GitHub repo. */
    public static String apiUrl() {
        Matcher m = Pattern.compile("^https://github\\.com/([^/]+)/([^/]+)").matcher(BuildConfig.YILI_RELEASES_URL);
        return m.find() ? "https://api.github.com/repos/" + m.group(1) + "/" + m.group(2) + "/releases/latest" : null;
    }

    public static boolean isConfigured() {
        return apiUrl() != null;
    }

    static String currentVersion() {
        try {
            Context c = ApplicationLoader.applicationContext;
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "0";
        }
    }

    /** Numeric part of a tag/versionName: "v0.2.0" and "0.2.0 (based on 13.2.2)" give "0.2.0". */
    static String clean(String v) {
        Matcher m = Pattern.compile("(\\d+(?:\\.\\d+)*)").matcher(v == null ? "" : v);
        return m.find() ? m.group(1) : "0";
    }

    static boolean isNewer(String candidate, String installed) {
        String a = clean(candidate), b = clean(installed);
        return !a.equals(b) && SharedConfig.versionBiggerOrEqual(a, b);
    }

    public BetaUpdate getUpdate() {
        if (version == null) {
            return null;
        }
        // BetaUpdate needs a code; versions are compared by name, so any positive value works.
        return new BetaUpdate(clean(version), 1, changelog);
    }

    public void checkForUpdate(boolean force, Runnable whenDone) {
        final String url = apiUrl();
        if (url == null || checking) {
            if (whenDone != null) whenDone.run();
            return;
        }
        if (firstCheck) {
            force = true;
            firstCheck = false;
        }
        if (!force && System.currentTimeMillis() - lastCheck < CHECK_INTERVAL) {
            if (whenDone != null) whenDone.run();
            return;
        }
        checking = true;
        new HttpGetTask(str -> AndroidUtilities.runOnUIThread(() -> {
            checking = false;
            final String old = version;
            try {
                if (str != null) {
                    JSONObject json = new JSONObject(str);
                    String tag = json.optString("tag_name", "");
                    String asset = pickAsset(json.optJSONArray("assets"));
                    lastCheck = System.currentTimeMillis();
                    if (!json.optBoolean("draft") && asset != null && isNewer(tag, currentVersion())) {
                        if (!tag.equals(version)) {
                            if (!TextUtils.isEmpty(path)) new File(path).delete();
                            path = null;
                        }
                        version = tag;
                        changelog = releaseNotes(json.optString("body", null));
                        fileUrl = asset;
                    } else {
                        clear();
                    }
                    save();
                }
            } catch (Exception e) {
                FileLog.e("YiliUpdater: bad release info from " + url, e);
            }
            if (!TextUtils.equals(old, version)) {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
            }
            if (whenDone != null) whenDone.run();
        })).execute(url);
    }

    /** GitHub release notes to the light Markdown YiliMarkdown renders: headings bold, bullets, no HTML. */
    static String releaseNotes(String body) {
        if (body == null) return null;
        StringBuilder out = new StringBuilder();
        for (String line : body.replace("\r", "").replace("&nbsp;", " ").replaceAll("<[^>]+>", "").split("\n")) {
            String t = line.trim();
            if (t.matches("#{1,6}(\\s.*)?")) {
                t = t.replaceFirst("#{1,6}\\s*", "").trim();
                if (t.isEmpty()) continue;
                t = "**" + t.replace("**", "") + "**";
            } else if (t.startsWith("- ") || t.startsWith("* ")) {
                t = "• " + t.substring(2);
            }
            if (t.isEmpty() && (out.length() == 0 || out.toString().endsWith("\n\n"))) continue;
            out.append(t).append('\n');
        }
        return out.toString().trim();
    }

    /** The APK for this device: its primary ABI in the name, else "universal", else the only APK. */
    private static String pickAsset(JSONArray assets) {
        if (assets == null) return null;
        String universal = null, any = null;
        int apks = 0;
        String abi = Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0].toLowerCase(Locale.US) : "";
        for (int i = 0; i < assets.length(); i++) {
            JSONObject a = assets.optJSONObject(i);
            if (a == null) continue;
            String name = a.optString("name", "").toLowerCase(Locale.US);
            String dl = a.optString("browser_download_url", null);
            if (!name.endsWith(".apk") || dl == null || !dl.startsWith("https://")) continue;
            apks++;
            any = dl;
            if (!abi.isEmpty() && name.contains(abi)) return dl;
            if (name.contains("universal")) universal = dl;
        }
        return universal != null ? universal : apks == 1 ? any : null;
    }

    public void downloadUpdate() {
        if (downloading || fileUrl == null) return;
        if (getDownloadedFile() != null) {
            openInstaller();
            return;
        }
        downloading = true;
        progress = 0;
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateLoading);
        downloadTask = new HttpGetFileTask(file -> AndroidUtilities.runOnUIThread(() -> {
            downloading = false;
            if (file != null) {
                path = file.getAbsolutePath();
                progress = 1f;
                save();
                openInstaller();
            }
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
        }), p -> {
            progress = p;
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateLoading);
        }).setOverrideExtension("apk");
        downloadTask.execute(fileUrl);
    }

    private void openInstaller() {
        File f = getDownloadedFile();
        Activity a = LaunchActivity.instance;
        if (f != null && a != null && ApplicationLoader.applicationLoaderInstance.checkApkInstallPermissions(a)) {
            AndroidUtilities.openForView(f, "YiliGram.apk", "application/vnd.android.package-archive", a, null, false);
        }
    }

    public void cancelDownload() {
        if (!downloading) return;
        if (downloadTask != null) downloadTask.cancel(false);
        downloading = false;
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
    }

    public boolean isDownloading() {
        return downloading;
    }

    public float getProgress() {
        return progress;
    }

    public File getDownloadedFile() {
        if (path == null) return null;
        File f = new File(path);
        if (!f.exists()) {
            path = null;
            save();
            return null;
        }
        return f;
    }

    /** Shows the update sheet if an update is pending (used by the manual check in settings). */
    public static void showPending(Context context) {
        BetaUpdate u = getInstance().getUpdate();
        if (u != null) {
            ApplicationLoader.applicationLoaderInstance.showCustomUpdateAppPopup(context, u, UserConfig.selectedAccount);
        }
    }
}
