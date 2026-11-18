package com.yiligram.bridge;

import static com.yiligram.design.color.ColorRole.*;

import android.util.SparseArray;
import android.util.SparseIntArray;

import com.yiligram.design.color.ColorRole;
import com.yiligram.design.color.YiliColorScheme;

import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Telegram theme keys pinned to exact Material 3 roles (step 2 of {@link ThemeBridge}).
 *
 * <p>Alpha values follow M3 state layers: hover 8%, pressed/focus 10%, dragged 16%;
 * disabled content 38%, disabled containers 12%.
 */
final class RoleOverrides {

    private static final float PRESSED = 0.10f;

    /**
     * One override: theme key name (as in .attheme files, see ThemeColors.createColorKeysMap) → role
     * with alpha, or a light/dark pair.
     */
    private static final class Rule {
        final String key;
        /** For keys that have no .attheme name; -1 otherwise. */
        final int themeKey;
        final ColorRole light;
        final ColorRole dark;
        final float alpha;

        Rule(String key, int themeKey, ColorRole light, ColorRole dark, float alpha) {
            this.key = key;
            this.themeKey = themeKey;
            this.light = light;
            this.dark = dark;
            this.alpha = alpha;
        }
    }

    private static final List<Rule> RULES = new ArrayList<>();

    private static void r(String key, ColorRole role) {
        RULES.add(new Rule(key, -1, role, role, 1f));
    }

    private static void r(String key, ColorRole role, float alpha) {
        RULES.add(new Rule(key, -1, role, role, alpha));
    }

    /** Override for a key that has no .attheme name (not in ThemeColors.createColorKeysMap). */
    private static void rk(int themeKey, ColorRole role, float alpha) {
        RULES.add(new Rule(null, themeKey, role, role, alpha));
    }

    private static void ld(String key, ColorRole light, ColorRole dark) {
        RULES.add(new Rule(key, -1, light, dark, 1f));
    }

