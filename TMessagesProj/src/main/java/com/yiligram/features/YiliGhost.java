package com.yiligram.features;

import com.yiligram.config.YiliConfig;

import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.tgnet.tl.TL_stories;

/**
 * Ghost mode: read receipts, typing, online status and story views are not sent while it is on. Messages are
 * still marked read on this device.
 */
public final class YiliGhost {

    private YiliGhost() {
    }

    public static boolean enabled() {
        return YiliConfig.get(YiliConfig.GHOST_MODE);
    }

    /** True when the request must not reach the server; may also rewrite it (online → offline). */
    public static boolean intercept(TLObject request) {
        if (request == null || !enabled()) return false;
        if (YiliConfig.get(YiliConfig.GHOST_READ) && (request instanceof TLRPC.TL_messages_readHistory
                || request instanceof TLRPC.TL_channels_readHistory
                || request instanceof TLRPC.TL_messages_readEncryptedHistory
                || request instanceof TLRPC.TL_messages_readDiscussion
                || request instanceof TLRPC.TL_messages_readMessageContents
                || request instanceof TLRPC.TL_channels_readMessageContents)) {
            return true;
        }
        if (YiliConfig.get(YiliConfig.GHOST_TYPING) && (request instanceof TLRPC.TL_messages_setTyping
                || request instanceof TLRPC.TL_messages_setEncryptedTyping)) {
            return true;
        }
        if (YiliConfig.get(YiliConfig.GHOST_STORIES) && (request instanceof TL_stories.TL_stories_readStories
                || request instanceof TL_stories.TL_stories_incrementStoryViews)) {
            return true;
        }
        if (YiliConfig.get(YiliConfig.GHOST_ONLINE) && request instanceof TL_account.updateStatus) {
            ((TL_account.updateStatus) request).offline = true;
        }
        return false;
    }
}
