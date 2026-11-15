#!/usr/bin/env python3
import pathlib
import re
import shutil

# 0. Remove obsolete LiquidGlass overlay directory if still present
old_lg_dir = pathlib.Path("TMessagesProj/src/main/java/org/telegram/ui/LiquidGlass")
if old_lg_dir.exists():
    shutil.rmtree(old_lg_dir, ignore_errors=True)
    print("Removed obsolete TMessagesProj/src/main/java/org/telegram/ui/LiquidGlass directory.")

# 1. Hook / migrate ConnectionsManager.java
cm_path = pathlib.Path("TMessagesProj/src/main/java/org/telegram/tgnet/ConnectionsManager.java")
if cm_path.exists():
    txt = cm_path.read_text(encoding="utf-8")
    txt = txt.replace("org.telegram.ui.LiquidGlass.DXPrivacyAndAIController", "org.telegram.ui.DXPrivacyAndAIController")
    hook_code = '''
        if (org.telegram.ui.DXPrivacyAndAIController.shouldInterceptOutgoingRequest(object)) {
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
    print("Updated ConnectionsManager.java successfully.")

# 2. Hook / migrate MessagesStorage.java
ms_path = pathlib.Path("TMessagesProj/src/main/java/org/telegram/messenger/MessagesStorage.java")
if ms_path.exists():
    txt = ms_path.read_text(encoding="utf-8")
    txt = txt.replace("org.telegram.ui.LiquidGlass.DXModConfig.antiRecallEnabled", "org.telegram.ui.DXModConfig.isAntiRecall()")
    txt = txt.replace("org.telegram.ui.LiquidGlass.DXPrivacyAndAIController", "org.telegram.ui.DXPrivacyAndAIController")
    pattern = r"(public\s+ArrayList<Long>\s+markMessagesAsDeletedInternal\s*\([^)]*\)\s*\{)"
    anti_recall_guard = r"\1\n        if (org.telegram.ui.DXModConfig.isAntiRecall()) { org.telegram.ui.DXPrivacyAndAIController.getInstance().markMessagesRecalled(messages, dialogId); return new ArrayList<>(); }"
    if "markMessagesRecalled" not in txt:
        txt, _ = re.subn(pattern, anti_recall_guard, txt, count=1)
    ms_path.write_text(txt, encoding="utf-8")
    print("Updated MessagesStorage.java successfully.")

# 3. Hook / migrate LocaleController.java
lc_path = pathlib.Path("TMessagesProj/src/main/java/org/telegram/messenger/LocaleController.java")
if lc_path.exists():
    txt = lc_path.read_text(encoding="utf-8")
    txt = txt.replace("org.telegram.ui.LiquidGlass.DXModConfig.realtimeActivityTracker", "org.telegram.ui.DXModConfig.isRealtimeTracker()")
    txt = txt.replace("org.telegram.ui.LiquidGlass.DXPrivacyAndAIController", "org.telegram.ui.DXPrivacyAndAIController")
    if 'currentInfo = getLanguageFromDict("en");' in txt and 'getLanguageFromDict("vi")' not in txt:
        txt = txt.replace(
            'currentInfo = getLanguageFromDict("en");',
            'currentInfo = getLanguageFromDict("vi") != null ? getLanguageFromDict("vi") : getLanguageFromDict("en");'
        )
    status_pattern = r"(public\s+static\s+String\s+formatUserStatus\s*\(\s*int\s+currentAccount\s*,\s*TLRPC\.User\s+user\s*,\s*boolean\[\]\s+isOnline\s*,\s*boolean\[\]\s+allowShowLastSeen\s*\)\s*\{)"
    status_hook = r'''\1
        if (user != null && org.telegram.ui.DXModConfig.isRealtimeTracker()) {
            String rt = org.telegram.ui.DXPrivacyAndAIController.getInstance().getFormattedRealtimeStatus(user.id);
            if (rt != null) return rt;
        }'''
    if "getFormattedRealtimeStatus" not in txt:
        txt, _ = re.subn(status_pattern, status_hook, txt, count=1)
    lc_path.write_text(txt, encoding="utf-8")
    print("Updated LocaleController.java successfully.")
