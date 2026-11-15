package org.telegram.messenger;

import android.app.Activity;
import android.os.Bundle;
import org.telegram.messenger.regular.BuildConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.LiquidGlass.DXModConfig;
import org.telegram.ui.LiquidGlass.DXPrivacyAndAIController;
import org.telegram.ui.LiquidGlass.DXViewHookInjector;
import org.telegram.ui.LiquidGlass.LiquidGlassEffectHelper;

public class ApplicationLoaderImpl extends ApplicationLoader {
    @Override
    protected String onGetApplicationId() {
        return BuildConfig.APPLICATION_ID;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        DXModConfig.load();

        // Đặt ngôn ngữ mặc định là Tiếng Việt ngay lần chạy đầu tiên
        AndroidUtilities.runOnUIThread(() -> {
            DXViewHookInjector.applyDefaultVietnameseLocaleIfNeeded();
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                DXPrivacyAndAIController.getInstance().init(a);
            }
        }, 600);

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                DXViewHookInjector.applyDefaultVietnameseLocaleIfNeeded();
            }

            @Override
            public void onActivityStarted(Activity activity) {}

            @Override
            public void onActivityResumed(Activity activity) {
                LiquidGlassEffectHelper.attachToActivity(activity);
            }

            @Override
            public void onActivityPaused(Activity activity) {}

            @Override
            public void onActivityStopped(Activity activity) {}

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

            @Override
            public void onActivityDestroyed(Activity activity) {}
        });
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
