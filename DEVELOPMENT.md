# 开发指南

使用 JDK 21 和 Android SDK Platform 37 构建。AndroidX Core 1.19.1 要求 API 37，因此应用使用 API 37 编译，target SDK 也是 37，minimum SDK 保持 33。Wrapper 使用 Gradle 9.8.2。

将 `ANDROID_HOME` 设为本地 Android SDK 位置，或创建被忽略的 `local.properties`，通过 `sdk.dir` 指向 SDK。CI 使用 runner 的 SDK 环境；不得提交机器专用 SDK 路径。接受 SDK 许可证后，Gradle 可以安装缺失的 SDK 包。

通过 Gradle Wrapper 构建、运行本地单元测试并检查 lint：

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Android Test 工作流还会在 API 34 模拟器上运行 instrumentation 测试。应用不需要 Google Play services，因此 CI 使用 AOSP 镜像，避免 Google APIs 镜像的 Pixel Launcher 启动 ANR 对话框遮挡启动器 UI。测试前会唤醒并解锁模拟器；UI 断言保持启用，默认桌面应用不变。验证 target SDK 变更时，在 API 37 设备或模拟器上运行 `./gradlew :app:connectedDebugAndroidTest`。应用 lint 不应有警告或错误。

应用与小部件选择列表使用 `ListAdapter` 和不可变快照。条目标识同时包含组件和用户配置；显示内容的变化必须纳入 diff 比较。更新已安装应用时，保持当前搜索查询有效。
