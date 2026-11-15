#!/usr/bin/env python3
import pathlib
import re

# 1. Hook ConnectionsManager.java for Ghost Mode (Unread Messages, Unread Stories, Hide Online Status, Hide Typing)
cm_path = pathlib.Path("TMessagesProj/src/main/java/org/telegram/tgnet/ConnectionsManager.java")
if cm_path.exists():
    txt = cm_path.read_text(encoding="utf-8")
    hook_code = '''
        if (org.telegram.ui.LiquidGlass.DXPrivacyAndAIController.shouldInterceptOutgoingRequest(object)) {
            if (onComplete != null) {
                Utilities.stageQueue.postRunnable(() -> onComplete.run(new TLRPC.TL_boolTrue(), null));
            }
            return;
        }
'''
    target = "private void sendRequestInternal(TLObject object, RequestDelegate onComplete, RequestDelegateTimestamp onCompleteTimestamp, QuickAckDelegate onQuickAck, WriteToSocketDelegate onWriteToSocket, int flags, int datacenterId, int connectionType, boolean immediate, int requestToken) {"
    if target in txt and "shouldInterceptOutgoingRequest" not in txt:
        txt = txt.replace(target, target + hook_code, 1)
        cm_path.write_text(txt, encoding="utf-8")
        print("Hooked ConnectionsManager.java successfully.")

# 2. Hook MessagesStorage.java & MessagesController.java for Anti-Recall (Xem tin nhắn thu hồi của người khác)
ms_path = pathlib.Path("TMessagesProj/src/main/java/org/telegram/messenger/MessagesStorage.java")
if ms_path.exists():
    txt = ms_path.read_text(encoding="utf-8")
    pattern = r"(public\s+ArrayList<Long>\s+markMessagesAsDeletedInternal\s*\([^)]*\)\s*\{)"
    anti_recall_guard = r"\1\n        if (org.telegram.ui.LiquidGlass.DXModConfig.antiRecallEnabled) { org.telegram.ui.LiquidGlass.DXPrivacyAndAIController.getInstance().markMessagesRecalled(messages, dialogId); return new ArrayList<>(); }"
    if "markMessagesRecalled" not in txt:
        new_txt, count = re.subn(pattern, anti_recall_guard, txt, count=1)
        if count > 0:
            ms_path.write_text(new_txt, encoding="utf-8")
            print("Hooked MessagesStorage.java for Anti-Recall successfully.")

# 3. Hook LocaleController.java for Default Vietnamese Language & Real-time Activity Status
lc_path = pathlib.Path("TMessagesProj/src/main/java/org/telegram/messenger/LocaleController.java")
if lc_path.exists():
    txt = lc_path.read_text(encoding="utf-8")
    if 'currentInfo = getLanguageFromDict("en");' in txt and 'getLanguageFromDict("vi")' not in txt:
        txt = txt.replace(
            'currentInfo = getLanguageFromDict("en");',
            'currentInfo = getLanguageFromDict("vi") != null ? getLanguageFromDict("vi") : getLanguageFromDict("en");'
        )
    status_pattern = r"(public\s+static\s+String\s+formatUserStatus\s*\(\s*int\s+currentAccount\s*,\s*TLRPC\.User\s+user\s*,\s*boolean\[\]\s+isOnline\s*,\s*boolean\[\]\s+allowShowLastSeen\s*\)\s*\{)"
    status_hook = r'''\1
        if (user != null && org.telegram.ui.LiquidGlass.DXModConfig.realtimeActivityTracker) {
            String rt = org.telegram.ui.LiquidGlass.DXPrivacyAndAIController.getInstance().getFormattedRealtimeStatus(user.id);
            if (rt != null) return rt;
        }'''
    if "getFormattedRealtimeStatus" not in txt:
        txt, _ = re.subn(status_pattern, status_hook, txt, count=1)
    lc_path.write_text(txt, encoding="utf-8")
    print("Hooked LocaleController.java for Vietnamese default & Real-time status successfully.")
