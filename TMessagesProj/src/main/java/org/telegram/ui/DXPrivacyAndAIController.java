package org.telegram.ui;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

public class DXPrivacyAndAIController implements NotificationCenter.NotificationCenterDelegate {

    private static final DXPrivacyAndAIController INSTANCE = new DXPrivacyAndAIController();
    public static DXPrivacyAndAIController getInstance() { return INSTANCE; }

    private final ConcurrentHashMap<Long, Long> realtimeLastActiveMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> realtimeActionMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, String> recalledMessageIds = new ConcurrentHashMap<>();

    public void init(int account) {
        try {
            NotificationCenter nc = NotificationCenter.getInstance(account);
            nc.addObserver(this, NotificationCenter.didReceiveNewMessages);
            nc.addObserver(this, NotificationCenter.messagesDeleted);
            nc.addObserver(this, NotificationCenter.updateInterfaces);
        } catch (Throwable ignored) {
        }
    }

    public static boolean shouldInterceptOutgoingRequest(TLObject object) {
        if (object == null) return false;
        String name = object.getClass().getName();

        if (DXModConfig.isGhostNoRead()) {
            if (object instanceof TLRPC.TL_messages_readHistory
                    || object instanceof TLRPC.TL_channels_readHistory
                    || object instanceof TLRPC.TL_messages_readEncryptedHistory
                    || object instanceof TLRPC.TL_messages_readDiscussion
                    || name.contains("readHistory")
                    || name.contains("readMentions")) {
                return true;
            }
        }

        if (DXModConfig.isGhostNoStory()) {
            if (name.contains("TL_stories_readStories")
                    || name.contains("TL_stories_incrementStoryViews")
                    || name.contains("readStories")
                    || name.contains("incrementStoryViews")) {
                return true;
            }
        }

        if (DXModConfig.isGhostHideOnline() && name.contains("updateStatus")) {
            try {
                object.getClass().getField("offline").setBoolean(object, true);
            } catch (Throwable ignored) {
            }
        }

        if (DXModConfig.isGhostHideTyping()) {
            if (object instanceof TLRPC.TL_messages_setTyping || name.contains("setTyping")) {
                return true;
            }
        }

        return false;
    }

    public void markMessagesRecalled(ArrayList<Integer> msgIds, long dialogId) {
        if (msgIds == null) return;
        String timeStr = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        for (Integer id : msgIds) {
            if (id != null) {
                recalledMessageIds.put(id, "🚫 Đã thu hồi lúc " + timeStr);
            }
        }
    }

    public boolean isMessageRecalled(int messageId) {
        return recalledMessageIds.containsKey(messageId);
    }

    public String getRecalledBadge(int messageId) {
        return recalledMessageIds.get(messageId);
    }

    public void recordRealtimeUserActivity(long userId, String actionDesc) {
        if (userId <= 0) return;
        realtimeLastActiveMap.put(userId, System.currentTimeMillis());
        if (actionDesc != null) {
            realtimeActionMap.put(userId, actionDesc);
        }
    }

    public String getFormattedRealtimeStatus(long userId) {
        if (!DXModConfig.isRealtimeTracker()) return null;
        Long ts = realtimeLastActiveMap.get(userId);
        if (ts == null) return null;
        long diffSec = Math.max(0L, (System.currentTimeMillis() - ts) / 1000L);
        String timeStr = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(ts));
        String action = realtimeActionMap.getOrDefault(userId, "Hoạt động");
        if (diffSec < 15) {
            return "🟢 Đang " + action.toLowerCase() + " (Vừa xong • " + timeStr + ")";
        } else if (diffSec < 3600) {
            return "⚡ " + action + " " + (diffSec / 60) + "p " + (diffSec % 60) + "s trước (" + timeStr + ")";
        } else {
            return "🕒 Hoạt động lúc " + timeStr;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didReceiveNewMessages) {
            if (args.length < 2) return;
            final Long dialogId = (Long) args[0];
            final ArrayList<MessageObject> arr = (ArrayList<MessageObject>) args[1];
            if (arr == null || arr.isEmpty()) return;

            for (MessageObject msg : arr) {
                if (msg == null || msg.messageOwner == null) continue;
                long senderId = msg.getSenderId();
                if (senderId > 0) {
                    recordRealtimeUserActivity(senderId, "Gửi tin nhắn");
                }
                if (DXModConfig.isAiEnabled() && !msg.isOut() && dialogId > 0) {
                    CharSequence txt = msg.messageText;
                    if (!TextUtils.isEmpty(txt)) {
                        triggerAiAutoReply(account, dialogId, txt.toString());
                    }
                }
            }
        }
    }

    private void triggerAiAutoReply(final int account, final long dialogId, final String incomingText) {
        final String apiKey = DXModConfig.getAiKey();
        if (TextUtils.isEmpty(apiKey) || TextUtils.isEmpty(incomingText)) return;
        Utilities.globalQueue.postRunnable(() -> {
            try {
                URL url = new URL(DXModConfig.getAiEndpoint());
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(20000);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
                conn.setDoOutput(true);

                JSONObject body = new JSONObject();
                body.put("model", DXModConfig.getAiModel().trim());
                JSONArray messages = new JSONArray();

                JSONObject sysMsg = new JSONObject();
                sysMsg.put("role", "system");
                sysMsg.put("content", "Bạn là trợ lý AI thông minh trên TelegramDX, trả lời ngắn gọn, tự nhiên bằng tiếng Việt.");
                messages.put(sysMsg);

                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                userMsg.put("content", incomingText);
                messages.put(userMsg);

                body.put("messages", messages);

                byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload);
                }

                int code = conn.getResponseCode();
                if (code >= 200 && code < 300) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                    br.close();

                    JSONObject resp = new JSONObject(sb.toString());
                    JSONArray choices = resp.optJSONArray("choices");
                    if (choices != null && choices.length() > 0) {
                        JSONObject msgObj = choices.getJSONObject(0).optJSONObject("message");
                        final String replyText = msgObj != null ? msgObj.optString("content", "").trim() : "";
                        if (!TextUtils.isEmpty(replyText)) {
                            AndroidUtilities.runOnUIThread(() -> {
                                SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(replyText, dialogId);
                                SendMessagesHelper.getInstance(account).sendMessage(params);
                            });
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        });
    }
}
