# 共享方法声明与平台绑定

## 现在的调用方向

```text
core:     LagrangeApi（共享 JNA 接口，49 个原始导出）
          LagrangeTypes / NativeInput（共享 ABI 类型、输入内存）
          Platform（只提供已经准备好的对象）
                          ↑ 注入
 desktop: 选平台 → 从 JAR 释放本地库 → 加载 → 解析导出 → JNA 映射
 android: 空占位模块，以后再提供相同接口的实现
```

这次按照“在 core 定义方法，平台把本地库的方法映射装上去”实现。这里的“替换”是**接口实现注入**，不是修改 Java 字节码。

- `LagrangeApi` 继承 JNA `Library`，方法名直接对应 C 导出名，没有会触发加载的静态 `INSTANCE`。
- JNA 是 **core 的共享依赖**：core 可以直接操作原始 Pointer/Structure；这里不额外造一整套 QQ 业务 SDK。
- 只有 desktop 使用 `Native.load` / `NativeLibrary`、平台判断、资源释放与路径处理。core 不选择系统、不加载 Lagrange，也没有 `loadNativeLibraries()` 钩子。
- `DesktopPlatform.load(data)` 完成加载和全部符号检查，返回已准备好的平台对象；入口再 `new CAF(platform)`，最后启动 SDL。
- `CAF` 保存注入的 `public final LagrangeApi lagrange`。core 内直接 `lagrange.Start(index)` 等调用，不反向引用 desktop。实例字段避免全局映射被随意覆盖，测试也能直接注入假的 Java 实现。
- 启动只装载和绑定，不调用 `Initialize`、`Start`，不联网登录。
- `android` 仅使用 Gradle `base` 插件，无 SDK/NDK、Activity、APK 或假实现，不影响桌面构建。

## 核心里怎么用

下面片段放在 core 中、拿到 `LagrangeApi api` 后即可使用。`caf.lagrange` 就是已经映射好的 API，无需再加载 DLL。

```java
import caliniya.caf.lagrange.*;
import com.sun.jna.Pointer;

// 以下代码应在用户主动操作后的工作线程运行，不放进每帧 update。
// signUrl 从本地配置读取：必须是有效 HTTP(S) 签名服务，并匹配本版默认 AppInfo。
LagrangeApi api = caf.lagrange;
int index;
try(NativeInput endpoint = NativeInput.utf8(signUrl)){
    LagrangeTypes.Config config = new LagrangeTypes.Config();
    config.signAddress = endpoint.value();
    config.write();
    index = api.Initialize(config.getPointer(), Pointer.NULL, Pointer.NULL);
}
if(index < 0) throw new IllegalStateException("Initialize failed");

// 单独在需要登录时调用。返回 0 只代表已提交，不代表登录成功。
int status = api.Start(index);
```

`NULL keystore` 会创建新的设备/会话，不会恢复上次登录。**当前还没有安全的票据保存/回填封装；不能据此宣称已实现自动重登。** 真正上线要等 Online 事件。队列的原生并发问题尚未修复，完整 QR 轮询/自动登录不在本次补丁范围。

共享输入结构这样用（示例只展示 AddText，不发送、不产生需要深层释放的返回值）：

```java
try(NativeInput text = NativeInput.utf8("你好")){
    api.AddText(index, builderId, text.value()); // ByteArrayNative 按值传递
}
```

`builderId` 需要先由 `CreateMessageBuilder` 取得；不要在每帧创建或长期遗弃 builder。独立 bool 参数传 `LagrangeTypes.yes/no`（byte 1/0），例如 `GetGroupList(index, LagrangeTypes.no)`，返回的列表指针仍须调用方正确解码和深层释放。

## 当前边界

本次核对 `lagrange` 固定提交 `20c2ba079b5ee67156bd71419ed54ea61a24e91f`，49 个 Java 方法名逐个匹配源码及仓库 Windows x64 DLL 导出表。DLL 的 `DotNetRuntimeDebugHeader` 是运行时调试数据，不映射为方法。

