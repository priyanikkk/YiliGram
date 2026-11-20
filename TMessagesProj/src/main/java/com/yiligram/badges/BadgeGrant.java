package com.yiligram.badges;

import static org.telegram.messenger.LocaleController.getString;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;

/** Profile -> ⋮ -> YiliGram badge: none / team / beta tester. */
public final class BadgeGrant {

    private BadgeGrant() {
    }

    /** {@code dialogId}: a user id (> 0) or a chat's dialog id (< 0). */
    public static void show(BaseFragment fragment, long dialogId, Runnable changed) {
        if (fragment.getParentActivity() == null) return;
        YiliBadges.Type current = YiliBadges.get(dialogId);
        final YiliBadges.Type[] types = dialogId > 0
                ? new YiliBadges.Type[]{null, YiliBadges.Type.ADMIN, YiliBadges.Type.BETA}
                : new YiliBadges.Type[]{null, YiliBadges.Type.OFFICIAL};
        CharSequence[] items = new CharSequence[types.length];
        for (int i = 0; i < types.length; i++) {
            items[i] = getString(BadgeInfo.title(types[i]));
        }
        new AlertDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider())
                .setTitle(getString(R.string.YiliBadgeMenu))
                .setItems(items, (d, which) -> {
                    YiliBadges.Type t = types[which];
                    if (t == current) return;
                    YiliBadges.set(dialogId, t);
                    if (changed != null) changed.run();
                    BulletinFactory.of(fragment).createSimpleBulletin(R.raw.contact_check,
                            getString(t == null ? R.string.YiliBadgeRemoved : R.string.YiliBadgeGranted)).show();
                })
                .show();
    }
}
