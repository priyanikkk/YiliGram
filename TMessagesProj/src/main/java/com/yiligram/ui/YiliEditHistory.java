package com.yiligram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.yiligram.features.YiliHistory;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.List;

/** The earlier versions of an edited message, oldest first, then the current text. */
public final class YiliEditHistory {

    private YiliEditHistory() {
    }

    public static void show(BaseFragment fragment, MessageObject message) {
        Context context = fragment.getParentActivity();
        if (context == null) return;
        List<YiliHistory.Entry> versions = YiliHistory.edits(fragment.getCurrentAccount(), message.getDialogId(), message.getId());
        Theme.ResourcesProvider rp = fragment.getResourceProvider();

        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(4), AndroidUtilities.dp(24), AndroidUtilities.dp(8));
        for (YiliHistory.Entry e : versions) {
            addVersion(context, rp, box, LocaleController.formatDateTime(e.date, true), e.text);
        }
        int date = message.messageOwner.edit_date != 0 ? message.messageOwner.edit_date : message.messageOwner.date;
        addVersion(context, rp, box, getString(R.string.YiliEditCurrent) + " · " + LocaleController.formatDateTime(date, true),
                message.messageOwner.message);

        ScrollView scroll = new ScrollView(context);
        scroll.addView(box);
        new AlertDialog.Builder(context, rp)
                .setTitle(getString(R.string.YiliEditHistory))
                .setView(scroll)
                .setPositiveButton(getString(R.string.Close), null)
                .show();
    }

    private static void addVersion(Context context, Theme.ResourcesProvider rp, LinearLayout box, String when, String text) {
        TextView title = new TextView(context);
        title.setText(when);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        title.setTextColor(Theme.getColor(Theme.key_dialogTextGray2, rp));
        box.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 2));

        TextView body = new TextView(context);
        body.setText(text);
        body.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        body.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, rp));
        body.setTextIsSelectable(true);
        box.addView(body, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }
}
