package org.telegram.ui.LiquidGlass;

import android.text.TextUtils;
import android.util.LongSparseArray;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
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

/**
 * Bộ xử lý trung tâm cho:
 * 1. Ghost Mode:
 *    - Đọc tin nhắn không hiện đã xem (chặn messages.readHistory, channels.readHistory, messages.readEncryptedHistory)
 *    - Xem Story không hiện mắt (chặn stories.readStories, stories.incrementStoryViews)
 *    - Ẩn trạng thái hoạt động & đang gõ (chặn account.updateStatus online=true, messages.setTyping)
 * 2. Anti-Recall (Chống thu hồi tin nhắn):
 *    - Giữ lại toàn bộ tin nhắn bị đối phương thu hồi trong bộ nhớ và gắn nhãn "[Đã thu hồi]"
 * 3. Real-time Activity Tracker:
 *    - Ghi nhận chính xác tới từng giây thời điểm người dùng bất kỳ chuyển trạng thái Online / Gõ phím / Gửi tin
 * 4. AI Auto-Reply:
 *    - Tự động trả lời tin nhắn mới bằng API Key & Model tự chọn (hỗ trợ OpenAI-compatible / Gemini).
 */
public class DXPrivacyAndAIController implements NotificationCenter.NotificationCenterDelegate {

    private static final DXPrivacyAndAIController INSTANCE = new DXPrivacyAndAIController();
    public static DXPrivacyAndAIController getInstance() { return INSTANCE; }

    // Lưu mốc thời gian hoạt động thực tế gần nhất (epoch millis) của từng user_id
    private final ConcurrentHashMap<Long, Long> realtimeLastActiveMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> realtimeActionMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, String> recalledMessageIds = new ConcurrentHashMap<>();

    public void init(int account) {
        NotificationCenter nc = NotificationCenter.getInstance(account);
        nc.addObserver(this, NotificationCenter.didReceiveNewMessages);
        nc.addObserver(this, NotificationCenter.messagesDeleted);
        nc.addObserver(this, NotificationCenter.updateInterfaces);
    }

    /**
     * Kiểm tra trong ConnectionsManager.sendRequestInternal:
     * Nếu trả về true, request sẽ bị chặn không gửi lên server Telegram và giả lập phản hồi thành công ở local.
     */
    public static boolean shouldInterceptOutgoingRequest(TLObject object) {
        if (object == null) return false;
        String name = object.getClass().getName();

        // 1. Không hiện "Đã xem" khi đọc tin nhắn
        if (DXModConfig.ghostUnreadMessages) {
            if (object instanceof TLRPC.TL_messages_readHistory
                    || object instanceof TLRPC.TL_channels_readHistory
                    || object instanceof TLRPC.TL_messages_readEncryptedHistory
                    || object instanceof TLRPC.TL_messages_readDiscussion
                    || name.contains("readHistory")
                    || name.contains("readMentions")) {
                return true;
            }
        }

        // 2. Xem Story người khác không hiện mắt
        if (DXModConfig.ghostUnreadStories) {
            if (name.contains("TL_stories_readStories")
                    || name.contains("TL_stories_incrementStoryViews")
                    || name.contains("readStories")
                    || name.contains("incrementStoryViews")) {
                return true;
            }
        }

        // 3. Ẩn hoạt động (Online) của bản thân đối với người khác
        if (DXModConfig.ghostHideOnlineStatus && name.contains("updateStatus")) {
            try {
                object.getClass().getField("offline").setBoolean(object, true);
            } catch (Throwable ignored) {
            }
        }

        // 4. Ẩn trạng thái đang soạn tin nhắn (Typing...)
        if (DXModConfig.ghostHideTyping) {
            if (object instanceof TLRPC.TL_messages_setTyping || name.contains("setTyping")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Đánh dấu tin nhắn thu hồi thay vì xóa khỏi máy khi bật Anti-Recall.
     */
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

    /**
     * Ghi nhận hoạt động theo thời gian thực khi nhận gói tin UpdateUserStatus / UpdateUserTyping / NewMessage.
     */
    public void recordRealtimeUserActivity(long userId, String actionDesc) {
        if (userId <= 0) return;
        realtimeLastActiveMap.put(userId, System.currentTimeMillis());
        if (actionDesc != null) {
            realtimeActionMap.put(userId, actionDesc);
        }
    }

    public String getFormattedRealtimeStatus(long userId) {
        if (!DXModConfig.realtimeActivityTracker) return null;
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
                if (DXModConfig.aiAutoReplyEnabled && !msg.isOut() && dialogId > 0) {
                    CharSequence txt = msg.messageText;
                    if (!TextUtils.isEmpty(txt)) {
                        triggerAiAutoReply(account, dialogId, txt.toString());
                    }
                }
            }
        }
    }

    private void triggerAiAutoReply(final int account, final long dialogId, final String incomingText) {
        if (TextUtils.isEmpty(DXModConfig.aiApiKey) || TextUtils.isEmpty(incomingText)) return;
        Utilities.globalQueue.postRunnable(() -> {
            try {
                URL url = new URL(DXModConfig.aiEndpointUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(20000);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + DXModConfig.aiApiKey.trim());
                conn.setDoOutput(true);

                JSONObject body = new JSONObject();
                body.put("model", DXModConfig.aiModelName.trim());
                JSONArray messages = new JSONArray();

                JSONObject sysMsg = new JSONObject();
                sysMsg.put("role", "system");
                sysMsg.put("content", DXModConfig.aiSystemPrompt);
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
