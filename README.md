神奇的小玩意就是了

## 桌面构建

```powershell
git submodule update --init --recursive
.\gradlew.bat clean build dist
java -jar desktop/build/output/CAF-0.1.0.jar
```

需要 JDK 17。发布产物是 `desktop/build/output/CAF-0.1.0.jar`，可以和同平台、同架构的 JRE 一起分发。开发运行仍用 `./gradlew run`（Windows 用 `.\gradlew.bat run`）。

## 工程结构

依赖方向为 `desktop → core ← android`，根工程负责聚合任务。

- `core`：共享代码、Arc 界面、`Platform`、`LagrangeApi` 共享 JNA 声明与 ABI 类型；没有 SDL/Android 后端或 Lagrange 加载逻辑。
- `desktop`：SDL 入口、`DesktopPlatform`、本地库提取/加载/绑定、桌面打包与测试。
- `android`：空占位模块，仅使用 `base` 插件，不需要 Android SDK，也不生成 APK。后续再实现 Android 后端。
- 桌面先 `DesktopPlatform.load(data)`，再 `new CAF(platform)`；core 直接调用 `caf.lagrange.Start(index)` 等方法，不处理平台加载，也不反向依赖 desktop。
- 根任务 `build` / `run` / `dist` 保持不变，平台任务是 `:desktop:run` / `:desktop:dist`。Java 模块各自应用 java-library，Android 占位不套用 Java 插件。
- `clean` 会删除各模块 build 目录（包含 Gradle run 的数据）；重要配置、票据须先备份或使用外部数据目录。

已声明 NativeAPI 的 **49 个业务导出**；调用示例、原始签名与上游风险见 [共享方法与平台绑定](docs/platform-native-api.md)。这是原始 C ABI 绑定，不是完整 QQ SDK；不会自动登录，复杂输出仍须解码和深层释放。

## 内置本地库

- `natives/win-x64/Lagrange.Core.NativeAPI.dll` 作为资源打进 JAR，不再复制到 JAR 旁边；不打包 PDB。
- 启动时按 JVM 所在平台选择本地库，自动释放到 JAR 旁的 `data/natives/<平台>/<SHA-256>/`，再由 JNA 加载。DLL 仍需落盘，不能直接从压缩包中加载。
- 相同内容复用缓存，损坏的缓存重新释放；升级后的 DLL 使用新目录，避免覆盖被运行中进程锁定的旧 DLL。
- 目录随程序整体移动；也可通过 `java -Dcaf.datadir=可写目录 -jar CAF-0.1.0.jar` 指定数据目录。Gradle `run` 使用 `desktop/build/run-data/`。
- 当前仓库只包含 Windows x64 库，需要使用 x64 JRE。其他平台需先提供对应的 `natives/linux-x64/Lagrange.Core.NativeAPI.so`、`natives/osx-arm64/Lagrange.Core.NativeAPI.dylib` 等二进制，再重新打包；不会用 Windows DLL 代替。

加载和 JNA 映射由平台启动完成，core 收到可直接调用的接口实例；没有加入自动 QQ 登录或 Agent 业务逻辑。返回的 JNA 库实例不要手动 `close/dispose`：[Native AOT 不支持卸载本地库](https://learn.microsoft.com/en-us/dotnet/core/deploying/native-aot/libraries)。
