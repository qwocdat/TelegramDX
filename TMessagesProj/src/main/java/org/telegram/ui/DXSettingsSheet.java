package org.telegram.ui;

import android.content.Context;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

public class DXSettingsSheet {

    public static void show(Context context, Theme.ResourcesProvider resourcesProvider) {
        if (context == null) return;

        ScrollView scrollView = new ScrollView(context);
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
        scrollView.addView(container);

        addSectionHeader(container, context, "Giao diện Liquid Glass (AGSL Shader)", resourcesProvider);
        addToggle(container, context, "Hiệu ứng Kính lỏng & Tán sắc 7 dải",
                DXModConfig.isLiquidGlassEnabled(), DXModConfig::setLiquidGlassEnabled, resourcesProvider);
        addToggle(container, context, "TabBar Biến hình Giọt nước 3 giai đoạn",
                DXModConfig.isMorphTabBarEnabled(), DXModConfig::setMorphTabBarEnabled, resourcesProvider);

        addSectionHeader(container, context, "Chế độ Tàng hình (Ghost Mode)", resourcesProvider);
        addToggle(container, context, "Đọc tin nhắn không hiện Đã xem",
                DXModConfig.isGhostNoRead(), DXModConfig::setGhostNoRead, resourcesProvider);
        addToggle(container, context, "Xem Story ẩn danh (không tăng lượt xem)",
                DXModConfig.isGhostNoStory(), DXModConfig::setGhostNoStory, resourcesProvider);
        addToggle(container, context, "Ẩn trạng thái Online",
                DXModConfig.isGhostHideOnline(), DXModConfig::setGhostHideOnline, resourcesProvider);
        addToggle(container, context, "Ẩn trạng thái Đang nhập...",
                DXModConfig.isGhostHideTyping(), DXModConfig::setGhostHideTyping, resourcesProvider);

        addSectionHeader(container, context, "Tin nhắn & Hoạt động", resourcesProvider);
        addToggle(container, context, "Chống thu hồi tin nhắn (Anti-Recall)",
                DXModConfig.isAntiRecall(), DXModConfig::setAntiRecall, resourcesProvider);
        addToggle(container, context, "Theo dõi hoạt động Real-Time (cục bộ)",
                DXModConfig.isRealtimeTracker(), DXModConfig::setRealtimeTracker, resourcesProvider);

        TextView noteView = new TextView(context);
        noteView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        noteView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
        noteView.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(4), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        noteView.setText("Lưu ý: Theo dõi Real-Time chỉ ghi nhận trạng thái online/đang nhập trực tiếp khi mở app; không thể vượt qua cài đặt ẩn Last Seen từ máy chủ Telegram.");
        container.addView(noteView);

        addSectionHeader(container, context, "AI Auto-Reply (OpenAI-Compatible)", resourcesProvider);
        addToggle(container, context, "Bật AI Auto-Reply",
                DXModConfig.isAiEnabled(), DXModConfig::setAiEnabled, resourcesProvider);

        EditTextBoldCursor endpointInput = createInput(context, "API Endpoint URL", DXModConfig.getAiEndpoint(), false, resourcesProvider);
        EditTextBoldCursor keyInput = createInput(context, "API Key (sk-...)", DXModConfig.getAiKey(), true, resourcesProvider);
        EditTextBoldCursor modelInput = createInput(context, "Model (vd: gpt-4o-mini)", DXModConfig.getAiModel(), false, resourcesProvider);

        container.addView(endpointInput, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 6, 16, 6));
        container.addView(keyInput, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 6, 16, 6));
        container.addView(modelInput, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 6, 16, 10));

        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle("Cài đặt TelegramDX");
        builder.setView(scrollView);
        builder.setPositiveButton("Lưu", (dialog, which) -> {
            DXModConfig.setAiEndpoint(endpointInput.getText().toString());
            DXModConfig.setAiKey(keyInput.getText().toString());
            DXModConfig.setAiModel(modelInput.getText().toString());
        });
        builder.setNegativeButton("Đóng", null);
        builder.show();
    }

    private static void addSectionHeader(LinearLayout parent, Context context, String title, Theme.ResourcesProvider rp) {
        TextView tv = new TextView(context);
        tv.setText(title);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        tv.setTypeface(AndroidUtilities.bold());
        tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader, rp));
        tv.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(4));
        parent.addView(tv);
    }

    private interface BoolSetter {
        void set(boolean value);
    }

    private static void addToggle(LinearLayout parent, Context context, String title, boolean initial, BoolSetter setter, Theme.ResourcesProvider rp) {
        TextCheckCell cell = new TextCheckCell(context, rp);
        cell.setTextAndCheck(title, initial, true);
        cell.setOnClickListener(v -> {
            boolean next = !cell.isChecked();
            cell.setChecked(next);
            setter.set(next);
        });
        parent.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    private static EditTextBoldCursor createInput(Context context, String hint, String value, boolean password, Theme.ResourcesProvider rp) {
        EditTextBoldCursor et = new EditTextBoldCursor(context);
        et.setHint(hint);
        et.setText(value);
        et.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        et.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, rp));
        et.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText, rp));
        et.setLineColors(
                Theme.getColor(Theme.key_windowBackgroundWhiteInputField, rp),
                Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated, rp),
                Theme.getColor(Theme.key_text_RedBold, rp)
        );
        et.setSingleLine(true);
        et.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        if (password) {
            et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        return et;
    }
}