- 已实现：49 个原始函数声明、需要的按值输入布局、配置和基本登录事件布局、JNA 输入内存所有权、桌面装载/绑定/注入。
- 桌面映射拦截负 index、NULL config 和非 0/1 的 bool 输入，原始调用异常不会被反射异常多包一层。
- 未实现：全部复杂事件/消息/群通知的 Java 解码器、输出深层释放器、会话/登录 UI、账号实测、Android 后端。
- **原始 API 不隐藏指针，也不自动释放输出。** `FreeMemory` 不递归，只释放外层会泄漏；不同输出必须按真实类型逐层释放。不要把本补丁当作完整 QQ SDK。
- 此补丁不修改 lagrange/arc 子模块，不需要为这次 Java 映射重新编译已有 Windows DLL；源码里已有的原生问题仍然存在。

## ABI / JNA 映射约定

| 原生类型 / 场景 | Java / JNA 处理 |
| --- | --- |
| int / StatusCode | int；枚举本版底层 int32 |
| long / ulong | long，保留 64 位位模式；无符号展示、范围检查另做，不用 NativeLong 代替固定宽度 long |
| IntPtr | Pointer；原始 API 调用方负责拷贝及按真实布局深层释放 |
| ByteArrayNative | FieldOrder(Length, Data)；64 位大小 16，Data 偏移 8；UTF-8 字符串也是这个结构，不是直接传 Java String |
| ByteArrayNative 按值参数 | Structure.ByValue；AddText / AddImage 等均需按值，而不是 struct* |
| BotMessageStruct 按值参数 | RecallMessage 需要完整结构按值，不能只拿 Pointer 糊过去；上游实现还需排查 |
| EventArrayStruct* | 先读头部 Pointer Events + int Count，再按真实记录大小遍历；64 位头部大小 16 |
| BotConfigStruct* | Initialize 接收指针；本版 Marshal.PtrToStructure 的 bool 字段按 4 字节 BOOL，配置大小 48，SignAddress 偏移 32 |
| 独立 bool 形参 | byte 0/1；与 Config 中经过 Marshal 的 BOOL32 字段不同。本次核对了 Windows x64 导出对低 8 位的使用，并另做本地 ABI 探针；不代表实际群操作已联机验证 |

输入的 Java/JNA Memory 由 Java/JNA 管理，不能交给原生 FreeMemory。输出要分别释放每个嵌套分配、Events 数组和外层头；FreeMemory 本身不会递归。数据拷贝完再跨线程使用，NativeAOT 库不得主动 close/dispose。

## 源码中已经看到的限制 / 问题

