package org.telegram.ui.LiquidGlass;

import android.app.Activity;
import android.content.SharedPreferences;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ContactAddActivity;
import org.telegram.ui.ContactsActivity;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.GroupCreateActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.SettingsActivity;

/**
 * Kết nối cử chỉ vuốt màn hình với hiệu ứng gộp/tách giọt nước của thanh TabBar,
 * điều hướng nút Chụp ảnh nhanh (trái), Tạo nhóm mới (phải), và tự động đặt Tiếng Việt làm ngôn ngữ mặc định.
 */
public class DXViewHookInjector {

    public static void applyDefaultVietnameseLocaleIfNeeded() {
        DXModConfig.load();
        if (DXModConfig.defaultVietnameseApplied) return;
        try {
            LocaleController controller = LocaleController.getInstance();
            LocaleController.LocaleInfo viInfo = controller.getLanguageFromDict("vi");
            if (viInfo == null) {
                viInfo = new LocaleController.LocaleInfo();
                viInfo.name = "Tiếng Việt";
                viInfo.nameEnglish = "Vietnamese";
                viInfo.shortName = "vi";
                viInfo.pluralLangCode = "vi";
                viInfo.pathToFile = "remote";
                controller.languages.add(viInfo);
                controller.languagesDict.put("vi", viInfo);
            }
            SharedPreferences prefs = MessagesController.getGlobalMainSettings();
            prefs.edit().putString("language", viInfo.getKey()).apply();
            controller.applyLanguage(viInfo, true, false, UserConfig.selectedAccount);
            DXModConfig.defaultVietnameseApplied = true;
            DXModConfig.save();
        } catch (Throwable ignored) {
        }
    }

    public static void bindScrollDetector(final ViewGroup decorView, final LiquidGlassTabBarOverlay overlay) {
        final int touchSlop = ViewConfiguration.get(decorView.getContext()).getScaledTouchSlop();
        decorView.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
            @Override
            public void onChildViewAdded(View parent, View child) {}
            @Override
            public void onChildViewRemoved(View parent, View child) {}
        });

        // Bắt cử chỉ vuốt dọc trên toàn màn hình mà không chặn sự kiện cuộn của RecyclerView
        decorView.getViewTreeObserver().addOnPreDrawListener(() -> {
            attachDispatchTouchInterceptor(decorView, overlay, touchSlop);
            return true;
        });
    }

    private static boolean touchHookInstalled = false;
    private static float lastTouchY = 0f;
    private static boolean trackingScroll = false;

    private static void attachDispatchTouchInterceptor(ViewGroup decorView, final LiquidGlassTabBarOverlay overlay, final int slop) {
        if (touchHookInstalled) return;
        touchHookInstalled = true;
        try {
            android.view.Window.Callback origCallback = ((Activity) decorView.getContext()).getWindow().getCallback();
            if (origCallback == null) return;
            ((Activity) decorView.getContext()).getWindow().setCallback(new WindowCallbackWrapper(origCallback) {
                @Override
                public boolean dispatchTouchEvent(MotionEvent ev) {
                    int action = ev.getActionMasked();
                    float y = ev.getRawY();
                    if (action == MotionEvent.ACTION_DOWN) {
                        lastTouchY = y;
                        trackingScroll = false;
                    } else if (action == MotionEvent.ACTION_MOVE) {
                        float dy = lastTouchY - y; // dy > 0: ngón tay vuốt lên (cuộn trang xuống sâu hơn)
                        if (Math.abs(dy) > 1.5f) {
                            trackingScroll = true;
                            overlay.onContentScrollDelta(dy);
                            lastTouchY = y;
                        }
                    } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                        if (trackingScroll) {
                            overlay.settleScrollStateIfNeeded();
                        }
                        trackingScroll = false;
                    }
                    return super.dispatchTouchEvent(ev);
                }
            });
        } catch (Throwable ignored) {
        }
    }

    public static void triggerQuickCamera(Activity activity) {
        if (!(activity instanceof LaunchActivity)) return;
        try {
            LaunchActivity launch = (LaunchActivity) activity;
            BaseFragment top = launch.getActionBarLayout().getLastFragment();
            if (top instanceof DialogsActivity) {
                // Mở trình quay/chụp Story nhanh của Telegram qua StoryRecorder
                Class<?> srCls = Class.forName("org.telegram.ui.Stories.recorder.StoryRecorder");
                Object sr = srCls.getMethod("getInstance", Activity.class, int.class).invoke(null, activity, UserConfig.selectedAccount);
                srCls.getMethod("open", android.view.View.class).invoke(sr, (Object) null);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void triggerNewGroupOrChat(Activity activity) {
        if (!(activity instanceof LaunchActivity)) return;
        try {
            LaunchActivity launch = (LaunchActivity) activity;
            launch.presentFragment(new GroupCreateActivity(new android.os.Bundle()));
        } catch (Throwable ignored) {
        }
    }

    public static void switchMainTab(Activity activity, int tabIndex) {
        if (!(activity instanceof LaunchActivity)) return;
        try {
            LaunchActivity launch = (LaunchActivity) activity;
            if (tabIndex == 0) {
                launch.presentFragment(new ContactsActivity(null));
            } else if (tabIndex == 2) {
                launch.getActionBarLayout().closeLastFragment(true);
            } else if (tabIndex == 3) {
                launch.presentFragment(new SettingsActivity());
            }
        } catch (Throwable ignored) {
        }
    }
}
