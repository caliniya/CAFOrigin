# Android 占位模块

目前仅注册 Gradle 模块，使用 `base` 插件，不依赖 Android SDK/NDK，不生成 APK，也没有安卓本地库实现。

当前重心是桌面端。后续再改用 Android 应用插件，依赖 `core` 和 Arc Android 后端，实现 `Platform`：安卓入口先装载/绑定本地库，提供 core 的同一个 `LagrangeApi` 实例，再注入 CAF；并添加入口、生命周期和 ABI 对应的本地库打包。

不要把桌面 JAR 的 DLL 释放流程直接复制到这里；Android/Bionic 二进制、依赖及真机兼容性需要单独验证。
