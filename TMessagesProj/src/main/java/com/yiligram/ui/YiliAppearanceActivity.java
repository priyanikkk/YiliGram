package com.yiligram.ui;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;

import androidx.core.content.FileProvider;

import com.yiligram.config.FontFamily;
import com.yiligram.config.YiliConfig;
import com.yiligram.config.YiliFonts;
import com.yiligram.design.color.ColorSpecVersion;
import com.yiligram.design.color.ContrastLevel;
import com.yiligram.design.color.DynamicColorSource;
import com.yiligram.design.color.SchemeVariant;
import com.yiligram.design.motion.YiliMotion;

import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;

/**
 * Settings → YiliGram: colors, shape and motion of the Material 3 Expressive design, with a live
 * preview, plus export / import / reset of the whole configuration.
 */
public class YiliAppearanceActivity extends UniversalFragment implements YiliConfig.Listener {

    private static final int ID_DYNAMIC = 1;
    private static final int ID_FOLLOW_SYSTEM = 2;
    private static final int ID_VARIANT = 3;
    private static final int ID_AMOLED = 4;
    private static final int ID_SPEC = 5;
    private static final int ID_ANIMATIONS = 6;
    private static final int ID_EXPORT = 7;
    private static final int ID_IMPORT = 8;
    private static final int ID_RESET = 9;

    private static final int REQUEST_IMPORT = 7301;
    private static final int REQUEST_FONT = 7303;
    private static final int ID_FONT = 10;
    private static final String EXPORT_NAME = "yiligram-settings.yiliconfig";

    private AppearancePreviewView preview;
    private SeedColorPickerView seedPicker;

    @Override
    public boolean onFragmentCreate() {
        YiliConfig.addListener(this);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        YiliConfig.removeListener(this);
        super.onFragmentDestroy();
    }

