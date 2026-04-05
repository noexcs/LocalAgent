package com.noexcs.localagent.agent

import ai.koog.agents.chatMemory.feature.ChatMemory
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.bedrock.BedrockLLMClient
import ai.koog.prompt.executor.clients.dashscope.DashscopeLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.mistralai.MistralAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message.Role
import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import aws.smithy.kotlin.runtime.identity.IdentityProvider
import com.noexcs.localagent.agent.tools.GetAppInfoTool
import com.noexcs.localagent.agent.tools.IntentTool
import com.noexcs.localagent.agent.tools.TermuxDialogTool
import com.noexcs.localagent.agent.tools.TermuxExecuteCommandTool
import com.noexcs.localagent.agent.tools.TermuxReadFileTool
import com.noexcs.localagent.agent.tools.TermuxWriteFileTool
import com.noexcs.localagent.agent.tools.UpdateMemoryTool
import com.noexcs.localagent.data.FileChatHistoryProvider
import com.noexcs.localagent.data.MemoryManager
import com.noexcs.localagent.data.MessageViewModel
import com.noexcs.localagent.data.SettingsManager
import com.noexcs.localagent.data.getModelById
import kotlinx.coroutines.launch
import java.util.UUID

class AgentViewModel(
    private val appContext: Context,
    private val memoryManager: MemoryManager,
    private val settingsManager: SettingsManager,
    private val fileChatHistoryProvider: FileChatHistoryProvider
) : ViewModel() {

    private val executor = TermuxExecutor(appContext.applicationContext)

    val messages = mutableStateListOf<MessageViewModel>()
    val isLoading = mutableStateOf(false)
    val error = mutableStateOf<String?>(null)

    // Callback to notify when conversation should be refreshed
    var onConversationUpdated: (() -> Unit)? = null

    // Track if the first assistant message has been received in this session
    private var hasNotifiedFirstResponse = false

    private var sessionId: String = UUID.randomUUID().toString()
    private var agent: AIAgent<String, String>? = null

    private suspend fun buildAgent(): AIAgent<String, String> {

        val memory = memoryManager.read()
        val systemPrompt = buildString {
            appendLine("You are a helpful Android assistant with Termux shell access.")
            appendLine("Use tools to execute commands, read/write files, and manage memory.")
            if (settingsManager.userSystemPrompt.isNotBlank()) {
                appendLine()
                appendLine("# User Custom Instruct")
                appendLine(settingsManager.userSystemPrompt)
            }
            if (memory.isNotBlank()) {
                appendLine()
                appendLine("# Memory")
                appendLine("<memory>")
                appendLine(memory)
                appendLine("</memory>")
            }
        }

        // Initialize tools
        TermuxExecuteCommandTool.init(executor)
        TermuxReadFileTool.init(executor)
        TermuxWriteFileTool.init(executor)
        UpdateMemoryTool.init(memoryManager)
        GetAppInfoTool.init(appContext.applicationContext)
        TermuxDialogTool.init(executor)
        IntentTool.init(appContext.applicationContext)

        // Create LLM client based on provider
        val apiKey = settingsManager.apiKey ?: ""
        val baseUrl = settingsManager.baseUrl?: "http://127.0.0.1:11434"
        val modelId = settingsManager.model!!


        val llmClient = when (settingsManager.providerType) {
            LLMProvider.DeepSeek -> DeepSeekLLMClient(apiKey)
            LLMProvider.Google -> GoogleLLMClient(apiKey = apiKey)
            LLMProvider.OpenAI -> OpenAILLMClient(apiKey = apiKey)
            LLMProvider.Ollama -> OllamaClient(baseUrl = baseUrl)
            LLMProvider.Anthropic -> AnthropicLLMClient(apiKey = apiKey)
            LLMProvider.Alibaba -> DashscopeLLMClient(apiKey = apiKey)
//            LLMProvider.Azure -> null
//            LLMProvider.Bedrock -> null
//            LLMProvider.HuggingFace -> null
//            LLMProvider.Meta -> null
//            LLMProvider.MiniMax -> null
            LLMProvider.MistralAI -> MistralAILLMClient(apiKey = apiKey)
//            LLMProvider.OCI -> null
            LLMProvider.OpenRouter -> OpenRouterLLMClient(apiKey = apiKey)
//            LLMProvider.Vertex -> null
//            LLMProvider.ZhipuAI -> null
            else -> throw IllegalArgumentException("Invalid LLM provider")
        }

        val model = getModelById(appContext, settingsManager.providerType!!, modelId)

        // Create an agent
        return AIAgent(
            promptExecutor = MultiLLMPromptExecutor(llmClient),
            llmModel = model,
            systemPrompt = systemPrompt,
            toolRegistry = ToolRegistry {
                tool(TermuxExecuteCommandTool)
                tool(TermuxReadFileTool)
                tool(TermuxWriteFileTool)
                tool(UpdateMemoryTool)
                tool(GetAppInfoTool)
                tool(TermuxDialogTool)
                tool(IntentTool)
            },
        ) {
            install(ChatMemory) {
                chatHistoryProvider = fileChatHistoryProvider
                windowSize(20)
            }
        }
    }

    fun checkSettings(): Boolean {
        return settingsManager.providerType == null || settingsManager.apiKey == null
    }

    fun sendMessage(userText: String) {
        if (userText.isBlank() || isLoading.value) return

        error.value = null
        messages.add(MessageViewModel(role = Role.User, content = userText))

        viewModelScope.launch {
            isLoading.value = true
            try {
                if (agent == null) {
                    agent = buildAgent()
                }

                val aIAgentRunSession = agent!!.createSession(sessionId)

                val response = aIAgentRunSession.run(userText)
//                val response = userText
                messages.add(MessageViewModel(role = Role.Assistant, content = response))

                // Notify only on the first assistant response in this session
                if (!hasNotifiedFirstResponse) {
                    onConversationUpdated?.invoke()
                    hasNotifiedFirstResponse = true
                }

            } catch (e: Exception) {
                error.value = e.message ?: "Unknown error"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun clearMessages() {
        messages.clear()
        agent = null
        sessionId = UUID.randomUUID().toString()
        hasNotifiedFirstResponse = false
    }

    fun newConversation() {
        clearMessages()
    }

    fun loadConversation(id: String) {
        val session = fileChatHistoryProvider._load(id)
        if (session != null) {
            messages.addAll(session.messages.map {
                MessageViewModel(it.role, it.content)
            })
            // If loading an existing conversation with messages, mark as already notified
            hasNotifiedFirstResponse = session.messages.any { it.role == Role.Assistant }
        } else {
            clearMessages()
        }

        sessionId = id
    }
}
