# AI备忘录 Android（持续更新中）

原生 Android 日程提醒与本地记账应用，支持 AI 识别日程和账目、系统提醒、收支统计与消费分析，如果你觉得不错，别忘了收藏。后面我会持续更新

## 下载最新版

**[直接下载 AI-Memo-Android v0.9.4 APK](https://github.com/yuyiren777/AI-Memo-Android/releases/download/v0.9.4/AI-Memo-Android-v0.9.4.apk)**

也可以进入 [Releases 页面](https://github.com/yuyiren777/AI-Memo-Android/releases/latest) 查看最新版本和安装包。

## 主要功能

- 智谱 GLM 与 DeepSeek 文本和图片识别，支持文本模型与视觉理解模型分开配置
- 默认模型：智谱 `GLM-4.7-Flash` / `GLM-4.6V-Flash`，DeepSeek `deepseek-v4-flash` / `deepseek-v4-flash-vision-exp`
- 从文字或最多 4 张图片识别日程，保存前可逐条检查和编辑
- 日程和记账图片识别均支持直接拍照，记账图片仍限制最多 4 张
- 手动新建、编辑、完成、恢复、删除和批量管理日程
- 日期、起止时间、地点、备注、紧急程度和重复安排
- 三阶段系统提醒，支持重启、系统时间和时区变化后恢复安排
- 提醒历史、未读红点和未读数量
- 本地加密备份与恢复，无需用户记忆备份密码
- 收入、支出流水，支持具体日期、时间、分类和备注
- 完整分类图标网格、自定义分类和分类删除
- AI 一次识别多条账目，并分别判断收支、金额、分类和备注
- 记账流水支持多选、全选和批量确认删除
- 记账图片识别入口，支持最多 4 张超市小票或购物清单，自动逐张识别并合并账目
- 小票识别会复核小字、数量、单价和行合计，避免把小计、总计或找零重复记账
- 月总结、年总结、按周期独立预算和预算进度
- 月度与年度收支折线图、收入与支出分类饼图
- 用户主动触发的流式 AI 消费分析
- 日间、柔和和夜间三种主题
- 可配置的截图与录屏保护

## 隐私说明

- 日程、账单和设置默认保存在用户手机本地。
- 本地敏感数据与备份已加密。
- AI 识别只发送用户本次选择的文字或图片。
- AI 消费分析只在用户主动点击时发送当前周期汇总。
- 安全相关实现不在公开源码中披露。

## 公开源码说明

本仓库公开界面、日程、提醒、记账、统计、图表和 AI 业务逻辑。安全相关模块仅保留接口级伪代码，真实实现未上传，也不包含在 Git 历史中。

带有 `.pseudocode` 后缀的文件用于说明模块职责和数据流，不提供可运行实现。因此，本仓库不能直接构建与 Release 页面完全相同的官方 APK；请从上方下载链接获取完整安装包。

## 技术结构

- Kotlin 2.1
- Jetpack Compose + Material 3
- SQLiteOpenHelper 本地数据库
- AlarmManager + BroadcastReceiver 系统提醒
- StateFlow + AndroidViewModel 状态管理
- 智谱与 DeepSeek OpenAI 兼容 HTTP 接口
- 最低 Android 8.0（API 26），目标 Android 15（API 35）

## 项目目录

```text
app/src/main/java/cn/aimemo/mobile/
├── ai/          AI 请求与识别结果解析
├── data/        日程、账目、统计与数据访问
├── reminder/    系统提醒与时间计算
└── ui/          Compose 页面和主题
```

当前公开版本：`v0.9.4`