1. **会话 index 不稳定且负索引不安全**：Stop 用 Contexts.RemoveAt，导致后续 index 变化；多处只检查 index >= Count，未检查负值；GetEventCount 连上界检查也没有，未 Initialize 或 Stop 后传旧索引会直接终止宿主进程（离线验证已复现）。当前原始 API 暴露 int index，桌面映射会拦截负数，但不能解决索引移动；在原生修复前，调用方只使用单会话，不在 Stop 后保留旧 index。
2. **事件队列没有同步保护**：注册处理器往 List.Add，拉取时反复读 Count、分配、遍历、Clear；新增事件可能丢失，也有分配大小与遍历数不一致的内存风险。Java 单线程轮询不能替代原生生产者/消费者使用同一把锁。
3. **keystore 的 PsKey 输入所有权不一致**：ToKeystoreWithoutFree 仍通过会释放内存的字典/字节数组隐式转换读取 PsKey；不要直接传 JNA 分配的字典后又自行 close，需先修原生路径。
4. **群撤回事件用了错误的结构泛型**：GetBotGroupRecallEvent 调用 GetEventStructPtr<BotGroupReactionEventStruct>，不应按这个错误布局编写绑定。
5. **部分导出的事件没有注册生产者**：ReverseEventInvoker 构造里漏了 FriendRecall、GroupRecall、SMS 的 RegisterEventHandler。GetEventCount 另有 InviteSelf 计数字段，但没有相应拉取导出，不能直接等同于完整可用事件支持。
6. **群通知列表是不同长度的混合记录**：总分配按各类型大小相加，却按 i * 当前记录大小定位，第一个 Join 分支还用了 EventStruct 的大小。需修正布局/游标，不能当统一固定步长数组硬解。
7. **消息嵌套结构有风险**：BotMessageStruct 的 GroupFileEntity 分支分配用了 MultiMsgEntityStruct 的大小；RecallMessage 的 PtrToStructure 写法、Contact/Receiver 类型和未等待的异步调用也需验证。涉及这些能力的回执与撤回暂不承诺可用。
8. **未发送的 builder 无导出释放入口**：SendMessageContext 有 RemoveMessageBuilder，但没有对应导出。调用方不宜先创建原生 builder 等用户编辑，应发送时再创建，异常/取消后的清理还需补原生入口。
9. **登录相关**：Start 是异步发起，不是成功回执；缺少独立密码登录 / SubmitCaptcha / SubmitSMSCode / Logout / GetKeystore（直接快照）入口，不能把 C# Core 的能力当作 NativeAPI 导出能力。现阶段通过 QR 和 RefreshKeystore 事件设计流程。
10. **协议与宿主 OS 分离**：Windows x64 DLL 不代表选 Windows QQ 协议；本版 NativeAPI 的 Windows/Mac 协议转换会抛异常。Linux=4 的签名地址不要为空，且须匹配 AppInfo 版本。

以上为固定版本的源码静态核对，不是已经完成账号登录、网络发送或群操作实测。这次不修改 lagrange 子模块或重新编译用户 DLL；后续修原生实现时需要重新生成对应二进制。

## 完整业务导出清单（49）

下表保留 C# 原始签名，Java 声明见 core/src/caliniya/caf/lagrange/LagrangeApi.java。49 个入口均已声明，桌面启动时逐个解析符号；这不等于所有复杂输出都已完成解码、深层释放或联机实测。

### 生命周期 / 内存（4）

| 导出名 | 原始签名 | 源码 |
| --- | --- | --- |
| `Initialize` | `int Initialize(IntPtr botConfigPtr, IntPtr keystorePtr, IntPtr appInfoPtr)` | `Program.cs:12` |
| `Start` | `StatusCode Start(int index)` | `Program.cs:40` |
| `Stop` | `StatusCode Stop(int index)` | `Program.cs:62` |
| `FreeMemory` | `void FreeMemory(IntPtr ptr)` | `Program.cs:75` |

### 事件（21：计数 + 20 种拉取）

