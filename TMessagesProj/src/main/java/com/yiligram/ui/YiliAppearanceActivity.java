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
import com.yiligram.config.YiliPresets;
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
    private static final int ID_MESSAGE_MENU = 11;
    private static final int ID_HIDE_CONTACTS = 12;
    private static final int ID_HIDE_PROFILE = 13;
    private static final int ID_GIFT_BUTTON = 14;
    private static final int ID_VOICE_ONLY = 15;
    private static final int ID_TAB_LABELS = 16;
    private static final int ID_HEADER_CALL = 17;
    private static final int ID_BUBBLE_TAILS = 18;
    private static final int ID_M3_CONTROLS = 19;
    private static final int ID_UPDATE_AUTO = 20;
    private static final int ID_UPDATE_CHECK = 21;
    private static final int ID_TEXT_ANIMATION = 22;
    private static final int ID_ICON_PACK = 23;
    private static final int ID_ICON_IMPORT = 24;
    private static final int ID_ICON_DELETE = 25;
    private static final int ID_AVATAR_FRAME = 26;
    private static final int ID_BUBBLE_SHAPE = 90;
    private static final int ID_APP_ICON = 91;
    private static final int ID_TABS_HIDE_ON_SCROLL = 92;
    private static final int ID_WALLPAPER = 93;
    private static final int ID_SHAPE_MORPH = 94;
    private static final int ID_M3_PROFILE = 95;
    private static final int[] WALLPAPER_TITLES = {R.string.YiliWallpaperOff, R.string.YiliWallpaperShapes, R.string.YiliWallpaperBubbles, R.string.YiliWallpaperWaves};
    private static final int[] BUBBLE_SHAPE_TITLES = {R.string.YiliBubbleRounded, R.string.YiliBubblePill, R.string.YiliBubbleM3};
    private static final int REQUEST_ICON_PACK = 7304;
    private static final String EXPORT_NAME = "yiligram-settings.yiliconfig";

    private AppearancePreviewView preview;
    private SeedColorPickerView seedPicker;
    private PresetPickerView presetPicker;

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
        presetPicker = new PresetPickerView(context);
        presetPicker.setOnSelect(YiliPresets::apply);
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

        // ---- Presets
        items.add(UItem.asHeader(getString(R.string.YiliPresetsHeader)));
        presetPicker.refresh();
        items.add(UItem.asCustom(presetPicker));
        items.add(UItem.asShadow(getString(R.string.YiliPresetsInfo)));

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
        items.add(UItem.asButton(ID_APP_ICON, getString(R.string.AppIcon), getString(currentAppIcon().title)));
        items.add(UItem.asButton(ID_AVATAR_FRAME, getString(R.string.AvatarFrame),
                getString(com.yiligram.avatars.AvatarFrames.current().title)));
        items.add(UItem.asButtonCheck(ID_M3_PROFILE, getString(R.string.YiliM3Profile), getString(R.string.YiliM3ProfileInfo))
                .setChecked(YiliConfig.get(YiliConfig.M3_PROFILE)));
        items.add(UItem.asButtonCheck(ID_SHAPE_MORPH, getString(R.string.YiliShapeMorph), getString(R.string.YiliShapeMorphInfo))
                .setChecked(YiliConfig.get(YiliConfig.SHAPE_MORPH)));
        items.add(UItem.asButton(ID_WALLPAPER, getString(R.string.YiliWallpaper),
                getString(WALLPAPER_TITLES[YiliConfig.get(YiliConfig.M3_WALLPAPER).ordinal()])));
        items.add(UItem.asButton(ID_BUBBLE_SHAPE, getString(R.string.YiliBubbleShape),
                getString(BUBBLE_SHAPE_TITLES[YiliConfig.get(YiliConfig.BUBBLE_SHAPE).ordinal()])));
        items.add(UItem.asButtonCheck(ID_BUBBLE_TAILS, getString(R.string.YiliBubbleTails), getString(R.string.YiliBubbleTailsInfo))
                .setChecked(YiliConfig.get(YiliConfig.BUBBLE_TAILS)));
        items.add(UItem.asButtonCheck(ID_M3_CONTROLS, getString(R.string.YiliM3Controls), getString(R.string.YiliM3ControlsInfo))
                .setChecked(YiliConfig.get(YiliConfig.M3_CONTROLS)));
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

        // ---- Icons
        items.add(UItem.asHeader(getString(R.string.YiliIconsHeader)));
        items.add(UItem.asButton(ID_ICON_PACK, getString(R.string.YiliIconPack), iconPackName()));
        items.add(UItem.asButton(ID_ICON_IMPORT, R.drawable.msg_download, getString(R.string.YiliIconImport)));
        if (!YiliConfig.get(YiliConfig.ICON_PACK).isEmpty()) {
            items.add(UItem.asButton(ID_ICON_DELETE, R.drawable.msg_delete, getString(R.string.YiliIconDelete)).red());
        }
        items.add(UItem.asShadow(getString(R.string.YiliIconInfo)));

        // ---- Interface scale (density)
        items.add(UItem.asHeader(getString(R.string.YiliUiScale)));
        items.add(UItem.asIntSlideView(1, Math.round(com.yiligram.config.YiliDensity.MIN * 100),
                Math.round(YiliConfig.get(YiliConfig.UI_SCALE) * 100), Math.round(com.yiligram.config.YiliDensity.MAX * 100),
                val -> val + "%", val -> {
                    YiliConfig.set(YiliConfig.UI_SCALE, Math.round(val / 5f) * 5 / 100f);
                    restartHint(R.string.YiliUiScaleRestart);
                }));
        items.add(UItem.asShadow(getString(R.string.YiliUiScaleInfo)));

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

        // ---- Navigation
        items.add(UItem.asHeader(getString(R.string.YiliNavHeader)));
        items.add(UItem.asCheck(ID_TAB_LABELS, getString(R.string.YiliNavLabels)).setChecked(YiliConfig.get(YiliConfig.TAB_LABELS)));
        items.add(UItem.asCheck(ID_TABS_HIDE_ON_SCROLL, getString(R.string.YiliNavHideOnScroll)).setChecked(YiliConfig.get(YiliConfig.TABS_HIDE_ON_SCROLL)));
        items.add(UItem.asCheck(ID_HIDE_CONTACTS, getString(R.string.YiliNavContacts)).setChecked(!YiliConfig.get(YiliConfig.HIDE_CONTACTS_TAB)));
        items.add(UItem.asCheck(ID_HIDE_PROFILE, getString(R.string.YiliNavProfile)).setChecked(!YiliConfig.get(YiliConfig.HIDE_PROFILE_TAB)));
        items.add(UItem.asShadow(getString(R.string.YiliNavInfo)));

        // ---- Chat header
        items.add(UItem.asHeader(getString(R.string.YiliHeaderHeader)));
        items.add(UItem.asButtonCheck(ID_HEADER_CALL, getString(R.string.YiliHeaderCall), getString(R.string.YiliHeaderCallInfo))
                .setChecked(YiliConfig.get(YiliConfig.HEADER_CALL_BUTTON)));
        items.add(UItem.asShadow(null));

        // ---- Input panel
        items.add(UItem.asHeader(getString(R.string.YiliInputHeader)));
        items.add(UItem.asButtonCheck(ID_TEXT_ANIMATION, getString(R.string.YiliTextAnimation), getString(R.string.YiliTextAnimationInfo))
                .setChecked(YiliConfig.get(YiliConfig.TEXT_ANIMATION)));
        items.add(UItem.asCheck(ID_GIFT_BUTTON, getString(R.string.YiliInputGift)).setChecked(!YiliConfig.get(YiliConfig.HIDE_GIFT_BUTTON)));
        items.add(UItem.asButtonCheck(ID_VOICE_ONLY, getString(R.string.YiliInputVoiceOnly), getString(R.string.YiliInputVoiceOnlyInfo))
                .setChecked(YiliConfig.get(YiliConfig.VOICE_ONLY)));
        items.add(UItem.asShadow(getString(R.string.YiliInputInfo)));

        // ---- Menus
        items.add(UItem.asHeader(getString(R.string.YiliMenusHeader)));
        int hiddenCount = com.yiligram.config.YiliMenus.hiddenMessageItems().size();
        items.add(UItem.asButton(ID_MESSAGE_MENU, R.drawable.msg_list, getString(R.string.YiliMenuMessage),
                hiddenCount == 0 ? getString(R.string.YiliMenuAllShown)
                        : org.telegram.messenger.LocaleController.formatPluralString("YiliMenuHidden", hiddenCount)));
        items.add(UItem.asShadow(null));

        // ---- Updates (GitHub Releases)
        items.add(UItem.asHeader(getString(R.string.YiliUpdatesHeader)));
        items.add(UItem.asCheck(ID_UPDATE_AUTO, getString(R.string.YiliUpdateAuto)).setChecked(YiliConfig.get(YiliConfig.AUTO_UPDATE_CHECK)));
        items.add(UItem.asButton(ID_UPDATE_CHECK, R.drawable.msg_retry, getString(R.string.YiliUpdateCheck)));
        items.add(UItem.asShadow(formatString(R.string.YiliUpdateInfo, org.telegram.messenger.BuildConfig.YILI_RELEASES_URL)));

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
            case ID_HIDE_CONTACTS:
                YiliConfig.set(YiliConfig.HIDE_CONTACTS_TAB, !YiliConfig.get(YiliConfig.HIDE_CONTACTS_TAB));
                break;
            case ID_HIDE_PROFILE:
                YiliConfig.set(YiliConfig.HIDE_PROFILE_TAB, !YiliConfig.get(YiliConfig.HIDE_PROFILE_TAB));
                break;
            case ID_AVATAR_FRAME:
                showAvatarFramePicker();
                break;
            case ID_ICON_PACK:
                showIconPackPicker();
                break;
            case ID_ICON_IMPORT: {
                Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                pick.setType("*/*");
                startActivityForResult(pick, REQUEST_ICON_PACK);
                break;
            }
            case ID_ICON_DELETE: {
                String id = YiliConfig.get(YiliConfig.ICON_PACK);
                new AlertDialog.Builder(getContext(), getResourceProvider())
                        .setMessage(formatString(R.string.YiliIconDeleteConfirm, iconPackName()))
                        .setPositiveButton(getString(R.string.Delete), (d, w) -> {
                            com.yiligram.icons.IconPacks.delete(id);
                            restartHint(R.string.YiliIconRestart);
                        })
                        .setNegativeButton(getString(R.string.Cancel), null)
                        .makeRed(AlertDialog.BUTTON_POSITIVE)
                        .show();
                break;
            }
            case ID_TEXT_ANIMATION:
                YiliConfig.set(YiliConfig.TEXT_ANIMATION, !YiliConfig.get(YiliConfig.TEXT_ANIMATION));
                break;
            case ID_UPDATE_AUTO:
                YiliConfig.set(YiliConfig.AUTO_UPDATE_CHECK, !YiliConfig.get(YiliConfig.AUTO_UPDATE_CHECK));
                break;
            case ID_UPDATE_CHECK:
                checkForUpdates();
                break;
            case ID_M3_CONTROLS:
                YiliConfig.set(YiliConfig.M3_CONTROLS, !YiliConfig.get(YiliConfig.M3_CONTROLS));
                break;
            case ID_APP_ICON: {
                org.telegram.ui.LauncherIconController.LauncherIcon[] icons = org.telegram.ui.LauncherIconController.LauncherIcon.values();
                CharSequence[] names = new CharSequence[icons.length];
                for (int i = 0; i < icons.length; i++) names[i] = getString(icons[i].title);
                new AlertDialog.Builder(getContext(), getResourceProvider())
                        .setTitle(getString(R.string.AppIcon))
                        .setItems(names, (d, which) -> {
                            org.telegram.ui.LauncherIconController.setIcon(icons[which]);
                            listView.adapter.update(true);
                        })
                        .show();
                break;
            }
            case ID_M3_PROFILE:
                YiliConfig.set(YiliConfig.M3_PROFILE, !YiliConfig.get(YiliConfig.M3_PROFILE));
                listView.adapter.update(true);
                break;
            case ID_SHAPE_MORPH:
                YiliConfig.set(YiliConfig.SHAPE_MORPH, !YiliConfig.get(YiliConfig.SHAPE_MORPH));
                listView.adapter.update(true);
                break;
            case ID_WALLPAPER: {
                com.yiligram.wallpaper.YiliWallpaper.Style[] values = com.yiligram.wallpaper.YiliWallpaper.Style.values();
                CharSequence[] names = new CharSequence[values.length];
                for (int i = 0; i < values.length; i++) names[i] = getString(WALLPAPER_TITLES[i]);
                new AlertDialog.Builder(getContext(), getResourceProvider())
                        .setTitle(getString(R.string.YiliWallpaper))
                        .setItems(names, (d, which) -> {
                            YiliConfig.set(YiliConfig.M3_WALLPAPER, values[which]);
                            org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(org.telegram.messenger.NotificationCenter.didSetNewWallpapper);
                            listView.adapter.update(true);
                        })
                        .show();
                break;
            }
            case ID_BUBBLE_SHAPE: {
                YiliConfig.BubbleShape[] values = YiliConfig.BubbleShape.values();
                CharSequence[] names = new CharSequence[values.length];
                for (int i = 0; i < values.length; i++) names[i] = getString(BUBBLE_SHAPE_TITLES[i]);
                new AlertDialog.Builder(getContext(), getResourceProvider())
                        .setTitle(getString(R.string.YiliBubbleShape))
                        .setItems(names, (d, which) -> {
                            YiliConfig.set(YiliConfig.BUBBLE_SHAPE, values[which]);
                            listView.adapter.update(true);
                        })
                        .show();
                break;
            }
            case ID_BUBBLE_TAILS:
                YiliConfig.set(YiliConfig.BUBBLE_TAILS, !YiliConfig.get(YiliConfig.BUBBLE_TAILS));
                break;
            case ID_HEADER_CALL:
                YiliConfig.set(YiliConfig.HEADER_CALL_BUTTON, !YiliConfig.get(YiliConfig.HEADER_CALL_BUTTON));
                break;
            case ID_TABS_HIDE_ON_SCROLL:
                YiliConfig.set(YiliConfig.TABS_HIDE_ON_SCROLL, !YiliConfig.get(YiliConfig.TABS_HIDE_ON_SCROLL));
                listView.adapter.update(true);
                break;
            case ID_TAB_LABELS:
                YiliConfig.set(YiliConfig.TAB_LABELS, !YiliConfig.get(YiliConfig.TAB_LABELS));
                break;
            case ID_GIFT_BUTTON:
                YiliConfig.set(YiliConfig.HIDE_GIFT_BUTTON, !YiliConfig.get(YiliConfig.HIDE_GIFT_BUTTON));
                break;
            case ID_VOICE_ONLY:
                YiliConfig.set(YiliConfig.VOICE_ONLY, !YiliConfig.get(YiliConfig.VOICE_ONLY));
                break;
            case ID_MESSAGE_MENU:
                presentFragment(new YiliMenusActivity());
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

    private static org.telegram.ui.LauncherIconController.LauncherIcon currentAppIcon() {
        for (org.telegram.ui.LauncherIconController.LauncherIcon icon : org.telegram.ui.LauncherIconController.LauncherIcon.values()) {
            if (org.telegram.ui.LauncherIconController.isEnabled(icon)) return icon;
        }
        return org.telegram.ui.LauncherIconController.LauncherIcon.DEFAULT;
    }

    private void showAvatarFramePicker() {
        com.yiligram.avatars.AvatarFrames.Frame[] values = com.yiligram.avatars.AvatarFrames.Frame.values();
        CharSequence[] names = new CharSequence[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = getString(values[i].title);
        }
        new AlertDialog.Builder(getContext(), getResourceProvider())
                .setTitle(getString(R.string.AvatarFrame))
                .setItems(names, (d, which) -> {
                    YiliConfig.set(YiliConfig.AVATAR_FRAME, values[which]);
                    listView.adapter.update(true);
                })
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

    private String iconPackName() {
        String id = YiliConfig.get(YiliConfig.ICON_PACK);
        if (id.isEmpty()) {
            return getString(R.string.YiliIconDefault);
        }
        for (com.yiligram.icons.IconPacks.Pack p : com.yiligram.icons.IconPacks.list()) {
            if (p.id.equals(id)) {
                return p.name;
            }
        }
        return id;
    }

    private void showIconPackPicker() {
        java.util.List<com.yiligram.icons.IconPacks.Pack> packs = com.yiligram.icons.IconPacks.list();
        CharSequence[] names = new CharSequence[packs.size() + 1];
        names[0] = getString(R.string.YiliIconDefault);
        for (int i = 0; i < packs.size(); i++) {
            com.yiligram.icons.IconPacks.Pack p = packs.get(i);
            names[i + 1] = p.name + " · " + formatString(R.string.YiliIconCount, p.count)
                    + (p.author.isEmpty() ? "" : " · " + p.author);
        }
        new AlertDialog.Builder(getContext(), getResourceProvider())
                .setTitle(getString(R.string.YiliIconPack))
                .setItems(names, (d, which) -> {
                    String id = which == 0 ? "" : packs.get(which - 1).id;
                    if (!id.equals(YiliConfig.get(YiliConfig.ICON_PACK))) {
                        YiliConfig.set(YiliConfig.ICON_PACK, id);
                        restartHint(R.string.YiliIconRestart);
                    }
                })
                .show();
    }

    private void importIconPack(Uri uri) {
        String name = null;
        try (android.database.Cursor c = getParentActivity().getContentResolver().query(uri, new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                name = c.getString(0);
            }
        } catch (Exception ignored) {
        }
        try (InputStream in = getParentActivity().getContentResolver().openInputStream(uri)) {
            com.yiligram.icons.IconPacks.Pack p = com.yiligram.icons.IconPacks.install(in, name);
            YiliConfig.set(YiliConfig.ICON_PACK, p.id);
            BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check,
                    formatString(R.string.YiliIconInstalled, p.name, p.count)).show();
        } catch (Exception e) {
            FileLog.e(e);
            BulletinFactory.of(this).createSimpleBulletin(R.raw.error,
                    formatString(R.string.YiliIconFailed, e.getMessage() == null ? e.toString() : e.getMessage())).show();
        }
    }

    private void checkForUpdates() {
        org.telegram.messenger.ApplicationLoader loader = org.telegram.messenger.ApplicationLoader.applicationLoaderInstance;
        BulletinFactory.of(this).createSimpleBulletin(R.raw.info, getString(R.string.YiliUpdateChecking)).show();
        loader.checkUpdate(true, () -> {
            org.telegram.messenger.BetaUpdate update = loader.getUpdate();
            if (getParentActivity() == null) {
                return;
            }
            if (update != null) {
                loader.showCustomUpdateAppPopup(getParentActivity(), update, currentAccount);
            } else {
                BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip, getString(R.string.YourVersionIsLatest)).show();
            }
        });
    }

    private void restartHint() {
        restartHint(R.string.YiliFontRestart);
    }

    private void restartHint(int text) {
        if (org.telegram.ui.Components.Bulletin.getVisibleBulletin() == null) {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.info, getString(text)).show();
        }
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
        if (requestCode == REQUEST_ICON_PACK && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            importIconPack(data.getData());
            return;
        }
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
