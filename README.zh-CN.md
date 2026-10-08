# Hush

[English](README.md)

[![test](https://github.com/lete114/Hush/actions/workflows/test.yml/badge.svg)](https://github.com/lete114/Hush/actions/workflows/test.yml)
![Android 7.0+ (API 24)](https://img.shields.io/badge/Android-7.0%2B%20API%2024-3DDC84?logo=android&logoColor=white)
![targetSdk 36](https://img.shields.io/badge/targetSdk-36-607D8B)
![Kotlin 2.2.21](https://img.shields.io/badge/Kotlin-2.2.21-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose BOM 2026.06.01](https://img.shields.io/badge/Jetpack%20Compose-BOM%202026.06.01-4285F4?logo=jetpackcompose&logoColor=white)
[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-1E88E5)](LICENSE)

倒计时自动锁屏 Android 应用 —— 设定时长，时间一到自动锁屏。

Hush 面向这样的时刻：你想让屏幕熄灭，却总是顾不上按电源键 —— 听着歌睡着、躺在床上看视频，或是把手机反扣在桌上专心做事。启动倒计时，放下手机，时间一到屏幕自动锁定，可选同时暂停正在播放的媒体。

倒计时期间，通知栏实时显示剩余时间，并附带**暂停 / 延长 / 取消**按钮，无需解锁就能调整。如果锁屏失败（比如设备管理员被关闭），Hush 会主动通知你，而不是静默失败。一切都在本机完成：无 `INTERNET` 权限、无账号、无统计、无遥测。

| 待机 | 时长选择 | 运行中 |
| --- | --- | --- |
| <img src="screenshots/idle.png" width="240" alt="待机主页"> | <img src="screenshots/duration-picker.png" width="240" alt="时长选择面板"> | <img src="screenshots/running.png" width="240" alt="倒计时运行中"> |

| 设置 | 通知栏 |
| --- | --- |
| <img src="screenshots/settings.png" width="240" alt="设置页"> | <img src="screenshots/notification.png" width="240" alt="带控制按钮的倒计时通知"> |

## 功能

- **自动锁屏**：倒计时归零即锁屏（设备管理员 `force-lock`，仅声明单一最小策略）
- **时长预设**：关闭 / 15 / 30 / 60 分钟 / 自定义（≥ 60 秒）
- **通知栏倒计时**：实时显示剩余时间，支持暂停 / 延长 / 取消
- **锁屏时暂停媒体**（可选）：锁屏瞬间暂停正在播放的音频和视频
- **锁屏失败提醒**：倒计时结束但因设备管理员被关闭而未能锁屏时发出通知
- **电池白名单 / 精确闹钟**：设置页一键直达，保证准时触发
- **双语界面**：简体中文 / English

## 隐私

Hush **完全离线，不收集任何信息**：

- 未声明 `INTERNET` 权限 —— 无账号、无统计、无广告、无遥测
- 所有数据（时长偏好、语言）仅保存在本机（SharedPreferences）
- 锁屏仅通过设备管理员 `force-lock` 实现，不读取、修改或删除你的任何数据

## 权限

| 权限 | 用途 |
| --- | --- |
| 设备管理员（`force-lock`） | 倒计时结束时锁屏 |
| 通知 `POST_NOTIFICATIONS` | 显示倒计时及其控制按钮 |
| 精确闹钟 `SCHEDULE_EXACT_ALARM` | 保证准时锁屏 |
| 忽略电池优化 | 防止系统在后台终止倒计时 |
| 前台服务（`specialUse`） | 倒计时期间保持服务存活 |

## 构建

需要 **JDK 21**（JDK 25 会在 Kotlin 2.2.21 编译层崩溃）和 Android SDK 36。

```bash
export JAVA_HOME='<path to your JDK 21>'

./gradlew test           # 单元测试（唯一质量闸门）
./gradlew assembleDebug  # 构建 debug APK
```

Windows（PowerShell）下改用 `gradlew.bat`，环境变量仅对当前会话生效：

```powershell
$env:JAVA_HOME='<path to your JDK 21>'
.\gradlew.bat test
.\gradlew.bat assembleDebug
```

## 许可证

[GPL-3.0](LICENSE)

应用内图标来自 Google Material Symbols（[Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0)）。
