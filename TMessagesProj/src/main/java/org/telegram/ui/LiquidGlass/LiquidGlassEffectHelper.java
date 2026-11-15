package org.telegram.ui.LiquidGlass;

import android.app.Activity;
import android.graphics.Outline;
import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

import java.util.WeakHashMap;

/**
 * Tự động nâng cấp mọi nút bấm (Button, ButtonWithCounterView, ActionBarMenuItem),
 * thanh trượt (SeekBarView, SeekBarCell) và ô nhập văn bản (EditText, EditTextBoldCursor, ChatActivityEnterView)
 * sang hiệu ứng Liquid Glass bằng AGSL RuntimeShader thật + chuyển động đàn hồi vật lý (Spring Physics) như trong video 19s.
 */
public class LiquidGlassEffectHelper {

    private static final WeakHashMap<View, Boolean> decoratedViews = new WeakHashMap<>();

    public static void attachToActivity(final Activity activity) {
        if (activity == null || activity.getWindow() == null) return;
        final View decor = activity.getWindow().getDecorView();
        if (!(decor instanceof ViewGroup)) return;

        final ViewGroup root = (ViewGroup) decor;

        // Gắn thanh TabBar Liquid Glass Gooey Metaball nếu chưa có
        if (root.findViewWithTag("DX_LIQUID_TABBAR_OVERLAY") == null) {
            final LiquidGlassTabBarOverlay overlay = new LiquidGlassTabBarOverlay(activity);
            overlay.setTag("DX_LIQUID_TABBAR_OVERLAY");
            overlay.setCallback(new LiquidGlassTabBarOverlay.Callback() {
                @Override
                public void onTabSelected(int index) {
                    if (index == 3) {
                        DXSettingsDialog.show(activity);
                    } else {
                        DXViewHookInjector.switchMainTab(activity, index);
                    }
                }

                @Override
                public void onQuickCameraClicked() {
                    DXViewHookInjector.triggerQuickCamera(activity);
                }

                @Override
                public void onNewGroupClicked() {
                    DXViewHookInjector.triggerNewGroupOrChat(activity);
                }

                @Override
                public void onOpenDXSettings() {
                    DXSettingsDialog.show(activity);
                }
            });
            ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            );
            root.addView(overlay, lp);
            DXViewHookInjector.bindScrollDetector(root, overlay);
        }

