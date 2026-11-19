package com.yiligram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.view.View;

import com.yiligram.push.PushBridge;

import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;
import java.util.List;

/** Settings → Push notifications: pick a UnifiedPush distributor or stay on the background connection. */
public class PushSettingsActivity extends UniversalFragment {

    private static final int ID_OFF = 1;
    private static final int ID_LEARN = 2;
    private static final int ID_DISTRIBUTOR_BASE = 100;

    private List<String> distributors = new ArrayList<>();

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.YiliPush);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listView != null) {
            listView.adapter.update(true); // a distributor may have been installed meanwhile
        }
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        PushBridge.Impl push = PushBridge.get();
        distributors = PushBridge.distributors();
        String current = push != null ? push.current() : null;
        items.add(UItem.asHeader(getString(R.string.YiliPushDistributors)));
        items.add(UItem.asRadio(ID_OFF, getString(R.string.YiliPushOff)).setChecked(current == null));
        for (int i = 0; i < distributors.size(); i++) {
            String pkg = distributors.get(i);
            CharSequence status = pkg.equals(current) && push != null
                    ? getString(push.registered() ? R.string.YiliPushStatusRegistered : R.string.YiliPushStatusWaiting) : null;
            items.add(UItem.asRadio(ID_DISTRIBUTOR_BASE + i, push != null ? push.label(pkg) : pkg, status).setChecked(pkg.equals(current)));
        }
        items.add(UItem.asShadow(getString(distributors.isEmpty() ? R.string.YiliPushNone : R.string.YiliPushAbout)));
        items.add(UItem.asButton(ID_LEARN, R.drawable.msg_info, getString(R.string.YiliPushLearnMore)));
        items.add(UItem.asShadow(null));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        PushBridge.Impl push = PushBridge.get();
        if (item.id == ID_LEARN) {
            Browser.openUrl(getParentActivity(), "https://unifiedpush.org/users/distributors/");
            return;
        }
        if (push == null || getParentActivity() == null) {
            return;
        }
        String target = null;
        if (item.id >= ID_DISTRIBUTOR_BASE) {
            int i = item.id - ID_DISTRIBUTOR_BASE;
            if (i >= distributors.size()) {
                return;
            }
            target = distributors.get(i);
        } else if (item.id != ID_OFF) {
            return;
        }
        push.use(target, getParentActivity(), () -> {
            if (listView != null) {
                listView.adapter.update(true);
            }
        });
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
