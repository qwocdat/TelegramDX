package org.telegram.ui.LiquidGlass;

import android.content.Context;
import android.content.SharedPreferences;
import org.telegram.messenger.ApplicationLoader;

public class DXModConfig {
    private static final String PREF_NAME = "telegram_dx_mod_prefs";

    public static boolean liquidGlassEnabled = true;
    public static boolean liquidButtonsAndInputs = true;
    public static boolean gooeyTabBarEnabled = true;
    public static float chromaticAberration = 0.28f;
    public static float refractionAmountDp = 24f;
    public static float refractionHeightDp = 24f;

    public static boolean ghostUnreadMessages = true;
    public static boolean ghostUnreadStories = true;
    public static boolean ghostHideOnlineStatus = true;
    public static boolean ghostHideTyping = true;

    public static boolean antiRecallEnabled = true;
    public static boolean realtimeActivityTracker = true;

    public static boolean aiAutoReplyEnabled = false;
    public static String aiEndpointUrl = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions";
    public static String aiApiKey = "";
    public static String aiModelName = "gemini-2.5-flash";
    public static String aiSystemPrompt = "Bạn là trợ lý AI thông minh trên TelegramDX, trả lời ngắn gọn, tự nhiên bằng tiếng Việt.";

    public static boolean defaultVietnameseApplied = false;

    private static SharedPreferences prefs() {
        Context ctx = ApplicationLoader.applicationContext;
        if (ctx == null) return null;
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static void load() {
        SharedPreferences p = prefs();
        if (p == null) return;
        liquidGlassEnabled = p.getBoolean("liquidGlassEnabled", true);
        liquidButtonsAndInputs = p.getBoolean("liquidButtonsAndInputs", true);
        gooeyTabBarEnabled = p.getBoolean("gooeyTabBarEnabled", true);
        chromaticAberration = p.getFloat("chromaticAberration", 0.28f);
        refractionAmountDp = p.getFloat("refractionAmountDp", 24f);
        refractionHeightDp = p.getFloat("refractionHeightDp", 24f);

        ghostUnreadMessages = p.getBoolean("ghostUnreadMessages", true);
        ghostUnreadStories = p.getBoolean("ghostUnreadStories", true);
        ghostHideOnlineStatus = p.getBoolean("ghostHideOnlineStatus", true);
        ghostHideTyping = p.getBoolean("ghostHideTyping", true);

        antiRecallEnabled = p.getBoolean("antiRecallEnabled", true);
        realtimeActivityTracker = p.getBoolean("realtimeActivityTracker", true);

        aiAutoReplyEnabled = p.getBoolean("aiAutoReplyEnabled", false);
        aiEndpointUrl = p.getString("aiEndpointUrl", "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions");
        aiApiKey = p.getString("aiApiKey", "");
        aiModelName = p.getString("aiModelName", "gemini-2.5-flash");
        aiSystemPrompt = p.getString("aiSystemPrompt", "Bạn là trợ lý AI thông minh trên TelegramDX, trả lời ngắn gọn, tự nhiên bằng tiếng Việt.");
        defaultVietnameseApplied = p.getBoolean("defaultVietnameseApplied", false);
    }

    public static void save() {
        SharedPreferences p = prefs();
        if (p == null) return;
        p.edit()
            .putBoolean("liquidGlassEnabled", liquidGlassEnabled)
            .putBoolean("liquidButtonsAndInputs", liquidButtonsAndInputs)
            .putBoolean("gooeyTabBarEnabled", gooeyTabBarEnabled)
            .putFloat("chromaticAberration", chromaticAberration)
            .putFloat("refractionAmountDp", refractionAmountDp)
            .putFloat("refractionHeightDp", refractionHeightDp)
            .putBoolean("ghostUnreadMessages", ghostUnreadMessages)
            .putBoolean("ghostUnreadStories", ghostUnreadStories)
            .putBoolean("ghostHideOnlineStatus", ghostHideOnlineStatus)
            .putBoolean("ghostHideTyping", ghostHideTyping)
            .putBoolean("antiRecallEnabled", antiRecallEnabled)
            .putBoolean("realtimeActivityTracker", realtimeActivityTracker)
            .putBoolean("aiAutoReplyEnabled", aiAutoReplyEnabled)
            .putString("aiEndpointUrl", aiEndpointUrl)
            .putString("aiApiKey", aiApiKey)
            .putString("aiModelName", aiModelName)
            .putString("aiSystemPrompt", aiSystemPrompt)
            .putBoolean("defaultVietnameseApplied", defaultVietnameseApplied)
            .apply();
    }
}
