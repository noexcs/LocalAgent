# Koog Framework Integration Guide

## 概述

本项目已成功集成 JetBrains Koog AI Agent 框架,为你的 Android Agent 提供了更强大的能力。

## 已完成的集成

### 1. 依赖配置
- ✅ 添加 `ai.koog:koog-agents:0.7.3` 依赖
- ✅ 升级 JVM 目标到 Java 17 (Koog 要求)

### 2. Koog 工具封装
创建了以下文件:
- `TermuxTool.kt` - 将 Termux 命令执行封装为 Koog Tool
- `TermuxResultReceiver.kt` - 处理 Termux 命令结果的服务
- `KoogAgentAdapter.kt` - Koog Agent 适配器
- `LocalAgent.kt` - 简化的 Koog Agent 接口

### 3. 核心功能

#### TermuxTool
```kotlin
@Serializable
data class TermuxToolArgs(val command: String)

@Serializable
data class TermuxToolResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int
)
```

Koog Agent 可以通过 `execute_shell_command` 工具执行任何 shell 命令。

## 使用方式

### 方式 1: 使用 KoogAgentAdapter (推荐用于简单场景)

```kotlin
val koogAgent = KoogAgentAdapter(
    context = context,
    apiKey = "your-openai-api-key",
    baseUrl = "https://api.openai.com/v1",
    model = "gpt-4o"
)

// 发送消息
val response = koogAgent.chat("列出当前目录的文件")
```

### 方式 2: 使用 LocalAgent (更简洁)

```kotlin
val agent = LocalAgent(context, apiKey)
val response = agent.chat("检查系统信息")
```

### 方式 3: 继续使用现有实现

你的项目已有完整的 `AgentViewModel` 实现,可以继续使用。如果需要,可以在 ViewModel 中添加 Koog 模式切换。

## Koog 框架的优势

相比现有实现,Koog 提供:

1. **多平台支持** - JVM, JS, WasmJS, Android, iOS
2. **内置功能**:
   - 智能历史压缩 (优化 token 使用)
   - 故障恢复和重试机制
   - Agent 状态持久化
   - OpenTelemetry 可观测性
3. **LLM 切换** - 无缝切换不同 LLM 提供商
4. **图工作流** - 复杂的 Agent 行为编排
5. **MCP 集成** - Model Context Protocol 支持

## 下一步建议

### 选项 A: 在现有 UI 中添加 Koog 模式
在 `SettingsScreen` 中添加开关,让用户选择使用原生实现还是 Koog 框架。

### 选项 B: 创建独立的 Koog Agent 页面
添加新的 Screen 专门用于测试 Koog Agent 功能。

### 选项 C: 迁移到 Koog
逐步将现有工具迁移到 Koog Tool 接口,享受框架的完整功能。

## 示例场景

```kotlin
// Koog Agent 会自动调用 execute_shell_command 工具
agent.chat("帮我找出占用空间最大的 5 个文件")
// Agent 会执行: du -ah | sort -rh | head -5

agent.chat("创建一个名为 test.txt 的文件,内容是 Hello World")
// Agent 会执行: echo "Hello World" > test.txt

agent.chat("检查 Python 是否安装")
// Agent 会执行: python --version 或 which python
```

## 配置说明

### API Key 配置
在 `SettingsScreen` 中配置的 API Key 可以直接用于 Koog:

```kotlin
val apiKey = settingsManager.apiKey
val koogAgent = KoogAgentAdapter(context, apiKey)
```

### 自定义 System Prompt
```kotlin
val agent = AIAgent(
    promptExecutor = simpleOpenAIExecutor(apiKey),
    systemPrompt = "你的自定义提示词",
    llmModel = OpenAIModels.Chat.GPT4o,
    toolRegistry = ToolRegistry { tool(TermuxTool(context)) }
)
```

## 注意事项

1. **Java 版本**: Koog 需要 Java 17+,已在 build.gradle.kts 中配置
2. **权限**: 仍需要 Termux RUN_COMMAND 权限
3. **兼容性**: Koog 工具与现有 TermuxExecutor 独立,互不影响
4. **性能**: Koog 的历史压缩功能可以减少长对话的 token 消耗

## 故障排查

### 编译错误
如果遇到 Java 版本相关错误,确认:
```gradle
compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlinOptions {
    jvmTarget = "17"
}
```

### 运行时错误
确保 Termux 已正确配置:
```bash
pkg install termux-api
echo "allow-external-apps=true" >> ~/.termux/termux.properties
```

## 参考资源

- [Koog 官方文档](https://docs.koog.ai/)
- [Koog API 参考](https://api.koog.ai/)
- [Koog GitHub](https://github.com/JetBrains/koog)