    static {
        // ---- Window, lists, sections -------------------------------------------------------
        r("windowBackgroundWhite", SURFACE);
        // Settings page behind section "cards": slightly darker than the sections in both modes.
        ld("windowBackgroundGray", SURFACE_CONTAINER, SURFACE_CONTAINER_LOWEST);
        r("windowBackgroundWhiteBlackText", ON_SURFACE);
        for (String k : new String[]{"windowBackgroundWhiteGrayText", "windowBackgroundWhiteGrayText2",
                "windowBackgroundWhiteGrayText3", "windowBackgroundWhiteGrayText4", "windowBackgroundWhiteGrayText5",
                "windowBackgroundWhiteGrayText6", "windowBackgroundWhiteGrayText7", "windowBackgroundWhiteGrayText8",
                "windowBackgroundWhiteHintText", "windowBackgroundWhiteGrayIcon", "key_graySectionText",
                "emptyListPlaceholder"}) {
            r(k, ON_SURFACE_VARIANT);
        }
        for (String k : new String[]{"windowBackgroundWhiteBlueText", "windowBackgroundWhiteBlueText2",
                "windowBackgroundWhiteBlueText3", "windowBackgroundWhiteBlueText4", "windowBackgroundWhiteBlueText5",
                "windowBackgroundWhiteBlueText6", "windowBackgroundWhiteBlueText7", "windowBackgroundWhiteBlueButton",
                "windowBackgroundWhiteBlueIcon", "windowBackgroundWhiteBlueHeader", "windowBackgroundWhiteLinkText",
                "windowBackgroundWhiteValueText", "windowBackgroundWhiteInputFieldActivated", "progressCircle",
                "fastScrollActive", "chat_status", "telegram_color", "telegram_color_text", "share_linkText",
                "share_icon", "botKeyboard_button_primary", "bot_loadingIcon"}) {
            r(k, PRIMARY);
        }
        r("windowBackgroundWhiteLinkSelection", PRIMARY, 0.20f);
        r("windowBackgroundWhiteInputField", OUTLINE);
        r("divider", OUTLINE_VARIANT);
        r("graySection", SURFACE_CONTAINER);
        r("listSelectorSDK21", ON_SURFACE, PRESSED);
        rk(org.telegram.ui.ActionBar.Theme.key_settings_listSelector, ON_SURFACE, PRESSED);
        r("fastScrollInactive", OUTLINE);
        r("fastScrollText", ON_PRIMARY);
        r("text_RedRegular", ERROR);
        r("text_RedBold", ERROR);
        r("fill_RedNormal", ERROR);
        r("telegram_color_dialogsLogo", ON_SURFACE);
        r("share_linkBackground", SURFACE_CONTAINER_HIGHEST);

        // ---- Switches, checkboxes, radios (M3 selection controls) ---------------------------
        for (String k : new String[]{"switchTrack", "switchTrackBlue", "switch2Track"}) {
            r(k, SURFACE_CONTAINER_HIGHEST);
        }
        for (String k : new String[]{"switchTrackChecked", "switchTrackBlueChecked", "switch2TrackChecked"}) {
            r(k, PRIMARY);
        }
        r("switchTrackBlueThumb", OUTLINE);
        r("switchTrackBlueThumbChecked", ON_PRIMARY);
        r("switchTrackBlueSelector", ON_SURFACE, PRESSED);
        r("switchTrackBlueSelectorChecked", PRIMARY, PRESSED);
        for (String k : new String[]{"checkbox", "checkboxSquareBackground", "dialogCheckboxSquareBackground",
                "dialogRoundCheckBox", "radioBackgroundChecked", "dialogRadioBackgroundChecked"}) {
            r(k, PRIMARY);
        }
        for (String k : new String[]{"checkboxCheck", "checkboxSquareCheck", "dialogCheckboxSquareCheck",
                "dialogRoundCheckBoxCheck"}) {
            r(k, ON_PRIMARY);
        }
        for (String k : new String[]{"checkboxSquareUnchecked", "dialogCheckboxSquareUnchecked", "radioBackground",
                "dialogRadioBackground"}) {
            r(k, ON_SURFACE_VARIANT);
        }
        r("checkboxDisabled", ON_SURFACE, 0.38f);
        r("checkboxSquareDisabled", ON_SURFACE, 0.38f);
        r("dialogCheckboxSquareDisabled", ON_SURFACE, 0.38f);

        // ---- Top app bar (M3: surface, title onSurface, scrolled → surfaceContainer) --------
        r("actionBarDefault", SURFACE);
        r("actionBarDefaultTitle", ON_SURFACE);
        r("actionBarDefaultIcon", ON_SURFACE);
        r("actionBarDefaultSubtitle", ON_SURFACE_VARIANT);
        r("actionBarDefaultSearch", ON_SURFACE);
        r("actionBarDefaultSearchPlaceholder", ON_SURFACE_VARIANT);
        r("actionBarDefaultSelector", ON_SURFACE, PRESSED);
        r("actionBarWhiteSelector", ON_SURFACE, PRESSED);
        r("actionBarBrowser", SURFACE);
        r("actionBarActionModeDefault", SURFACE_CONTAINER);
        r("actionBarActionModeDefaultTop", SURFACE_CONTAINER);
        r("actionBarActionModeDefaultIcon", ON_SURFACE);
        r("actionBarActionModeDefaultSelector", ON_SURFACE, PRESSED);
        r("actionBarTabActiveText", PRIMARY);
        r("actionBarTabUnactiveText", ON_SURFACE_VARIANT);
        r("actionBarTabLine", PRIMARY);
        r("actionBarTabSelector", ON_SURFACE, PRESSED);
        r("actionBarDefaultArchived", SURFACE_CONTAINER);
        r("actionBarDefaultArchivedTitle", ON_SURFACE);
        r("actionBarDefaultArchivedIcon", ON_SURFACE);
        r("actionBarDefaultArchivedSearch", ON_SURFACE);
        r("actionBarDefaultSearchArchivedPlaceholder", ON_SURFACE_VARIANT);
        r("actionBarDefaultArchivedSelector", ON_SURFACE, PRESSED);
        // Menus (M3: surfaceContainer, items onSurface, icons onSurfaceVariant)
        r("actionBarDefaultSubmenuBackground", SURFACE_CONTAINER);
        r("actionBarDefaultSubmenuItem", ON_SURFACE);
        r("actionBarDefaultSubmenuItemIcon", ON_SURFACE_VARIANT);
        r("actionBarDefaultSubmenuSeparator", SURFACE_CONTAINER_HIGHEST);

        // ---- Chat list ---------------------------------------------------------------------
        r("chats_name", ON_SURFACE);
        r("chats_nameMessage", ON_SURFACE);
        r("chats_nameMessage_threeLines", ON_SURFACE);
        r("chats_message", ON_SURFACE_VARIANT);
        r("chats_message_threeLines", ON_SURFACE_VARIANT);
        r("chats_date", ON_SURFACE_VARIANT);
        r("chats_attachMessage", PRIMARY);
        r("chats_actionMessage", PRIMARY);
        r("chats_draft", ERROR);
        r("chats_unreadCounter", PRIMARY);
        r("chats_unreadCounterText", ON_PRIMARY);
        r("chats_unreadCounterMuted", OUTLINE);
        r("chats_mentionIcon", ON_PRIMARY);
        r("chats_pinnedIcon", ON_SURFACE_VARIANT);
        r("chats_muteIcon", ON_SURFACE_VARIANT);
        r("chats_sentCheck", PRIMARY);
        r("chats_sentReadCheck", PRIMARY);
        r("chats_sentClock", ON_SURFACE_VARIANT);
        r("chats_sentError", ERROR);
        r("chats_sentErrorIcon", ON_ERROR);
        r("chats_verifiedBackground", PRIMARY);
        r("chats_verifiedCheck", ON_PRIMARY);
        r("chats_pinnedOverlay", SURFACE_CONTAINER_LOW);
        r("chats_tabletSelectedOverlay", SECONDARY_CONTAINER);
        r("chats_archiveBackground", SECONDARY_CONTAINER);
        r("chats_archiveIcon", ON_SECONDARY_CONTAINER);
        r("chats_archivePinBackground", TERTIARY_CONTAINER);
        r("chats_archiveText", ON_SURFACE_VARIANT);
        r("chats_archivePullDownBackground", SURFACE_CONTAINER_HIGH);
        r("chats_archivePullDownBackgroundActive", PRIMARY_CONTAINER);
        r("chats_tabUnreadActiveBackground", PRIMARY);
        r("chats_tabUnreadUnactiveBackground", OUTLINE);
        r("topics_unreadCounter", PRIMARY);
        r("topics_unreadCounterMuted", OUTLINE);
        // FAB (M3: primaryContainer / onPrimaryContainer)
        r("chats_actionBackground", PRIMARY_CONTAINER);
        r("chats_actionIcon", ON_PRIMARY_CONTAINER);
        r("chats_actionPressedBackground", PRIMARY_CONTAINER);
        // Navigation drawer
        r("chats_menuBackground", SURFACE_CONTAINER_LOW);
        r("chats_menuItemText", ON_SURFACE_VARIANT);
        r("chats_menuItemIcon", ON_SURFACE_VARIANT);
        r("chats_menuItemCheck", PRIMARY);
        r("chats_menuTopBackground", SURFACE_CONTAINER_HIGH);
        r("chats_menuTopBackgroundCats", SURFACE_CONTAINER_HIGH);
        r("chats_menuName", ON_SURFACE);
        r("chats_menuPhone", ON_SURFACE_VARIANT);
        r("chats_menuPhoneCats", ON_SURFACE_VARIANT);

        // ---- Dialogs and bottom sheets (M3: surfaceContainerHigh) --------------------------
        r("dialogBackground", SURFACE_CONTAINER_HIGH);
        r("dialogBackgroundGray", SURFACE_CONTAINER);
        r("dialogTextBlack", ON_SURFACE);
        for (String k : new String[]{"dialogTextGray2", "dialogTextGray3", "dialogTextGray4", "dialogTextHint",
                "dialogIcon", "dialogSearchHint", "dialogSearchIcon", "dialogEmptyText"}) {
            r(k, ON_SURFACE_VARIANT);
        }
        for (String k : new String[]{"dialogTextLink", "dialogTextBlue", "dialogTextBlue2", "dialogTextBlue4",
                "dialogTextGray", "dialogButton", "dialogTopBackground", "dialogLineProgress",
                "dialogInputFieldActivated", "dialog_inlineProgress"}) {
            r(k, PRIMARY);
        }
        r("dialogLinkSelection", PRIMARY, 0.20f);
        r("dialogButtonSelector", PRIMARY, PRESSED);
        r("dialogInputField", OUTLINE);
        r("dialogGrayLine", OUTLINE_VARIANT);
        r("dialogLineProgressBackground", SECONDARY_CONTAINER);
        r("dialog_inlineProgressBackground", SECONDARY_CONTAINER);
        r("dialogSearchBackground", SURFACE_CONTAINER_HIGHEST);
        r("dialogSearchText", ON_SURFACE);
        r("dialogFloatingButton", PRIMARY_CONTAINER);
        r("dialogFloatingButtonPressed", PRIMARY_CONTAINER);
        r("dialogFloatingIcon", ON_PRIMARY_CONTAINER);
        r("dialogScrollGlow", SURFACE_CONTAINER_HIGHEST);

        // ---- Chat --------------------------------------------------------------------------
        r("chat_wallpaper", SURFACE);
        r("chat_inBubble", SURFACE_CONTAINER_HIGH);
        r("chat_inBubbleSelected", SURFACE_CONTAINER_HIGHEST);
        r("chat_outBubble", PRIMARY_CONTAINER);
        r("chat_outBubbleGradient", PRIMARY_CONTAINER);
        r("chat_messageTextIn", ON_SURFACE);
        r("chat_messageTextOut", ON_PRIMARY_CONTAINER);
        r("chat_messageLinkIn", PRIMARY);
        r("chat_messageLinkOut", PRIMARY);
        r("chat_inTimeText", ON_SURFACE_VARIANT);
        r("chat_inViews", ON_SURFACE_VARIANT);
        r("chat_inSentClock", ON_SURFACE_VARIANT);
        r("chat_inMenu", ON_SURFACE_VARIANT);
        r("chat_outTimeText", ON_PRIMARY_CONTAINER, 0.72f);
        r("chat_outViews", ON_PRIMARY_CONTAINER, 0.72f);
        r("chat_outSentClock", ON_PRIMARY_CONTAINER, 0.72f);
        r("chat_outMenu", ON_PRIMARY_CONTAINER, 0.72f);
        r("chat_outSentCheck", PRIMARY);
        r("chat_outSentCheckRead", PRIMARY);
        for (String k : new String[]{"chat_inReplyLine", "chat_inReplyNameText", "chat_inForwardedNameText",
                "chat_inSiteNameText", "chat_inLoader", "chat_inVoiceSeekbarFill", "chat_inQuote",
                "chat_outReplyLine", "chat_outReplyNameText", "chat_outForwardedNameText", "chat_outSiteNameText",
                "chat_outLoader", "chat_outVoiceSeekbarFill", "chat_outQuote"}) {
            r(k, PRIMARY);
        }
        r("chat_inReplyMessageText", ON_SURFACE);
        r("chat_outReplyMessageText", ON_PRIMARY_CONTAINER);
        r("chat_inTextSelectionHighlight", PRIMARY, 0.25f);
        r("chat_outTextSelectionHighlight", PRIMARY, 0.25f);
        r("chat_TextSelectionCursor", PRIMARY);
        r("chat_outTextSelectionCursor", PRIMARY);
        // Input panel (M3 bottom toolbar)
        r("chat_messagePanelBackground", SURFACE_CONTAINER);
        r("chat_messagePanelText", ON_SURFACE);
        r("chat_messagePanelHint", ON_SURFACE_VARIANT);
        r("chat_messagePanelCursor", PRIMARY);
        r("chat_messagePanelIcons", ON_SURFACE_VARIANT);
        r("chat_messagePanelSend", PRIMARY);
        r("chat_messagePanelVoiceBackground", PRIMARY);
        r("chat_messagePanelVoicePressed", ON_PRIMARY);
        r("chat_emojiPanelBackground", SURFACE_CONTAINER_LOW);
        r("chat_emojiPanelIcon", ON_SURFACE_VARIANT);
        r("chat_emojiPanelIconSelected", PRIMARY);
        r("chat_topPanelBackground", SURFACE_CONTAINER);
        r("chat_topPanelTitle", PRIMARY);
        r("chat_topPanelLine", PRIMARY);
        r("chat_topPanelMessage", ON_SURFACE_VARIANT);
        r("chat_topPanelClose", ON_SURFACE_VARIANT);
        r("chat_replyPanelIcons", PRIMARY);
        r("chat_replyPanelName", PRIMARY);
        r("chat_replyPanelLine", OUTLINE_VARIANT);
        r("chat_replyPanelClose", ON_SURFACE_VARIANT);
        r("chat_goDownButton", SURFACE_CONTAINER_HIGH);
        r("chat_goDownButtonCounter", ON_PRIMARY);
        r("chat_goDownButtonCounterBackground", PRIMARY);
        r("chat_unreadMessagesStartBackground", SURFACE_CONTAINER);
        r("chat_unreadMessagesStartText", ON_SURFACE_VARIANT);
        r("chat_unreadMessagesStartArrowIcon", ON_SURFACE_VARIANT);
        r("chat_selectedBackground", PRIMARY, 0.12f);

        // ---- Profile -----------------------------------------------------------------------
        r("avatar_backgroundActionBarBlue", SURFACE);
        r("avatar_actionBarIconBlue", ON_SURFACE);
        r("avatar_actionBarSelectorBlue", ON_SURFACE, PRESSED);
        r("avatar_subtitleInProfileBlue", ON_SURFACE_VARIANT);
        r("avatar_backgroundInProfileBlue", PRIMARY_CONTAINER);
        r("profile_title", ON_SURFACE);
        r("profile_status", ON_SURFACE_VARIANT);
        r("profile_actionBackground", PRIMARY_CONTAINER);
        r("profile_actionPressedBackground", PRIMARY_CONTAINER);
        r("profile_actionIcon", ON_PRIMARY_CONTAINER);
        r("profile_tabText", ON_SURFACE_VARIANT);
        r("profile_tabSelectedText", PRIMARY);
        r("profile_tabSelectedLine", PRIMARY);
        r("profile_tabSelector", ON_SURFACE, PRESSED);
        r("profile_verifiedBackground", PRIMARY);
        r("profile_verifiedCheck", ON_PRIMARY);

        // ---- Snackbars (undo bulletins), mini player, misc ----------------------------------
        r("undo_background", INVERSE_SURFACE);
        r("undo_infoColor", INVERSE_ON_SURFACE);
        r("undo_cancelColor", INVERSE_PRIMARY);
        r("inappPlayerBackground", SURFACE_CONTAINER);
        r("inappPlayerTitle", ON_SURFACE);
        r("inappPlayerPerformer", ON_SURFACE_VARIANT);
        r("inappPlayerPlayPause", PRIMARY);
        r("inappPlayerClose", ON_SURFACE_VARIANT);
        r("contacts_inviteBackground", PRIMARY_CONTAINER);
        r("contacts_inviteText", ON_PRIMARY_CONTAINER);
        r("buttonNeutral", SECONDARY_CONTAINER);
        r("buttonNeutralText", ON_SECONDARY_CONTAINER);
        r("files_folderIcon", ON_PRIMARY_CONTAINER);
        r("files_folderIconBackground", PRIMARY_CONTAINER);
    }

