神奇的小玩意就是了

## 桌面构建

```powershell
git submodule update --init --recursive
.\gradlew.bat clean build dist
java -jar desktop/build/output/CAF-0.1.0.jar
```

需要 JDK 17。发布产物是 `desktop/build/output/CAF-0.1.0.jar`，可以和同平台、同架构的 JRE 一起分发。

## 内置本地库

- `natives/win-x64/Lagrange.Core.NativeAPI.dll` 作为资源打进 JAR，不再复制到 JAR 旁边；不打包 PDB。
- 启动时按 JVM 所在平台选择本地库，自动释放到 JAR 旁的 `data/natives/<平台>/<SHA-256>/`，再由 JNA 加载。DLL 仍需落盘，不能直接从压缩包中加载。
- 相同内容复用缓存，损坏的缓存重新释放；升级后的 DLL 使用新目录，避免覆盖被运行中进程锁定的旧 DLL。
- 目录随程序整体移动；也可通过 `java -Dcaf.datadir=可写目录 -jar CAF-0.1.0.jar` 指定数据目录。Gradle `run` 使用 `desktop/build/run-data/`。
- 当前仓库只包含 Windows x64 库，需要使用 x64 JRE。其他平台需先提供对应的 `natives/linux-x64/Lagrange.Core.NativeAPI.so`、`natives/osx-arm64/Lagrange.Core.NativeAPI.dylib` 等二进制，再重新打包；不会用 Windows DLL 代替。

加载器只负责释放和加载，没有加入 QQ 登录或 Agent 业务逻辑。返回的 JNA 库实例不要手动 `close/dispose`：[Native AOT 不支持卸载本地库](https://learn.microsoft.com/en-us/dotnet/core/deploying/native-aot/libraries)。
