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

    public static void show(BaseFragment fragment, long userId, Runnable changed) {
        if (fragment.getParentActivity() == null) return;
        YiliBadges.Type current = YiliBadges.get(userId);
        CharSequence[] items = {
                getString(R.string.YiliBadgeNone),
                getString(R.string.YiliBadgeAdmin),
                getString(R.string.YiliBadgeBeta),
        };
        new AlertDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider())
                .setTitle(getString(R.string.YiliBadgeMenu))
                .setItems(items, (d, which) -> {
                    YiliBadges.Type t = which == 1 ? YiliBadges.Type.ADMIN : which == 2 ? YiliBadges.Type.BETA : null;
                    if (t == current) return;
                    YiliBadges.set(userId, t);
                    if (changed != null) changed.run();
                    BulletinFactory.of(fragment).createSimpleBulletin(R.raw.contact_check,
                            getString(t == null ? R.string.YiliBadgeRemoved : R.string.YiliBadgeGranted)).show();
                })
                .show();
    }
}
