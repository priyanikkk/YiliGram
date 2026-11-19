package com.yiligram.plugins;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Process;

import androidx.core.content.FileProvider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.FileLog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * "Share plugin logs": one text file with the device, the plugin engine's own report and the
 * plugin-related lines of this process's logcat. Telegram's network logs (MTProto, tgnet) carry
 * message contents and are never included: only lines whose log tag is about plugins, hooks or
 * crashes are kept.
 */
public final class PluginDiagnostics {

    /** "MM-DD HH:MM:SS.mmm L/Tag( pid): message" -> tag in group 1, message in group 2. */
    private static final Pattern LINE = Pattern.compile("^\\S+ \\S+ [VDIWEF]/([^(]+)\\(\\s*\\d+\\): ?(.*)$");
    private static final Pattern TAGS = Pattern.compile(
            "plugin\\..*|Yili.*|yili.*|LSPlant|python\\.std(out|err)|[Cc]haquopy|AndroidRuntime|DEBUG|libc|DexMaker|HiddenApiBypass");

    private PluginDiagnostics() {
    }

    /** Builds the report on the plugins queue and opens the share sheet. */
    public static void share(Activity activity) {
        PluginApi.PLUGINS_QUEUE.postRunnable(() -> {
            File file;
            try {
                file = write(build());
            } catch (Exception e) {
                FileLog.e(e);
                return;
            }
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    Uri uri = FileProvider.getUriForFile(activity, ApplicationLoader.getApplicationId() + ".provider", file);
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType("text/plain");
                    send.putExtra(Intent.EXTRA_STREAM, uri);
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    activity.startActivity(Intent.createChooser(send, file.getName()));
                } catch (Exception e) {
                    FileLog.e(e);
                }
            });
        });
    }

    static String build() {
        StringBuilder sb = new StringBuilder();
        sb.append("YiliGram plugin report ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())).append('\n');
        sb.append("== Device\n");
        sb.append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append(", Android ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("ABIs: ").append(Arrays.toString(Build.SUPPORTED_ABIS)).append('\n');
        try {
            sb.append("App: ").append(ApplicationLoader.applicationContext.getPackageManager()
                    .getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0).versionName).append(", debug=").append(BuildConfig.DEBUG).append('\n');
        } catch (Exception ignored) {
        }
        sb.append('\n');
        PluginEngine engine = Plugins.engine();
        sb.append(engine == null ? "== Plugin engine is off\n" : engine.diagnostics()).append("\n\n");
        sb.append("== Logcat (plugin, hook and crash lines of this process)\n");
        ArrayDeque<String> tail = new ArrayDeque<>();
        try {
            java.lang.Process p = Runtime.getRuntime().exec(new String[]{"logcat", "-d", "-v", "time", "--pid=" + Process.myPid()});
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    java.util.regex.Matcher m = LINE.matcher(line);
                    boolean keep = m.matches() && (TAGS.matcher(m.group(1).trim()).matches()
                            || m.group(1).trim().equals("art") && m.group(2).toLowerCase(Locale.US).contains("hook"));
                    if (keep) {
                        tail.add(line.length() > 600 ? line.substring(0, 600) + "…" : line);
                        if (tail.size() > 1500) {
                            tail.removeFirst();
                        }
                    }
                }
            }
        } catch (Exception e) {
            sb.append("(logcat unavailable: ").append(e).append(")\n");
        }
        for (String l : tail) {
            sb.append(l).append('\n');
        }
        return sb.toString();
    }

    private static File write(String text) throws Exception {
        File dir = new File(ApplicationLoader.getFilesDirFixed(), "cache");
        dir.mkdirs();
        File f = new File(dir, "yiligram-plugin-report.txt");
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return f;
    }
}
