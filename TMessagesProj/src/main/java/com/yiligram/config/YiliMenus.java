package com.yiligram.config;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import org.telegram.messenger.R;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Which items the message context menu shows. Hidden items are stored by stable name (not by
 * Telegram's option ids, which may change between versions). Delete, sending controls and the
 * items of sponsored messages are never hideable: the Telegram API terms require the latter.
 */
public final class YiliMenus {

    public enum MessageItem {
        REPLY(R.string.Reply, ChatActivity.OPTION_REPLY),
        COPY(R.string.Copy, ChatActivity.OPTION_COPY),
        COPY_LINK(R.string.CopyLink, ChatActivity.OPTION_COPY_LINK),
        FORWARD(R.string.Forward, ChatActivity.OPTION_FORWARD),
        PIN(R.string.PinMessage, ChatActivity.OPTION_PIN, ChatActivity.OPTION_UNPIN),
        EDIT(R.string.Edit, ChatActivity.OPTION_EDIT),
        TRANSLATE(R.string.TranslateMessage, ChatActivity.OPTION_TRANSLATE),
        SAVE(R.string.YiliMenuSave, ChatActivity.OPTION_SAVE_TO_GALLERY, ChatActivity.OPTION_SAVE_TO_GALLERY2,
                ChatActivity.OPTION_SAVE_TO_DOWNLOADS_OR_MUSIC),
        SHARE(R.string.ShareFile, ChatActivity.OPTION_SHARE),
        STICKERS(R.string.AddToStickers, ChatActivity.OPTION_ADD_TO_STICKERS_OR_MASKS,
                ChatActivity.OPTION_ADD_STICKER_TO_FAVORITES, ChatActivity.OPTION_DELETE_STICKER_FROM_FAVORITES),
        GIFS(R.string.SaveToGIFs, ChatActivity.OPTION_ADD_TO_GIFS),
        REPLIES(R.string.ViewThread, ChatActivity.OPTION_VIEW_REPLIES_OR_THREAD, ChatActivity.OPTION_VIEW_IN_TOPIC),
        STATISTICS(R.string.ViewStatistics, ChatActivity.OPTION_STATISTICS, ChatActivity.OPTION_VIEW_STATISTICS),
        FACT_CHECK(R.string.AddFactCheck, ChatActivity.OPTION_FACT_CHECK),
        REPORT(R.string.ReportChat, ChatActivity.OPTION_REPORT_CHAT),
        PREMIUM_PROMO(R.string.PremiumSpeedPromo, ChatActivity.OPTION_SPEED_PROMO);

        @StringRes
        public final int title;
        final int[] options;

        MessageItem(@StringRes int title, int... options) {
            this.title = title;
            this.options = options;
        }
    }

    private static String cachedRaw;
    private static Set<Integer> cachedOptions = new HashSet<>();

    private YiliMenus() {
    }

    @NonNull
    public static Set<MessageItem> hiddenMessageItems() {
        Set<MessageItem> out = new HashSet<>();
        for (String name : YiliConfig.get(YiliConfig.HIDDEN_MESSAGE_ITEMS).split(",")) {
            try {
                out.add(MessageItem.valueOf(name.trim()));
            } catch (IllegalArgumentException ignored) {
                // Empty string or an item removed in a later version.
            }
        }
        return out;
    }

    public static boolean isHidden(@NonNull MessageItem item) {
        return hiddenMessageItems().contains(item);
    }

    public static void setHidden(@NonNull MessageItem item, boolean hidden) {
        Set<MessageItem> set = hiddenMessageItems();
        if (hidden) {
            set.add(item);
        } else {
            set.remove(item);
        }
        StringBuilder sb = new StringBuilder();
        for (MessageItem i : MessageItem.values()) {
            if (set.contains(i)) {
                sb.append(sb.length() > 0 ? "," : "").append(i.name());
            }
        }
        YiliConfig.set(YiliConfig.HIDDEN_MESSAGE_ITEMS, sb.toString());
    }

    private static Set<Integer> hiddenOptions() {
        String raw = YiliConfig.get(YiliConfig.HIDDEN_MESSAGE_ITEMS);
        if (!raw.equals(cachedRaw)) {
            Set<Integer> ids = new HashSet<>();
            for (MessageItem i : hiddenMessageItems()) {
                for (int o : i.options) {
                    ids.add(o);
                }
            }
            cachedOptions = ids;
            cachedRaw = raw;
        }
        return cachedOptions;
    }

    /** Removes hidden items from the three parallel lists ChatActivity builds. */
    public static void filterMessageOptions(List<CharSequence> items, List<Integer> options, List<Integer> icons) {
        Set<Integer> hidden = hiddenOptions();
        if (hidden.isEmpty() || options.size() != items.size() || options.size() != icons.size()) {
            return;
        }
        for (int i = options.size() - 1; i >= 0; i--) {
            if (hidden.contains(options.get(i))) {
                items.remove(i);
                options.remove(i);
                icons.remove(i);
            }
        }
    }

    /** For tests and the settings screen. */
    @NonNull
    public static List<MessageItem> all() {
        List<MessageItem> l = new ArrayList<>();
        java.util.Collections.addAll(l, MessageItem.values());
        return l;
    }
}
