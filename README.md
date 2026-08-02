# AI备忘录 Android

这是与 Windows 桌面端完全分离的原生 Android 客户端，项目目录为
`D:\VS\AI-Memo-Android`。当前版本专注于可靠的本地日程和系统提醒，所有
日程默认只保存在手机本地。

## 当前功能

- 查看、手动添加、编辑、完成和删除日程
- 日期、时间、地点、备注和紧急程度
- 只有日期没有时间时，按当天中午 12:00 计算
- 未设置日期的待办，在每次打开应用时提醒一次
- 通过 AlarmManager 安排系统提醒
- 手机重启、系统时间或时区改变后重新安排提醒
- 夜间主题和可配置的提前提醒分钟数
- Android 13 及以上的通知权限申请
- 禁止 Android 云备份日程数据库和设置

## 技术结构

- Kotlin 2.1
- Jetpack Compose + Material 3
- SQLiteOpenHelper 本地数据库
- AlarmManager + BroadcastReceiver 系统提醒
- StateFlow + AndroidViewModel 状态管理
- 最低 Android 8.0（API 26），目标 Android 15（API 35）

## 构建

需要 JDK 17 和 Android SDK 35。首次构建会通过 Gradle Wrapper 下载依赖。

```powershell
powershell -ExecutionPolicy Bypass -File .\build_apk.ps1
```

构建产物：

```text
release\AI-Memo-Android-v0.1.0-debug.apk
```

Debug APK 用于当前开发和真机测试。正式分发前应创建受保护的发布签名，并
生成 release APK 或 AAB。

## 下一阶段

在当前本地日程和提醒层稳定后，再接入智谱文字识别与图片识别、多个提醒
阶段、日程导入导出和迁移工具。AI Key 只保存在手机本地，不写入源码。

