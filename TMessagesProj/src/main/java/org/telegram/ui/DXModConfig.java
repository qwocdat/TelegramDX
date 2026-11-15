package org.telegram.ui;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;

public class DXModConfig {
    private static final String PREFS = "telegramdx_mod_prefs";

    public static final String KEY_LIQUID_GLASS = "dx_liquid_glass_enabled";
    public static final String KEY_MORPH_TABBAR = "dx_morph_tabbar_enabled";
    public static final String KEY_GHOST_NO_READ = "dx_ghost_no_read";
    public static final String KEY_GHOST_NO_STORY = "dx_ghost_no_story";
    public static final String KEY_GHOST_HIDE_ONLINE = "dx_ghost_hide_online";
    public static final String KEY_GHOST_HIDE_TYPING = "dx_ghost_hide_typing";
    public static final String KEY_ANTI_RECALL = "dx_anti_recall";
    public static final String KEY_REALTIME_TRACKER = "dx_realtime_tracker";
    public static final String KEY_AI_ENABLED = "dx_ai_enabled";
    public static final String KEY_AI_ENDPOINT = "dx_ai_endpoint";
    public static final String KEY_AI_KEY = "dx_ai_key";
    public static final String KEY_AI_MODEL = "dx_ai_model";
    public static final String KEY_VI_DEFAULT_APPLIED = "dx_vi_default_applied_v1";

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isLiquidGlassEnabled() {
        return prefs().getBoolean(KEY_LIQUID_GLASS, true);
    }

    public static void setLiquidGlassEnabled(boolean v) {
        prefs().edit().putBoolean(KEY_LIQUID_GLASS, v).apply();
    }

    public static boolean isMorphTabBarEnabled() {
        return prefs().getBoolean(KEY_MORPH_TABBAR, true);
    }

    public static void setMorphTabBarEnabled(boolean v) {
        prefs().edit().putBoolean(KEY_MORPH_TABBAR, v).apply();
    }

    public static boolean isGhostNoRead() {
        return prefs().getBoolean(KEY_GHOST_NO_READ, false);
    }

    public static void setGhostNoRead(boolean v) {
        prefs().edit().putBoolean(KEY_GHOST_NO_READ, v).apply();
    }

    public static boolean isGhostNoStory() {
        return prefs().getBoolean(KEY_GHOST_NO_STORY, false);
    }

    public static void setGhostNoStory(boolean v) {
        prefs().edit().putBoolean(KEY_GHOST_NO_STORY, v).apply();
    }

    public static boolean isGhostHideOnline() {
        return prefs().getBoolean(KEY_GHOST_HIDE_ONLINE, false);
    }

    public static void setGhostHideOnline(boolean v) {
        prefs().edit().putBoolean(KEY_GHOST_HIDE_ONLINE, v).apply();
    }

    public static boolean isGhostHideTyping() {
        return prefs().getBoolean(KEY_GHOST_HIDE_TYPING, false);
    }

    public static void setGhostHideTyping(boolean v) {
        prefs().edit().putBoolean(KEY_GHOST_HIDE_TYPING, v).apply();
    }

    public static boolean isAntiRecall() {
        return prefs().getBoolean(KEY_ANTI_RECALL, true);
    }

    public static void setAntiRecall(boolean v) {
        prefs().edit().putBoolean(KEY_ANTI_RECALL, v).apply();
    }

    public static boolean isRealtimeTracker() {
        return prefs().getBoolean(KEY_REALTIME_TRACKER, false);
    }

    public static void setRealtimeTracker(boolean v) {
        prefs().edit().putBoolean(KEY_REALTIME_TRACKER, v).apply();
    }

    public static boolean isAiEnabled() {
        return prefs().getBoolean(KEY_AI_ENABLED, false);
    }

    public static void setAiEnabled(boolean v) {
        prefs().edit().putBoolean(KEY_AI_ENABLED, v).apply();
    }

    public static String getAiEndpoint() {
        return prefs().getString(KEY_AI_ENDPOINT, "https://api.openai.com/v1/chat/completions");
    }

    public static void setAiEndpoint(String v) {
        prefs().edit().putString(KEY_AI_ENDPOINT, v != null ? v.trim() : "").apply();
    }

    public static String getAiKey() {
        return prefs().getString(KEY_AI_KEY, "");
    }

    public static void setAiKey(String v) {
        prefs().edit().putString(KEY_AI_KEY, v != null ? v.trim() : "").apply();
    }

    public static String getAiModel() {
        return prefs().getString(KEY_AI_MODEL, "gpt-4o-mini");
    }

    public static void setAiModel(String v) {
        prefs().edit().putString(KEY_AI_MODEL, v != null ? v.trim() : "").apply();
    }

    public static void ensureDefaultVietnamese() {
        try {
            SharedPreferences p = prefs();
            if (!p.getBoolean(KEY_VI_DEFAULT_APPLIED, false)) {
                p.edit().putBoolean(KEY_VI_DEFAULT_APPLIED, true).apply();
                LocaleController lc = LocaleController.getInstance();
                if (lc != null && lc.getCurrentLocaleInfo() != null && !"vi".equalsIgnoreCase(lc.getCurrentLocaleInfo().shortName)) {
                    LocaleController.LocaleInfo vi = lc.getLanguageFromDict("vi");
                    if (vi != null) {
                        lc.applyLanguage(vi, true, false, false, true, 0, null);
                    }
                }
            }
        } catch (Throwable ignore) {
        }
    }
}