    private static Map<String, Integer> keysByName;

    private RoleOverrides() {
    }

    static void apply(SparseIntArray out, YiliColorScheme s, SparseArray<String> names) {
        if (keysByName == null) {
            Map<String, Integer> m = new HashMap<>();
            for (int i = 0; i < names.size(); i++) {
                m.put(names.valueAt(i), names.keyAt(i));
            }
            keysByName = m;
        }
        boolean dark = s.spec.dark;
        for (Rule rule : RULES) {
            Integer key = rule.themeKey >= 0 ? Integer.valueOf(rule.themeKey) : keysByName.get(rule.key);
            if (key == null) {
                // Upstream renamed or removed a key: visible in debug logs, harmless otherwise.
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("YiliGram ThemeBridge: unknown theme key " + rule.key);
                }
                continue;
            }
            int color = s.get(dark ? rule.dark : rule.light);
            if (rule.alpha < 1f) {
                int a = Math.round(((color >>> 24) & 0xFF) * rule.alpha);
                color = (a << 24) | (color & 0x00FFFFFF);
            }
            out.put(key, color);
        }
    }

    /** Names of all overridden keys (for tests). */
    static List<String> overriddenKeys() {
        List<String> keys = new ArrayList<>();
        for (Rule rule : RULES) {
            if (rule.key != null) {
                keys.add(rule.key);
            }
        }
        return keys;
    }
}
