package caliniya.caf.lagrange;

import com.sun.jna.*;

import caliniya.caf.lagrange.LagrangeTypes.*;

/**
 * Shared, low-level C ABI for Lagrange NativeAPI 20c2ba0 (49 exports).
 * No static INSTANCE, native loading, platform selection or automatic login here.
 * Platform code supplies the implementation; core calls the same methods everywhere.
 *
 * Returned pointers are native-owned allocations: copy the data, then free every
 * nested allocation and the outer allocation via FreeMemory. It is NOT recursive.
 * Never pass JNA-owned input memory to FreeMemory. See docs/platform-native-api.md
 * for upstream limitations; declaring a function does not make it production-safe.
 */
public interface LagrangeApi extends Library{
    // Lifecycle. Initialize accepts pointers; Start only submits asynchronous login.
    int Initialize(Pointer config, Pointer keystore, Pointer appInfo);
    int Start(int index);
    int Stop(int index);
    void FreeMemory(Pointer pointer);

    // Event getters return EventArray*, except GetEventCount (ReverseEventCountStruct*).
    Pointer GetEventCount(int index);
    Pointer GetCaptchaEvent(int index);
    Pointer GetBotFriendRecallEvent(int index);
    Pointer GetBotFriendRequestEvent(int index);
    Pointer GetBotGroupInviteNotificationEvent(int index);
    Pointer GetBotGroupJoinNotificationEvent(int index);
    Pointer GetBotGroupMemberDecreaseEvent(int index);
    Pointer GetBotGroupMemberIncreaseEvent(int index);
    Pointer GetBotGroupNudgeEvent(int index);
    Pointer GetBotGroupReactionEvent(int index);
    Pointer GetBotGroupRecallEvent(int index);
    Pointer GetLoginEvent(int index);
    Pointer GetBotLogEvent(int index);
    Pointer GetMessageEvent(int index);
    Pointer GetNewDeviceVerifyEvent(int index);
    Pointer GetOfflineEvent(int index);
    Pointer GetOnlineEvent(int index);
    Pointer GetQrCodeEvent(int index);
    Pointer GetQrCodeQueryEvent(int index);
    Pointer GetRefreshKeystoreEvent(int index);
    Pointer GetSMSEvent(int index);

    // Message builders. Bytes and Message parameters are structs BY VALUE, not pointers.
    int CreateMessageBuilder(int index);
    void AddText(int index, int id, Bytes.ByValue text);
    void AddImage(int index, int id, Bytes.ByValue image, Bytes.ByValue summary, int subType);
    void AddLocalImage(int index, int id, Bytes.ByValue path, Bytes.ByValue summary, int subType);
    void AddMention(int index, int id, long uin, Bytes.ByValue display);
    void AddMultiMsg(int index, int id, Bytes.ByValue resId);
    void AddRecord(int index, int id, Bytes.ByValue bytes);
    void AddLocalRecord(int index, int id, Bytes.ByValue path);
    void AddVideo(int index, int id, Bytes.ByValue bytes, Bytes.ByValue thumbnail);
    void AddLocalVideo(int index, int id, Bytes.ByValue path, Bytes.ByValue thumbnailPath);
    Pointer SendFriendMessage(int index, int id, long friendUin);
    Pointer SendGroupMessage(int index, int id, long groupUin);
    void RecallMessage(int index, Message.ByValue message);

    // ulong is a fixed-width 64-bit value, NOT NativeLong. Preserve its bit pattern.
    Pointer FetchGroupNotifications(int index, long count, long start);
    Pointer FetchFilteredGroupNotifications(int index, long count, long start);
    // Independent C ABI bool arguments use byte 0/1, unlike the BOOL32 fields in Config.
    Pointer GetGroupList(int index, byte refresh);
    Pointer GetMemberList(int index, long groupUin, byte refresh);
    void QuitGroup(int index, long groupUin);
    void RenameGroup(int index, long groupUin, Bytes.ByValue name);
    void RenameGroupMember(int index, long groupUin, long targetUin, Bytes.ByValue name);
    void SendGroupNudge(int index, long peerUin, long targetUin);
    void SetGroupSpecialTitle(int index, long groupUin, long targetUin, Bytes.ByValue title);
    void SetGroupNotification(int index, long groupUin, long sequence, int type, byte isFiltered, int operate, Bytes.ByValue message);
    void SetGroupReaction(int index, long groupUin, long sequence, Bytes.ByValue code, byte isAdd);
}
