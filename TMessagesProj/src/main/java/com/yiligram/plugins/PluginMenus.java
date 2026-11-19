package com.yiligram.plugins;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shows plugins' {@code MenuItemData} entries in Telegram menus and builds the {@code on_click}
 * context documented by exteraGram (account, context, fragment, dialog_id, user, chat, message…).
 *
 * <p>Plugin entries get ids from {@link #ID_BASE} upwards, far from Telegram's own option and
 * menu ids; the list shown last for each menu maps an id back to the plugin item.
 */
public final class PluginMenus {
    public static final String MESSAGE_CONTEXT_MENU = "MESSAGE_CONTEXT_MENU";
    public static final String DRAWER_MENU = "DRAWER_MENU";
    public static final String MAIN_MENU = "MAIN_MENU";
    public static final String CHAT_ACTION_MENU = "CHAT_ACTION_MENU";
    public static final String PROFILE_ACTION_MENU = "PROFILE_ACTION_MENU";

    /** First option / menu id used for plugin entries. */
    public static final int ID_BASE = 0x5A000;
    private static final int ID_LIMIT = 1000;

    private static final Map<String, List<PluginEngine.MenuItem>> shown = new HashMap<>();

    private PluginMenus() {
    }

    public static boolean isPluginId(int id) {
        return id >= ID_BASE && id < ID_BASE + ID_LIMIT;
    }

    /** Current items of a menu; remembered so that a click can be mapped back. */
    @NonNull
    public static List<PluginEngine.MenuItem> items(@NonNull String menuType) {
        List<PluginEngine.MenuItem> list = Plugins.menuItems(menuType);
        if (list.size() > ID_LIMIT) {
            list = list.subList(0, ID_LIMIT);
        }
        shown.put(menuType, list);
        return list;
    }

    @Nullable
    private static PluginEngine.MenuItem itemFor(@NonNull String menuType, int id) {
        List<PluginEngine.MenuItem> list = shown.get(menuType);
        int index = id - ID_BASE;
        return list != null && index >= 0 && index < list.size() ? list.get(index) : null;
    }

    /** Drawable resource for a plugin icon name such as "msg_info"; a plugin icon by default. */
    public static int iconRes(@Nullable String name) {
        if (!TextUtils.isEmpty(name)) {
            Context c = ApplicationLoader.applicationContext;
            int res = c.getResources().getIdentifier(name, "drawable", c.getPackageName());
            if (res != 0) {
                return res;
            }
        }
        return R.drawable.msg_bots;
    }

    // ---------------------------------------------------------------- message context menu

    public static void addMessageOptions(ArrayList<CharSequence> items, ArrayList<Integer> options, ArrayList<Integer> icons) {
        List<PluginEngine.MenuItem> list = items(MESSAGE_CONTEXT_MENU);
        for (int i = 0; i < list.size(); i++) {
            items.add(list.get(i).text);
            options.add(ID_BASE + i);
            icons.add(iconRes(list.get(i).icon));
        }
    }

    public static void clickMessageOption(int option, @NonNull ChatActivity fragment,
                                          @Nullable MessageObject message, @Nullable MessageObject.GroupedMessages group) {
        PluginEngine.MenuItem item = itemFor(MESSAGE_CONTEXT_MENU, option);
        if (item == null) {
            return;
        }
        Map<String, Object> ctx = chatContext(fragment);
        ctx.put("message", message);
        ctx.put("groupedMessages", group);
        Plugins.clickMenuItem(item.id, ctx);
    }

    // ---------------------------------------------------------------- action bar ("⋮") menus

    /** Adds the items of {@code menuType} as sub-items of an action bar "other" menu. */
    public static void addSubItems(@NonNull String menuType, @Nullable ActionBarMenuItem menu) {
        if (menu == null) {
            return;
        }
        List<PluginEngine.MenuItem> list = items(menuType);
        for (int i = 0; i < list.size(); i++) {
            menu.lazilyAddSubItem(ID_BASE + i, iconRes(list.get(i).icon), list.get(i).text);
        }
    }

    /** Handles a click on an action bar item; false if the id is not a plugin item. */
    public static boolean onActionBarItem(@NonNull String menuType, int id, @NonNull Map<String, Object> ctx) {
        if (!isPluginId(id)) {
            return false;
        }
        PluginEngine.MenuItem item = itemFor(menuType, id);
        if (item != null) {
            Plugins.clickMenuItem(item.id, ctx);
        }
        return true;
    }

    // ---------------------------------------------------------------- main screen "⋮" menu

    /** MAIN_MENU and DRAWER_MENU items for the chat list's options popup. */
    public static void addItemOptions(@NonNull org.telegram.ui.Components.ItemOptions io, @NonNull BaseFragment fragment) {
        List<PluginEngine.MenuItem> all = new ArrayList<>(Plugins.menuItems(MAIN_MENU));
        all.addAll(Plugins.menuItems(DRAWER_MENU));
        if (all.isEmpty()) {
            return;
        }
        io.addGap();
        for (PluginEngine.MenuItem item : all) {
            io.add(iconRes(item.icon), item.text, () -> Plugins.clickMenuItem(item.id, baseContext(fragment)));
        }
    }

    // ---------------------------------------------------------------- contexts

    @NonNull
    public static Map<String, Object> baseContext(@Nullable BaseFragment fragment) {
        Map<String, Object> ctx = new HashMap<>();
        int account = fragment != null ? fragment.getCurrentAccount() : org.telegram.messenger.UserConfig.selectedAccount;
        ctx.put("account", account);
        ctx.put("fragment", fragment);
        ctx.put("context", fragment != null && fragment.getParentActivity() != null
                ? fragment.getParentActivity() : ApplicationLoader.applicationContext);
        return ctx;
    }

    @NonNull
    public static Map<String, Object> chatContext(@NonNull ChatActivity fragment) {
        Map<String, Object> ctx = baseContext(fragment);
        ctx.put("dialog_id", fragment.getDialogId());
        ctx.put("user", fragment.getCurrentUser());
        ctx.put("userId", fragment.getCurrentUser() != null ? fragment.getCurrentUser().id : 0L);
        ctx.put("userFull", fragment.getCurrentUserInfo());
        ctx.put("chat", fragment.getCurrentChat());
        ctx.put("chatId", fragment.getCurrentChat() != null ? fragment.getCurrentChat().id : 0L);
        ctx.put("chatFull", fragment.getCurrentChatInfo());
        ctx.put("encryptedChat", fragment.getCurrentEncryptedChat());
        return ctx;
    }

    @NonNull
    public static Map<String, Object> profileContext(@NonNull BaseFragment fragment, long userId, long chatId, long dialogId,
                                                     @Nullable Object userFull, @Nullable Object chatFull) {
        Map<String, Object> ctx = baseContext(fragment);
        ctx.put("dialog_id", dialogId != 0 ? dialogId : (userId != 0 ? userId : -chatId));
        ctx.put("userId", userId);
        ctx.put("user", userId != 0 ? fragment.getMessagesController().getUser(userId) : null);
        ctx.put("userFull", userFull);
        ctx.put("chatId", chatId);
        ctx.put("chat", chatId != 0 ? fragment.getMessagesController().getChat(chatId) : null);
        ctx.put("chatFull", chatFull);
        return ctx;
    }

    @NonNull
    public static List<PluginEngine.MenuItem> none() {
        return Collections.emptyList();
    }
}