    @Override
    public View createView(Context context) {
        preview = new AppearancePreviewView(context);
        seedPicker = new SeedColorPickerView(context);
        seedPicker.setOnSelect(seed -> YiliConfig.set(YiliConfig.SEED_COLOR, seed));
        return super.createView(context);
    }

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.YiliAppearance);
    }

    @Override
    public void onYiliConfigChanged(Set<YiliConfig.Key<?>> changed) {
        if (listView != null) {
            listView.adapter.update(true);
        }
        if (preview != null) {
            preview.invalidate();
        }
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCustom(preview));

        // ---- Color
        items.add(UItem.asHeader(getString(R.string.YiliColorHeader)));
        boolean dynamicSupported = DynamicColorSource.isSupported();
        boolean dynamic = dynamicSupported && YiliConfig.get(YiliConfig.DYNAMIC_COLOR);
        if (dynamicSupported) {
            items.add(UItem.asButtonCheck(ID_DYNAMIC, getString(R.string.YiliDynamicColor),
                    getString(R.string.YiliDynamicColorInfo)).setChecked(dynamic));
            if (dynamic) {
                items.add(UItem.asButtonCheck(ID_FOLLOW_SYSTEM, getString(R.string.YiliFollowSystemStyle),
                        getString(R.string.YiliFollowSystemStyleInfo)).setChecked(YiliConfig.get(YiliConfig.FOLLOW_SYSTEM_STYLE)));
            }
        }
        if (!dynamic) {
            seedPicker.setSelectedColor(YiliConfig.get(YiliConfig.SEED_COLOR));
            seedPicker.setBackgroundColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhite));
            items.add(UItem.asCustom(seedPicker));
        }
        if (!dynamic || !YiliConfig.get(YiliConfig.FOLLOW_SYSTEM_STYLE)) {
            items.add(UItem.asButton(ID_VARIANT, getString(R.string.YiliSchemeStyle),
                    variantName(YiliConfig.get(YiliConfig.SCHEME_VARIANT))));
        }
        items.add(UItem.asButton(ID_SPEC, getString(R.string.YiliColorSpec), specName(YiliConfig.get(YiliConfig.COLOR_SPEC))));
        items.add(UItem.asButtonCheck(ID_AMOLED, getString(R.string.YiliAmoled), getString(R.string.YiliAmoledInfo))
                .setChecked(YiliConfig.get(YiliConfig.AMOLED)));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.YiliContrast)));
        String[] contrasts = {getString(R.string.YiliContrastStandard), getString(R.string.YiliContrastMedium),
                getString(R.string.YiliContrastHigh)};
        items.add(UItem.asSlideView(contrasts, YiliConfig.get(YiliConfig.CONTRAST).ordinal(),
                i -> YiliConfig.set(YiliConfig.CONTRAST, ContrastLevel.values()[i])));
        items.add(UItem.asShadow(getString(R.string.YiliColorInfo)));

        // ---- Shape
        items.add(UItem.asHeader(getString(R.string.YiliShapeHeader)));
        items.add(UItem.asIntSlideView(1, 0, Math.round(YiliConfig.get(YiliConfig.SHAPE_ROUNDNESS) * 100), 200,
                val -> val + "%", val -> YiliConfig.set(YiliConfig.SHAPE_ROUNDNESS, val / 100f)));
        items.add(UItem.asShadow(null));

        // ---- Font
        items.add(UItem.asHeader(getString(R.string.YiliFontHeader)));
        items.add(UItem.asButton(ID_FONT, getString(R.string.YiliFont), fontName(YiliConfig.get(YiliConfig.FONT_FAMILY))));
        if (YiliConfig.get(YiliConfig.FONT_FAMILY) != FontFamily.SYSTEM) {
            items.add(UItem.asHeader(getString(R.string.YiliFontWeight)));
            items.add(UItem.asIntSlideView(1, -200, YiliConfig.get(YiliConfig.FONT_WEIGHT_DELTA), 200,
                    val -> (val > 0 ? "+" : "") + val, val -> {
                        YiliConfig.set(YiliConfig.FONT_WEIGHT_DELTA, Math.round(val / 50f) * 50);
                        restartHint();
                    }));
        }
        items.add(UItem.asShadow(getString(R.string.YiliFontInfo)));

        // ---- Motion
        items.add(UItem.asHeader(getString(R.string.YiliMotionHeader)));
        boolean animations = YiliConfig.get(YiliConfig.ANIMATIONS);
        items.add(UItem.asCheck(ID_ANIMATIONS, getString(R.string.YiliAnimations)).setChecked(animations));
        if (animations) {
            String[] styles = {getString(R.string.YiliMotionStandard), getString(R.string.YiliMotionExpressive)};
            items.add(UItem.asSlideView(styles, YiliConfig.get(YiliConfig.MOTION_SCHEME).ordinal(),
                    i -> YiliConfig.set(YiliConfig.MOTION_SCHEME, YiliMotion.Scheme.values()[i])));
            items.add(UItem.asHeader(getString(R.string.YiliMotionSpeed)));
            items.add(UItem.asIntSlideView(1, 50, Math.round(YiliConfig.get(YiliConfig.MOTION_SPEED) * 100), 200,
                    val -> String.format(Locale.US, "%.1f×", val / 100f),
                    val -> YiliConfig.set(YiliConfig.MOTION_SPEED, val / 100f)));
            items.add(UItem.asHeader(getString(R.string.YiliMotionBounce)));
            items.add(UItem.asIntSlideView(1, 0, Math.round(YiliConfig.get(YiliConfig.MOTION_BOUNCE) * 100), 100,
                    val -> val + "%", val -> YiliConfig.set(YiliConfig.MOTION_BOUNCE, val / 100f)));
        }
        items.add(UItem.asShadow(getString(R.string.YiliMotionInfo)));

        // ---- Configuration
        items.add(UItem.asHeader(getString(R.string.YiliConfigHeader)));
        items.add(UItem.asButton(ID_EXPORT, R.drawable.msg_share, getString(R.string.YiliExport)));
        items.add(UItem.asButton(ID_IMPORT, R.drawable.msg_download, getString(R.string.YiliImport)));
        items.add(UItem.asButton(ID_RESET, R.drawable.msg_reset, getString(R.string.YiliReset)).red());
        items.add(UItem.asShadow(null));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case ID_DYNAMIC:
                YiliConfig.set(YiliConfig.DYNAMIC_COLOR, !YiliConfig.get(YiliConfig.DYNAMIC_COLOR));
                break;
            case ID_FOLLOW_SYSTEM:
                YiliConfig.set(YiliConfig.FOLLOW_SYSTEM_STYLE, !YiliConfig.get(YiliConfig.FOLLOW_SYSTEM_STYLE));
                break;
            case ID_AMOLED:
                YiliConfig.set(YiliConfig.AMOLED, !YiliConfig.get(YiliConfig.AMOLED));
                break;
            case ID_ANIMATIONS:
                YiliConfig.set(YiliConfig.ANIMATIONS, !YiliConfig.get(YiliConfig.ANIMATIONS));
                break;
            case ID_VARIANT:
                showVariantPicker();
                break;
            case ID_FONT:
                showFontPicker();
                break;
            case ID_SPEC:
                showSpecPicker();
                break;
            case ID_EXPORT:
                exportConfig();
                break;
            case ID_IMPORT:
                Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                pick.setType("*/*");
                startActivityForResult(pick, REQUEST_IMPORT);
                break;
            case ID_RESET:
                new AlertDialog.Builder(getContext(), getResourceProvider())
                        .setMessage(getString(R.string.YiliResetConfirm))
                        .setPositiveButton(getString(R.string.YiliReset), (d, w) -> resetAll())
                        .setNegativeButton(getString(R.string.Cancel), null)
                        .makeRed(AlertDialog.BUTTON_POSITIVE)
                        .show();
                break;
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    private void showVariantPicker() {
        SchemeVariant[] values = SchemeVariant.values();
        CharSequence[] names = new CharSequence[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = variantName(values[i]);
        }
        new AlertDialog.Builder(getContext(), getResourceProvider())
                .setTitle(getString(R.string.YiliSchemeStyle))
                .setItems(names, (d, which) -> YiliConfig.set(YiliConfig.SCHEME_VARIANT, values[which]))
                .show();
    }

    private void showSpecPicker() {
        ColorSpecVersion[] values = ColorSpecVersion.values();
        CharSequence[] names = new CharSequence[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = specName(values[i]);
        }
        new AlertDialog.Builder(getContext(), getResourceProvider())
                .setTitle(getString(R.string.YiliColorSpec))
                .setItems(names, (d, which) -> YiliConfig.set(YiliConfig.COLOR_SPEC, values[which]))
                .show();
    }

    private void resetAll() {
        java.util.Map<YiliConfig.Key<?>, Object> defaults = new java.util.LinkedHashMap<>();
        for (YiliConfig.Key<?> key : YiliConfig.keys().values()) {
            defaults.put(key, key.defaultValue);
        }
        YiliConfig.setAll(defaults);
    }

    private void exportConfig() {
        try {
            File dir = new File(ApplicationLoader.getFilesDirFixed(), "cache");
            dir.mkdirs();
            File file = new File(dir, EXPORT_NAME);
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(YiliConfig.exportJson().toString(2).getBytes(StandardCharsets.UTF_8));
            }
            Uri uri = FileProvider.getUriForFile(getParentActivity(), ApplicationLoader.getApplicationId() + ".provider", file);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("application/json");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            getParentActivity().startActivity(Intent.createChooser(send, getString(R.string.YiliExport)));
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void showFontPicker() {
        FontFamily[] values = FontFamily.values();
        CharSequence[] names = new CharSequence[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = fontName(values[i]);
        }
        new AlertDialog.Builder(getContext(), getResourceProvider())
                .setTitle(getString(R.string.YiliFont))
                .setItems(names, (d, which) -> {
                    if (values[which] == FontFamily.CUSTOM) {
                        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        pick.addCategory(Intent.CATEGORY_OPENABLE);
                        pick.setType("*/*");
                        startActivityForResult(pick, REQUEST_FONT);
                    } else {
                        YiliConfig.set(YiliConfig.FONT_FAMILY, values[which]);
                        restartHint();
                    }
                })
                .show();
    }

    private void restartHint() {
        BulletinFactory.of(this).createSimpleBulletin(R.raw.info, getString(R.string.YiliFontRestart)).show();
    }

    private void importFont(Uri uri) {
        File target = YiliFonts.customFile();
        File tmp = new File(target.getPath() + ".tmp");
        try (InputStream in = getParentActivity().getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[16384];
            int total = 0, n;
            while (in != null && (n = in.read(buf)) > 0) {
                total += n;
                if (total > 32 * 1024 * 1024) {
                    throw new IllegalArgumentException("font file is too large");
                }
                out.write(buf, 0, n);
            }
        } catch (Exception e) {
            FileLog.e(e);
            BulletinFactory.of(this).createSimpleBulletin(R.raw.error, getString(R.string.YiliFontInvalid)).show();
            return;
        }
        // Reject files Android cannot read as a font before they replace the working one.
        if (android.os.Build.VERSION.SDK_INT >= 26 && new android.graphics.Typeface.Builder(tmp).build() == null) {
            tmp.delete();
            BulletinFactory.of(this).createSimpleBulletin(R.raw.error, getString(R.string.YiliFontInvalid)).show();
            return;
        }
        tmp.renameTo(target);
        YiliConfig.set(YiliConfig.FONT_FAMILY, FontFamily.CUSTOM);
        restartHint();
    }

    static String fontName(FontFamily f) {
        switch (f) {
            case ROBOTO_FLEX: return "Roboto Flex";
            case GOOGLE_SANS_FLEX: return getString(R.string.YiliFontGoogleSansFlex);
            case CUSTOM: return getString(R.string.YiliFontCustom);
            default: return getString(R.string.YiliFontSystem);
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_FONT && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            importFont(data.getData());
            return;
        }
        if (requestCode != REQUEST_IMPORT || resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        try (InputStream in = getParentActivity().getContentResolver().openInputStream(data.getData())) {
            byte[] bytes = new byte[256 * 1024];
            int total = 0, read;
            while (in != null && total < bytes.length && (read = in.read(bytes, total, bytes.length - total)) > 0) {
                total += read;
            }
            JSONObject root = new JSONObject(new String(bytes, 0, total, StandardCharsets.UTF_8));
            if (!root.has("yiligram")) {
                throw new IllegalArgumentException("not a YiliGram export");
            }
            ArrayList<String> skipped = YiliConfig.importJson(root);
            String message = skipped.isEmpty() ? getString(R.string.YiliImported)
                    : formatString(R.string.YiliImportSkipped, String.join(", ", skipped));
            BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, message).show();
        } catch (Exception e) {
            FileLog.e(e);
            BulletinFactory.of(this).createSimpleBulletin(R.raw.error, getString(R.string.YiliImportFailed)).show();
        }
    }

    static String variantName(SchemeVariant v) {
        switch (v) {
            case NEUTRAL: return getString(R.string.YiliVariantNeutral);
            case VIBRANT: return getString(R.string.YiliVariantVibrant);
            case EXPRESSIVE: return getString(R.string.YiliVariantExpressive);
            case FIDELITY: return getString(R.string.YiliVariantFidelity);
            case CONTENT: return getString(R.string.YiliVariantContent);
            case MONOCHROME: return getString(R.string.YiliVariantMonochrome);
            case RAINBOW: return getString(R.string.YiliVariantRainbow);
            case FRUIT_SALAD: return getString(R.string.YiliVariantFruitSalad);
            case TONAL_SPOT:
            default: return getString(R.string.YiliVariantTonalSpot);
        }
    }

    static String specName(ColorSpecVersion v) {
        switch (v) {
            case M3_2021: return getString(R.string.YiliSpec2021);
            case M3_2026: return getString(R.string.YiliSpec2026);
            case M3_EXPRESSIVE_2025:
            default: return getString(R.string.YiliSpec2025);
        }
    }
}
