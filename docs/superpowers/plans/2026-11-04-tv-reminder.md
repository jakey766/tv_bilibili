# 电视使用时长提醒实施计划

> 执行方式：本会话内逐项执行，使用 executing-plans；遵循用户要求，不创建 worktree，不执行 git add/commit。完成后按 requesting-code-review 进行独立代码检查。

**目标：** 交付支持当天累计、可配置提醒时间和启用开关的电视 APK。

**架构：** Kotlin JVM 核心负责计时和提醒状态机，Android 应用负责无障碍前台检测、悬浮窗、设置和持久化。没有服务器或网络请求。

**技术：** Kotlin 2.1.20、Android Gradle Plugin 8.9.2、Gradle 8.11.1、Android SDK 35、原生 Views、JUnit；复用 family-location 已有工具的兼容版本。

## 1. 构建与核心行为

- [x] 创建根 Gradle 配置、`:core` JVM 模块和 `:app` Android 模块；复制标准 Gradle wrapper。
- [x] 创建 `scripts/android.sh`，自动识别本机 JBR/SDK，构建缓存和签名保存在本项目 `.cache`，支持环境变量覆盖。
- [x] 在 `core/src/test/kotlin/com/jk/tvreminder/core/ReminderEngineTest.kt` 先定义默认和自定义边界、暂停恢复、去重、重新进入和跨天的行为测试。
- [x] 运行 `./scripts/android.sh :core:test` 确认未实现功能使测试失败。
- [x] 实现 `ReminderEngine.kt`，使用传入的单调时钟和当地日期；由 `update(now, watching, config)` 同时处理时钟、前台和配置变更，`confirm()` 关闭当前提醒，`snapshot()` 导出累计进度；返回提醒状态供 Android 渲染。
- [x] 再次执行同一命令，要求所有核心测试通过。

## 2. Android 接入

- [x] `ReminderPreferences.kt` 存储设置和日期/累计毫秒，只存必要本地信息。
- [x] `MainActivity.kt` 提供电视遥控器可操作的开关、两个整数阈值、保存、今日时长、目标客户端选择、无障碍入口和状态。
- [x] `ForegroundDetector.kt` 从可见应用窗口识别前台；过滤自身悬浮窗，系统遮罩不直接重置计时；无法确认前台时暂停。
- [x] `ReminderService.kt` 接收窗口事件、屏幕和设置变化，以单调时间驱动状态机；每 5 秒与暂停时保存；自带弹窗不触发重新进入。
- [x] `ReminderOverlay.kt` 实现 5 秒自动关闭和遥控器确认两类窗口，禁止重复显示，返回键不关闭确认窗口，退出/息屏/停用可以撤下。
- [x] 在 Manifest 声明 Leanback 启动入口、无障碍服务、非必需触摸屏和目标应用查询；不声明网络权限。
- [x] 在 `app/src/test` 验证设置保存、输入校验、目标应用过滤、窗口确认和取消倒计时，补充核心边界回归。

## 3. 交付验证

- [x] 运行 `./scripts/android.sh :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`，修复实际失败并记录结果。
- [x] 独立审查计时、焦点、服务恢复、权限和版本兼容，解决重要问题。
- [x] 如果有可用模拟器/已连接设备，验证启动、设置和服务路径；不擅自连接家庭网络上的设备。
- [x] 检查 APK 签名、包信息和可安装产物，复制到 `dist/`。
- [x] 编写 `README.md`：安装、启用无障碍、选择客户端、设置、构建测试和真机验收步骤，明确未验证的 REDMI 兼容性。

## 验证记录（2026-11-04）

- 核心测试先在空实现下出现 12 个预期失败，完成实现后 13 项全部通过。
- Android 测试共 21 项通过；前台识别、悬浮窗和服务测试分别运行于 API 28、35，设置页测试运行于 API 28。
- 回归测试先复现旧错误提示不清除及 API 35 初始焦点问题，修复后通过。按键测试等待首次窗口布局后以 DPAD_CENTER 的按下/抬起事件验证确认。
- 独立只读代码审查完成，指出的问题已修复或在文档中明确限制。
- `adb devices -l` 未发现已连接设备，本机 SDK 没有模拟器镜像；未进行 REDMI 真机或模拟器安装验证。
- APK 使用项目本地调试证书签名，支持 v1/v2，包名 `com.jk.tvreminder`，Android API 23+，包含 Leanback 启动入口。
- 工作区原本没有 Git 仓库，全部文件直接在当前目录生成，没有初始化 Git、暂存或提交。
