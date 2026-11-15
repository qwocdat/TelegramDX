package org.telegram.messenger;

import org.telegram.messenger.regular.BuildConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.DXModConfig;
import org.telegram.ui.DXPrivacyAndAIController;

public class ApplicationLoaderImpl extends ApplicationLoader {
    @Override
    protected String onGetApplicationId() {
        return BuildConfig.APPLICATION_ID;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        AndroidUtilities.runOnUIThread(() -> {
            DXModConfig.ensureDefaultVietnamese();
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                DXPrivacyAndAIController.getInstance().init(a);
            }
        }, 600);
    }

    @Override
    public void processUpdate(int currentAccount, TLRPC.Update update) {
        super.processUpdate(currentAccount, update);
        if (update == null) return;
        String clsName = update.getClass().getSimpleName();
        if (clsName.contains("updateUserStatus")) {
            try {
                long uid = update.getClass().getField("user_id").getLong(update);
                Object st = update.getClass().getField("status").get(update);
                String desc = (st != null && st.getClass().getSimpleName().contains("Online")) ? "Trực tuyến" : "Rời mạng";
                DXPrivacyAndAIController.getInstance().recordRealtimeUserActivity(uid, desc);
            } catch (Throwable ignored) {
            }
        } else if (clsName.contains("updateUserTyping")) {
            try {
                long uid = update.getClass().getField("user_id").getLong(update);
                DXPrivacyAndAIController.getInstance().recordRealtimeUserActivity(uid, "Đang soạn tin");
            } catch (Throwable ignored) {
            }
        }
    }
}