        root.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (!DXModConfig.liquidGlassEnabled || !DXModConfig.liquidButtonsAndInputs) return;
                scanAndApplyLiquidGlass(root);
            }
        });
    }

    public static void scanAndApplyLiquidGlass(View view) {
        if (view == null || view instanceof LiquidGlassTabBarOverlay) return;

        String cls = view.getClass().getSimpleName();
        boolean isButtonLike = (view instanceof Button)
                || (view instanceof ImageButton)
                || cls.contains("ButtonWithCounterView")
                || cls.contains("FloatingActionButton")
                || cls.contains("RoundButton");
        boolean isInputLike = (view instanceof EditText)
                || cls.contains("ChatActivityEnterView")
                || cls.contains("EditTextCaption")
                || cls.contains("EditTextEmoji");
        boolean isSliderLike = cls.contains("SeekBarView") || cls.contains("SeekBarCell");

        // Ẩn 2 nút nổi mặc định (Camera và Tạo tin nhắn/nhóm) trên DialogsActivity vì đã chuyển xuống 2 bên cạnh TabBar
        if (DXModConfig.gooeyTabBarEnabled && (cls.contains("RLottieImageView") || cls.contains("ImageView"))) {
            CharSequence desc = view.getContentDescription();
            if (desc != null) {
                String d = desc.toString().toLowerCase();
                if (d.contains("new message") || d.contains("story") || d.contains("camera") || d.contains("tin nhắn mới")) {
                    if (view.getWidth() >= AndroidUtilities.dp(44f) && view.getWidth() <= AndroidUtilities.dp(72f)
                            && view.getTop() > view.getRootView().getHeight() * 0.55f) {
                        view.setVisibility(View.GONE);
                    }
                }
            }
        }

        if ((isButtonLike || isInputLike || isSliderLike) && !decoratedViews.containsKey(view)) {
            decoratedViews.put(view, Boolean.TRUE);
            applyLiquidShaderToWidget(view, isInputLike);
        }

        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0, n = vg.getChildCount(); i < n; i++) {
                scanAndApplyLiquidGlass(vg.getChildAt(i));
            }
        }
    }

    private static void applyLiquidShaderToWidget(final View target, final boolean isInput) {
        final float radiusPx = AndroidUtilities.dp(isInput ? 22f : 26f);

        GradientDrawable glassDrawable = new GradientDrawable();
        glassDrawable.setCornerRadius(radiusPx);
        boolean isDark = Theme.isCurrentThemeDark();
        glassDrawable.setColor(isDark ? 0x441E2736 : 0x44F2F6FF);
        glassDrawable.setStroke(AndroidUtilities.dp(1.1f), isDark ? 0x66FFFFFF : 0x88FFFFFF);
        if (target.getBackground() == null || isInput) {
            target.setBackground(glassDrawable);
        }

        target.setClipToOutline(true);
        target.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                int w = Math.max(1, v.getWidth());
                int h = Math.max(1, v.getHeight());
                float r = Math.min(radiusPx, Math.min(w, h) * 0.5f);
                outline.setRoundRect(0, 0, w, h, r);
            }
        });

        final FloatValueHolder pressHolder = new FloatValueHolder(0f);
        final SpringAnimation pressAnim = new SpringAnimation(pressHolder);
        SpringForce force = new SpringForce(0f);
        force.setDampingRatio(0.55f);
        force.setStiffness(380f);
        pressAnim.setSpring(force);

        final RuntimeShader widgetShader;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RuntimeShader tmp = null;
            try {
                tmp = LiquidGlassShaders.createWidgetLensShader();
            } catch (Throwable ignored) {
            }
            widgetShader = tmp;
        } else {
            widgetShader = null;
        }

        pressAnim.addUpdateListener((animation, value, velocity) -> {
            float p = value / 1000f;
            float scale = 1f + (isInput ? 0.025f : 0.075f) * p;
            target.setScaleX(scale);
            target.setScaleY(scale);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && widgetShader != null && target.getWidth() > 0 && target.getHeight() > 0) {
                try {
                    RenderEffect effect = LiquidGlassShaders.buildWidgetRenderEffect(
                        widgetShader,
                        target.getWidth(),
                        target.getHeight(),
                        radiusPx,
                        AndroidUtilities.dp(18f),
                        AndroidUtilities.dp(16f) * (0.65f + 0.35f * p),
                        DXModConfig.chromaticAberration,
                        0.45f,
                        -0.85f,
                        p,
                        Theme.isCurrentThemeDark()
                    );
                    target.setRenderEffect(effect);
                } catch (Throwable ignored) {
                }
            }
        });

        target.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && widgetShader != null && (right - left) > 0 && (bottom - top) > 0) {
                try {
                    RenderEffect effect = LiquidGlassShaders.buildWidgetRenderEffect(
                        widgetShader,
                        right - left,
                        bottom - top,
                        radiusPx,
                        AndroidUtilities.dp(18f),
                        AndroidUtilities.dp(14f),
                        DXModConfig.chromaticAberration,
                        0.45f,
                        -0.85f,
                        0f,
                        Theme.isCurrentThemeDark()
                    );
                    v.setRenderEffect(effect);
                } catch (Throwable ignored) {
                }
            }
        });

        target.setOnTouchListener((v, ev) -> {
            int action = ev.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                pressAnim.getSpring().setFinalPosition(1000f);
                pressAnim.start();
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                pressAnim.getSpring().setFinalPosition(0f);
                pressAnim.start();
            }
            return false; // Giữ nguyên sự kiện click/nhập chữ gốc của View
        });
    }
}
