package com.yiligram.push;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Push transport controls for the settings screen. The UnifiedPush implementation lives in the
 * app module (it owns the connector dependency) and installs itself with {@link #set}.
 */
public final class PushBridge {

    public interface Impl {
        /** Package names of installed UnifiedPush distributors. */
        @NonNull
        List<String> distributors();

        /** Distributor in use, or null. */
        @Nullable
        String current();

        /** Whether Telegram accepted a push endpoint for this device. */
        boolean registered();

        /** Switches to a distributor (null = off: background connection) and re-registers. */
        void use(@Nullable String distributor, @NonNull Activity activity, @NonNull Runnable done);

        /** Human-readable name of a distributor package. */
        @NonNull
        String label(@NonNull String packageName);
    }

    private static volatile Impl impl;

    private PushBridge() {
    }

    public static void set(@Nullable Impl i) {
        impl = i;
    }

    @Nullable
    public static Impl get() {
        return impl;
    }

    @NonNull
    public static List<String> distributors() {
        Impl i = impl;
        return i == null ? Collections.emptyList() : i.distributors();
    }
}