| 导出名 | 原始签名 | 源码 |
| --- | --- | --- |
| `GetEventCount` | `IntPtr GetEventCount(int index)` | `ReverseEvent/EventEntryPoint.cs:9` |
| `GetCaptchaEvent` | `IntPtr GetCaptchaEvent(int index)` | `ReverseEvent/EventEntryPoint.cs:41` |
| `GetBotFriendRecallEvent` | `IntPtr GetBotFriendRecallEvent(int index)` | `ReverseEvent/EventEntryPoint.cs:56` |
| `GetBotFriendRequestEvent` | `IntPtr GetBotFriendRequestEvent(int index)` | `ReverseEvent/EventEntryPoint.cs:71` |
| `GetBotGroupInviteNotificationEvent` | `IntPtr GetBotGroupInviteNotificationEvent(int index)` | `ReverseEvent/EventEntryPoint.cs:86` |
| `GetBotGroupJoinNotificationEvent` | `IntPtr GetBotGroupJoinNotificationEvent(int index)` | `ReverseEvent/EventEntryPoint.cs:101` |
| `GetBotGroupMemberDecreaseEvent` | `IntPtr GetBotGroupMemberDecreaseEvent(int index)` | `ReverseEvent/EventEntryPoint.cs:116` |
| `GetBotGroupMemberIncreaseEvent` | `IntPtr GetBotGroupMemberIncreaseEvent(int index)` | `ReverseEvent/EventEntryPoint.cs:131` |
| `GetBotGroupNudgeEvent` | `IntPtr GetBotGroupNudgeEvent(int index)` | `ReverseEvent/EventEntryPoint.cs:146` |
| `GetBotGroupReactionEvent` | `IntPtr GetBotGroupReactionEvent(int index)` | `ReverseEvent/EventEntryPoint.cs:161` |
| `GetBotGroupRecallEvent` | `IntPtr GetBotGroupRecallEvent(int index)` | `ReverseEvent/EventEntryPoint.cs:176` |
| `GetLoginEvent` | `IntPtr GetLoginEvent(int index)` | `ReverseEvent/EventEntryPoint.cs:191` |
| `GetBotLogEvent` | `IntPtr GetBotLogEvent(int index)` | `ReverseEvent/EventEntryPoint.cs:206` |
| `GetMessageEvent` | `IntPtr GetMessageEvent(int index)` | `ReverseEvent/EventEntryPoint.cs:221` |
| `GetNewDeviceVerifyEvent` | `IntPtr GetNewDeviceVerifyEvent(int index)` | `ReverseEvent/EventEntryPoint.cs:236` |
| `GetOfflineEvent` | `IntPtr GetOfflineEvent(int index)` | `ReverseEvent/EventEntryPoint.cs:251` |
| `GetOnlineEvent` | `IntPtr GetOnlineEvent(int index)` | `ReverseEvent/EventEntryPoint.cs:266` |
| `GetQrCodeEvent` | `IntPtr GetQrCodeEvent(int index)` | `ReverseEvent/EventEntryPoint.cs:281` |
| `GetQrCodeQueryEvent` | `IntPtr GetQrCodeQueryEvent(int index)` | `ReverseEvent/EventEntryPoint.cs:296` |
| `GetRefreshKeystoreEvent` | `IntPtr GetRefreshKeystoreEvent(int index)` | `ReverseEvent/EventEntryPoint.cs:311` |
| `GetSMSEvent` | `IntPtr GetSMSEvent(int index)` | `ReverseEvent/EventEntryPoint.cs:326` |

### 消息（13）

| 导出名 | 原始签名 | 源码 |
| --- | --- | --- |
| `CreateMessageBuilder` | `int CreateMessageBuilder(int index)` | `SendMessageEntryPoint.cs:13` |
| `AddText` | `void AddText(int index, int id, ByteArrayNative byteArrayNative)` | `SendMessageEntryPoint.cs:25` |
| `AddImage` | `void AddImage( int index, int id, ByteArrayNative byteArrayNative, ByteArrayNative summary, int subType )` | `SendMessageEntryPoint.cs:38` |
| `AddLocalImage` | `void AddLocalImage( int index, int id, ByteArrayNative byteArrayNative, ByteArrayNative summary, int subType )` | `SendMessageEntryPoint.cs:64` |
| `AddMention` | `void AddMention( int index, int id, long uin, ByteArrayNative display )` | `SendMessageEntryPoint.cs:90` |
| `AddMultiMsg` | `void AddMultiMsg( int index, int id, ByteArrayNative resId )` | `SendMessageEntryPoint.cs:111` |
| `AddRecord` | `void AddRecord(int index, int id, ByteArrayNative byteArrayNative)` | `SendMessageEntryPoint.cs:129` |
| `AddLocalRecord` | `void AddLocalRecord(int index, int id, ByteArrayNative byteArrayNative)` | `SendMessageEntryPoint.cs:141` |
| `AddVideo` | `void AddVideo( int index, int id, ByteArrayNative byteArrayNative, ByteArrayNative thumbnail )` | `SendMessageEntryPoint.cs:156` |
| `AddLocalVideo` | `void AddLocalVideo( int index, int id, ByteArrayNative byteArrayNative, ByteArrayNative thumbnail )` | `SendMessageEntryPoint.cs:179` |
| `SendFriendMessage` | `IntPtr SendFriendMessage(int index, int id, long friendUin)` | `SendMessageEntryPoint.cs:202` |
| `SendGroupMessage` | `IntPtr SendGroupMessage(int index, int id, long groupUin)` | `SendMessageEntryPoint.cs:231` |
| `RecallMessage` | `void RecallMessage(int index, BotMessageStruct message)` | `SendMessageEntryPoint.cs:260` |

