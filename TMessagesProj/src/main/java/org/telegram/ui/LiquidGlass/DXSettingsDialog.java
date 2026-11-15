package org.telegram.ui.LiquidGlass;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;

/**
 * Bảng điều khiển Cài đặt TelegramDX (100% Tiếng Việt):
 * Cho phép bật/tắt Shader Liquid Glass, điều chỉnh độ khúc xạ / tán sắc cầu vồng,
 * bật Ghost Mode (ẩn đã xem tin nhắn, ẩn mắt xem Story, ẩn Online/Typing),
 * bật Xem tin nhắn thu hồi (Anti-Recall), Theo dõi hoạt động thời gian thực và cấu hình AI Auto-Reply.
 */
public class DXSettingsDialog {

    public static void show(final Activity activity) {
        if (activity == null) return;
        DXModConfig.load();

        ScrollView scroll = new ScrollView(activity);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = AndroidUtilities.dp(18f);
        root.setPadding(pad, pad, pad, pad);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xEE141B26);
        bg.setCornerRadius(AndroidUtilities.dp(24f));
        bg.setStroke(AndroidUtilities.dp(1.2f), 0x55FFFFFF);
        root.setBackground(bg);

        TextView title = new TextView(activity);
        title.setText("✨ Cài đặt TelegramDX Liquid Glass");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        title.setPadding(0, 0, 0, AndroidUtilities.dp(12f));
        root.addView(title);

        final CheckBox cbLiquid = addToggle(activity, root, "Bật Shader Liquid Glass thật (AGSL Refraction)", DXModConfig.liquidGlassEnabled);
        final CheckBox cbWidgets = addToggle(activity, root, "Biến mọi Nút bấm, Slider & Ô nhập thành Liquid Glass", DXModConfig.liquidButtonsAndInputs);
        final CheckBox cbGooeyTab = addToggle(activity, root, "Thanh TabBar giọt nước (Camera trái + Tạo nhóm phải + Gộp 3 gạch)", DXModConfig.gooeyTabBarEnabled);

        TextView lblChroma = addLabel(activity, root, "Độ tán sắc cầu vồng (Chromatic Aberration): " + String.format("%.2f", DXModConfig.chromaticAberration));
        final SeekBar sbChroma = new SeekBar(activity);
        sbChroma.setMax(60);
        sbChroma.setProgress((int) (DXModConfig.chromaticAberration * 100f));
        sbChroma.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                lblChroma.setText("Độ tán sắc cầu vồng (Chromatic Aberration): " + String.format("%.2f", progress / 100f));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        root.addView(sbChroma);

        addSectionHeader(activity, root, "👻 Quyền riêng tư & Chống thu hồi");
        final CheckBox cbAntiRecall = addToggle(activity, root, "Xem tin nhắn thu hồi của người khác (Anti-Recall)", DXModConfig.antiRecallEnabled);
        final CheckBox cbRealtime = addToggle(activity, root, "Xem trạng thái hoạt động gần nhất theo thời gian thực (giây)", DXModConfig.realtimeActivityTracker);
        final CheckBox cbGhostMsg = addToggle(activity, root, "Đọc tin nhắn không hiện Đã xem (Ghost Read)", DXModConfig.ghostUnreadMessages);
        final CheckBox cbGhostStory = addToggle(activity, root, "Xem Story người khác không hiện mắt", DXModConfig.ghostUnreadStories);
        final CheckBox cbGhostOnline = addToggle(activity, root, "Ẩn trạng thái hoạt động (Online) với người khác", DXModConfig.ghostHideOnlineStatus);
        final CheckBox cbGhostTyping = addToggle(activity, root, "Ẩn trạng thái đang soạn tin nhắn (Typing...)", DXModConfig.ghostHideTyping);

        addSectionHeader(activity, root, "🤖 Tự động trả lời bằng AI (Auto-Reply)");
        final CheckBox cbAiEnable = addToggle(activity, root, "Bật tự động trả lời tin nhắn bằng AI", DXModConfig.aiAutoReplyEnabled);
        final EditText etEndpoint = addInput(activity, root, "API Endpoint (OpenAI/Gemini compatible)", DXModConfig.aiEndpointUrl);
        final EditText etApiKey = addInput(activity, root, "API Key", DXModConfig.aiApiKey);
        final EditText etModel = addInput(activity, root, "Tên Model (vd: gemini-2.5-flash, gpt-4o-mini)", DXModConfig.aiModelName);
        final EditText etPrompt = addInput(activity, root, "System Prompt (hướng dẫn cách trả lời)", DXModConfig.aiSystemPrompt);

        scroll.addView(root);

        AlertDialog dialog = new AlertDialog.Builder(activity)
            .setView(scroll)
            .setPositiveButton("Lưu cài đặt", (d, which) -> {
                DXModConfig.liquidGlassEnabled = cbLiquid.isChecked();
                DXModConfig.liquidButtonsAndInputs = cbWidgets.isChecked();
                DXModConfig.gooeyTabBarEnabled = cbGooeyTab.isChecked();
                DXModConfig.chromaticAberration = sbChroma.getProgress() / 100f;

                DXModConfig.antiRecallEnabled = cbAntiRecall.isChecked();
                DXModConfig.realtimeActivityTracker = cbRealtime.isChecked();
                DXModConfig.ghostUnreadMessages = cbGhostMsg.isChecked();
                DXModConfig.ghostUnreadStories = cbGhostStory.isChecked();
                DXModConfig.ghostHideOnlineStatus = cbGhostOnline.isChecked();
                DXModConfig.ghostHideTyping = cbGhostTyping.isChecked();

                DXModConfig.aiAutoReplyEnabled = cbAiEnable.isChecked();
                DXModConfig.aiEndpointUrl = etEndpoint.getText().toString().trim();
                DXModConfig.aiApiKey = etApiKey.getText().toString().trim();
                DXModConfig.aiModelName = etModel.getText().toString().trim();
                DXModConfig.aiSystemPrompt = etPrompt.getText().toString().trim();

                DXModConfig.save();
            })
            .setNegativeButton("Đóng", null)
            .create();
        dialog.show();
    }

    private static void addSectionHeader(Context ctx, LinearLayout parent, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(0xFF66B8FF);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        tv.setPadding(0, AndroidUtilities.dp(14f), 0, AndroidUtilities.dp(6f));
        parent.addView(tv);
    }

    private static TextView addLabel(Context ctx, LinearLayout parent, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(0xFFE0ECFF);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        tv.setPadding(0, AndroidUtilities.dp(6f), 0, AndroidUtilities.dp(4f));
        parent.addView(tv);
        return tv;
    }

    private static CheckBox addToggle(Context ctx, LinearLayout parent, String label, boolean checked) {
        CheckBox cb = new CheckBox(ctx);
        cb.setText(label);
        cb.setTextColor(Color.WHITE);
        cb.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        cb.setChecked(checked);
        cb.setPadding(AndroidUtilities.dp(4f), AndroidUtilities.dp(6f), AndroidUtilities.dp(4f), AndroidUtilities.dp(6f));
        parent.addView(cb);
        return cb;
    }

    private static EditText addInput(Context ctx, LinearLayout parent, String hint, String value) {
        addLabel(ctx, parent, hint);
        EditText et = new EditText(ctx);
        et.setText(value);
        et.setHint(hint);
        et.setTextColor(Color.WHITE);
        et.setHintTextColor(0x88B0C4DE);
        et.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
        int p = AndroidUtilities.dp(10f);
        et.setPadding(p, p, p, p);
        parent.addView(et);
        return et;
    }
}
