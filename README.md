# MyClaw · 手机里的 AI 助手

> 让 LLM 直接操作你的 Android 手机：在微信 / QQ / 钉钉 / 飞书 / Discord / Telegram 里发一句话，它自己点、按、输入、截图、翻页，直到任务做完再回你一句结果。
> *Turn your phone into an AI-operated device: send a message on WeChat / QQ / DingTalk / Feishu / Discord / Telegram and the agent taps, types, swipes and reads the screen until the task is done.*

<img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Android-7F52FF?logo=kotlin&logoColor=white">
<img alt="LangChain4j" src="https://img.shields.io/badge/Agent-LangChain4j-8A2BE2?logo=java&logoColor=white">
<img alt="Android" src="https://img.shields.io/badge/Android-API%2028%2B-3DDC84?logo=android&logoColor=white">
<img alt="License" src="https://img.shields.io/badge/License-Apache%202.0-D22128?logo=apache&logoColor=white">

---

## 这是什么

一个装在手机上的 Agent。它不只会聊天，而是**替人操作设备**：你说"把微信第三条未读转给同事并回复已收到"，它在无障碍服务上真的点开微信、滚动列表、点进会话、输入文字、发送，然后把结果发回原聊天窗口。

它的骨架来自开源项目 [ApkClaw](https://github.com/apkclaw-team/ApkClaw)（Apache-2.0），**本仓库是在其之上做的二次开发**，我负责的部分见下方「我做了什么」。

## 截图

<p align="center">
  <img src="Screenshots/Home.jpg" width="300" alt="首页 - 权限管理" />
  <img src="Screenshots/Setting.jpg" width="300" alt="设置 - LLM 与渠道配置" />
</p>

---

## 🛠 我做了什么

本仓库相对上游基线共 **84 个文件、+5816 / −741 行**的改动，集中在 5 个方向：

### 1. 自定义 LLM Provider 与思考深度控制

上游只有 OpenAI / Anthropic 两套客户端。我把它改成**可插拔的自定义 Provider**，并加了一整套推理策略层：

- `ReasoningPolicy` —— 按模型名识别模型家族（MiniMax / OpenAI / Anthropic…），为不同厂商生成各自的**思考预算规划**（`effort` = `min` / `medium` / `max`）
- `thinkingEnabled` + `reasoningEffort` 两级开关，落到 `AgentConfig`（`maxIterations` 默认 500、`maxTokens` 8192）
- `CURRENT_APP` 注入 —— 解决 Agent **把自己当成当前应用**的身份幻觉

### 2. 实时日志系统

排查问题的时候，"App 没反应"是最没用的信息。所以我做了一套完整的日志链路：

| 组件 | 作用 |
|---|---|
| `GlobalLogInterceptor` | 挂在**所有** HTTP 客户端上（QQ / 飞书 / Discord / Telegram / 微信），统一记录 URL、方法、状态码、耗时 |
| `LogRecorder` | 落盘为结构化日志文件，可暂停 / 恢复 |
| `LogViewerActivity` | App 内日志查看器，按文件分组展示 |

### 3. 配置服务扩展

上游的配置服务器只有 `/api/channels` 和 `/api/llm` 两个接口。我扩到 **8 个业务端点 + 4 个调试端点**：

```
/api/channels              /api/llm
/api/agent/max-iterations   /api/agent/max-tokens
/api/agent/thinking         /api/debug/tools
/api/debug/execute          /api/debug/screen-full
```

`/api/debug/execute` 可以直接在浏览器里执行任意工具并看结果 —— 调试 Agent 行为不用点手机。

### 4. 新增工具能力

| 工具 | 说明 |
|---|---|
| `ClipboardTool` | 读 / 写剪贴板（含独立的 `ClipboardReaderActivity` 兜底） |
| `DumpFullUiTool` | 导出**完整** UI 树（上游只能拿到可见节点） |
| `SystemKeyTool` | 系统按键注入 |
| `ScrollToFindTool` | 滚动查找目标元素 —— 滑动列表里找东西，Agent 自己就能翻页 |

### 5. 微信官方渠道 + 工程加固

- **微信渠道完整接入**（7 个文件）：`WeChatChannelHandler` / `WeChatCdn` / `WeChatMarkdown` / `WeChatInbound` / `WeChatSender`…
- **QQ 重连退避 + DNS 预检**（重连失败时先查 DNS，而不是盲目重试）
- **密钥编译期注入**：`local.properties` → `BuildConfig`，源码里**不再有任何密钥常量**
- **修好 Gradle 编译**（这个项目从 2026-05 起就编不过了，一直坏着）

---

## 🏗 架构

```
┌───────────────────────────────────────────────────────────────┐
│                      消息渠道                                  │
│  微信  │  QQ  │  钉钉  │  飞书  │  Discord  │  Telegram         │
└──────────────────────┬────────────────────────────────────────┘
                       │ 收到消息
                       ▼
              ┌─────────────────┐
              │  ChannelManager  │  消息路由与分发
              └────────┬────────┘
                       ▼
              ┌──────────────────────────┐
              │      TaskOrchestrator     │  任务锁、生命周期
              └────────┬─────────────────┘
                       ▼
              ┌──────────────────────────┐
              │  AgentService（Agent 循环）│
              │                          │
              │  ┌────────────┐          │
              │  │  LLM 调用  │◄─ LangChain4j（OpenAI / Anthropic / 自定义）│
              │  └─────┬──────┘          │
              │  ┌─────▼──────┐          │
              │  │  工具执行   │◄─ ToolRegistry → ClawAccessibilityService│
              │  └─────┬──────┘          │
              │   循环直到任务完成          │
              └────────┬─────────────────┘
                       ▼
              通过原渠道回复用户
```

### Agent 循环协议

遵循 **观察 → 思考 → 行动 → 验证**：

- **系统提示词**注入设备上下文（品牌 / 型号 / Android 版本 / 分辨率）+ 已注册工具清单 + 安全约束
- **LLM 重试**最多 3 次，指数退避 1s → 2s → 4s；遇到 401 / 403 **不重试**（重试没意义，只会浪费额度）
- **死循环检测**：维护 4 轮滑动窗口 `(screenHash, toolCall)` 指纹，全同则注入系统消息强制换路
- **Token 优化**：历史 `get_screen_info` 结果替换为占位符，只保留最近一次
- **系统弹窗处理**：`getRootInActiveWindow()` 返回 null（受保护弹窗）时截图发给用户并终止任务，而不是死磕

### 工具系统

工具按设备类型在 `ToolRegistry` 注册，每个工具继承 `BaseTool`，提供**中英双语描述**与类型化参数声明。

**通用工具**：`get_screen_info` / `find_node_info` / `dump_full_ui` / `take_screenshot` / `input_text` / `open_app` / `get_installed_apps` / `press_back` / `press_home` / `open_recent_apps` / `expand_notifications` / `collapse_notifications` / `lock_screen` / `system_key` / `clipboard` / `wait` / `repeat_actions` / `send_file` / `finish`

**手机专属**：`tap` / `long_press` / `swipe` / `click_by_text` / `click_by_id` / `scroll_to_find` / `search_app_in_store`

### 无障碍服务

`ClawAccessibilityService`（Java）是设备交互核心层：

- **手势**：`dispatchGesture()` 实现点击 / 滑动 / 长按
- **节点遍历**：`getRootInActiveWindow()` 获取 UI 层级
- **按键注入**：`performGlobalAction()` 实现 Home / 返回 / 最近任务
- **截屏**：`takeScreenshot()`（需 Android 11+）

> **已知限制**：系统保护窗口（如 `com.android.permissioncontroller` 的权限弹窗）会同时阻断节点树读取和手势注入（`filterTouchesWhenObscured` 机制）。Agent 检测到后截图通知用户手动处理 —— 这是系统级限制，无法在应用层绕过。

### 局域网配置服务器

基于 NanoHTTPD 的 HTTP 服务，端口 **9527**，方便用 PC 浏览器配置手机：

| 端点 | 方法 | 用途 |
|---|---|---|
| `/` | GET | 配置页面 |
| `/api/channels` | GET/POST | 渠道凭证 |
| `/api/llm` | GET/POST | LLM 配置 |
| `/api/agent/max-iterations` | GET/POST | 最大迭代轮数 |
| `/api/agent/max-tokens` | GET/POST | 单次最大 token |
| `/api/agent/thinking` | GET/POST | 思考开关 + 强度 |
| `/api/debug/tools` | GET | 列出已注册工具（Debug） |
| `/api/debug/execute` | POST | 直接执行工具（Debug） |
| `/api/debug/screen-full` | GET | 完整 UI 树（Debug） |

GET 返回的密钥做**脱敏**（仅显示末 4 位）。Debug 构建额外提供 `/debug.html` 工具调试控制台。

---

## 🚀 构建与运行

### 环境要求

- Java 17+
- Android Studio（Ladybug 或更高）
- Android SDK 36（compile / target），最低 SDK 28
- Android 设备（Android 9+）

### 构建

```bash
git clone https://github.com/soyoung5565/ApkClaw.git
cd ApkClaw

# Debug
./gradlew assembleDebug

# Release
./gradlew assembleRelease
```

### 密钥配置

密钥**不写进源码**，走编译期注入：

```properties
# local.properties（不入库，仓库内有 local.properties.example 模板）
YOUR_LLM_API_KEY=your_key_here
YOUR_QQ_APP_ID=your_app_id
YOUR_QQ_APP_SECRET=your_app_secret
```

编译时读入 `BuildConfig`。也可以直接在 App 内设置页填写，或用局域网配置页。

### 使用步骤

1. **安装** APK 到设备
2. **授权** —— 在首页依次开启：无障碍服务、通知权限、悬浮窗、电池白名单、文件访问
3. **配置 LLM** —— 设置 > LLM Config，填 API Key / Base URL / Model Name
4. **配置渠道** —— 至少配一个（微信 / QQ / 钉钉 / 飞书 / Discord / Telegram）
5. **发消息** —— 从配置好的渠道发一句自然语言指令，Agent 开始干活

> **提示**：也可以用 PC 浏览器连 `http://<设备IP>:9527` 配，不用在手机上打字。

---

## 🔧 主要依赖

**AI / Agent**

| 依赖 | 版本 | 用途 |
|---|---|---|
| [LangChain4j](https://github.com/langchain4j/langchain4j) | 1.12.2 | Agent 编排、工具定义、LLM 集成 |

**消息渠道**

| 依赖 | 版本 | 用途 |
|---|---|---|
| [DingTalk Stream SDK](https://github.com/open-dingtalk/dingtalk-stream-sdk-java) | 1.3.12 | 钉钉渠道 |
| [Feishu OAPI SDK](https://github.com/larksuite/oapi-sdk-java) | 2.5.3 | 飞书 / Lark 渠道 |

**网络 / 存储 / UI**

| 依赖 | 版本 | 用途 |
|---|---|---|
| [OkHttp](https://github.com/square/okhttp) | 4.12.0 | HTTP 客户端（LLM 调用） |
| [Retrofit](https://github.com/square/retrofit) | 2.11.1 | REST API 客户端 |
| [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd) | 2.3.1 | 局域网配置 & 调试服务器 |
| [MMKV](https://github.com/Tencent/MMKV) | 2.3.0 | 高性能本地 KV 存储 |
| [Gson](https://github.com/google/gson) | 2.13.2 | JSON 序列化 |
| [ZXing](https://github.com/zxing/zxing) | 3.5.3 | 二维码生成 |
| [Glide](https://github.com/bumptech/glide) | 5.0.5 | 图片加载 |
| [EasyFloat](https://github.com/princekin-f/EasyFloat) | 2.0.4 | 悬浮窗 |
| [UtilCode](https://github.com/Blankj/AndroidUtilCode) | 1.31.1 | Android 工具函数 |

---

## 📄 License

[Apache License 2.0](LICENSE) —— 与上游 [apkclaw-team/ApkClaw](https://github.com/apkclaw-team/ApkClaw) 保持一致。

**致谢**：本项目基于 [apkclaw-team/ApkClaw](https://github.com/apkclaw-team/ApkClaw) 开发，Thanks。