### 群查询 / 管理（11）

| 导出名 | 原始签名 | 源码 |
| --- | --- | --- |
| `FetchGroupNotifications` | `IntPtr FetchGroupNotifications(int index, ulong count, ulong start )` | `OperateGroupEntryPoint.cs:13` |
| `FetchFilteredGroupNotifications` | `IntPtr FetchFilteredGroupNotifications(int index, ulong count, ulong start )` | `OperateGroupEntryPoint.cs:27` |
| `GetGroupList` | `IntPtr GetGroupList(int index, bool refresh )` | `OperateGroupEntryPoint.cs:41` |
| `GetMemberList` | `IntPtr GetMemberList(int index, long groupUin, bool refresh )` | `OperateGroupEntryPoint.cs:79` |
| `QuitGroup` | `void QuitGroup(int index, long groupUin)` | `OperateGroupEntryPoint.cs:117` |
| `RenameGroup` | `void RenameGroup(int index, long groupUin, ByteArrayNative name)` | `OperateGroupEntryPoint.cs:129` |
| `RenameGroupMember` | `void RenameGroupMember(int index, long groupUin, long targetUin, ByteArrayNative name)` | `OperateGroupEntryPoint.cs:141` |
| `SendGroupNudge` | `void SendGroupNudge(int index, long peerUin, long targetUin)` | `OperateGroupEntryPoint.cs:153` |
| `SetGroupSpecialTitle` | `void SetGroupSpecialTitle(int index, long groupUin, long targetUin, ByteArrayNative title)` | `OperateGroupEntryPoint.cs:165` |
| `SetGroupNotification` | `void SetGroupNotification(int index, long groupUin, ulong sequence, int type, bool isFiltered, int operate, ByteArrayNative message)` | `OperateGroupEntryPoint.cs:177` |
| `SetGroupReaction` | `void SetGroupReaction(int index, long groupUin, ulong sequence, ByteArrayNative code, bool isAdd)` | `OperateGroupEntryPoint.cs:195` |

## 后续再做什么

当前只解决“core 定义方法、平台加载后注入映射”。不增加会话管理器、线程池或自动登录流程。

1. 先修复原生队列同步、索引和内存问题，再实现完整 QQ 会话、事件复制/深层释放、票据持久化。
2. 消息、群通知等复杂输出的完整结构和 Java 业务封装，随后按需要补充，不把裸 Pointer 当长期对象。
3. Android 当前仍是空模块；未来由安卓入口准备同一个 LagrangeApi，再注入 CAF。Android/Bionic 库、JNA 原生依赖与生命周期需要另外实现和真机验证。

参考：[JNA Native.load 接口映射](https://java-native-access.github.io/jna/5.19.1/javadoc/com/sun/jna/Native.html)、[Structure 的指针/按值规则](https://java-native-access.github.io/jna/5.19.1/javadoc/com/sun/jna/Structure.html)、[Native AOT 本地库与卸载限制](https://learn.microsoft.com/en-us/dotnet/core/deploying/native-aot/libraries)。具体 ABI 以固定子模块源码和实际二进制为准。
