package com.yiligram.badges;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/** The sheet shown when a YiliGram badge is tapped: big badge, its name and what it means. */
public final class BadgeInfo {

    private BadgeInfo() {
    }

    /** Name of a badge type; null means "no badge". */
    public static int title(YiliBadges.Type type) {
        if (type == null) return R.string.YiliBadgeNone;
        switch (type) {
            case ADMIN: return R.string.YiliBadgeAdmin;
            case OFFICIAL: return R.string.YiliBadgeOfficial;
            default: return R.string.YiliBadgeBeta;
        }
    }

    public static void show(BaseFragment fragment, YiliBadges.Type type) {
        Context ctx = fragment.getParentActivity();
        if (ctx == null || type == null) return;
        BottomSheet sheet = new BottomSheet(ctx, false, fragment.getResourceProvider());
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);

        ImageView badge = new ImageView(ctx);
        badge.setImageDrawable(new YiliBadgeDrawable(type, 72));
        box.addView(badge, LayoutHelper.createLinear(72, 72, Gravity.CENTER_HORIZONTAL, 0, 24, 0, 16));

        TextView title = new TextView(ctx);
        title.setText(getString(title(type)));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        title.setTypeface(AndroidUtilities.bold());
        title.setGravity(Gravity.CENTER);
        title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, fragment.getResourceProvider()));
        box.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 0, 24, 8));

        TextView text = new TextView(ctx);
        text.setText(getString(type == YiliBadges.Type.ADMIN ? R.string.YiliBadgeAdminInfo
                : type == YiliBadges.Type.OFFICIAL ? R.string.YiliBadgeOfficialInfo : R.string.YiliBadgeBetaInfo));
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        text.setGravity(Gravity.CENTER);
        text.setLineSpacing(AndroidUtilities.dp(2), 1f);
        text.setTextColor(Theme.getColor(Theme.key_dialogTextGray2, fragment.getResourceProvider()));
        box.addView(text, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 32, 0, 32, 28));

        sheet.setCustomView(box);
        fragment.showDialog(sheet);
    }
}
