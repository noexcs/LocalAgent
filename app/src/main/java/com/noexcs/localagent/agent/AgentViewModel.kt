package com.noexcs.localagent.agent

import ai.koog.agents.chatMemory.feature.ChatMemory
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.message.Message.Role
import ai.koog.prompt.structure.markdown.markdownStreamingParser
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.log
import ai.koog.agents.ext.agent.chatAgentStrategy
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.invoke
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.structure.markdown.MarkdownStructureDefinition
import ai.koog.agents.ext.agent.reActStrategy
import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.bedrock.BedrockClientSettings
import ai.koog.prompt.executor.clients.bedrock.BedrockLLMClient
import ai.koog.prompt.executor.clients.dashscope.DashscopeClientSettings
import ai.koog.prompt.executor.clients.dashscope.DashscopeLLMClient
import ai.koog.prompt.executor.clients.dashscope.DashscopeModels
import ai.koog.prompt.executor.clients.google.GoogleClientSettings
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.mistralai.MistralAIClientSettings
import ai.koog.prompt.executor.clients.mistralai.MistralAILLMClient
import ai.koog.prompt.executor.clients.mistralai.MistralAIModels
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.openrouter.OpenRouterClientSettings
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.prompt.llm.OpenRouterLLMProvider
import kotlin.time.Clock
import  ai.koog.prompt.llm.LLModel
import android.util.Log

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

    private var sessionId: String = UUID.randomUUID().toString()
    private var agent: AIAgent<String, String>? = null

    private fun buildAgent(): AIAgent<String, String> {

        Log.d("AgentViewModel", "userSystemPrompt: ${settingsManager.userSystemPrompt}")

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


        Log.d("AgentViewModel", "systemPrompt: $systemPrompt")

        // Initialize tools
        TermuxExecuteCommandTool.init(executor)
        TermuxReadFileTool.init(executor)
        TermuxWriteFileTool.init(executor)
        UpdateMemoryTool.init(memoryManager)
        GetAppInfoTool.init(appContext.applicationContext)
        TermuxDialogTool.init(executor)
        IntentTool.init(appContext.applicationContext)

        // Create LLM client based on provider
        val llmClient = when (settingsManager.providerType) {
            "DeepSeek" -> DeepSeekLLMClient(settingsManager.apiKey)
            "Anthropic" -> AnthropicLLMClient(
                apiKey = settingsManager.apiKey,
                settings = AnthropicClientSettings()
            )
            "OpenAI" -> OpenAILLMClient(
                apiKey = settingsManager.apiKey,
                settings = OpenAIClientSettings()
            )
            "Google" -> GoogleLLMClient(
                apiKey = settingsManager.apiKey,
                settings = GoogleClientSettings()
            )
            "Dashscope" -> DashscopeLLMClient(
                apiKey = settingsManager.apiKey,
                settings = DashscopeClientSettings()
            )
            "Mistral AI" -> MistralAILLMClient(
                apiKey = settingsManager.apiKey,
                settings = MistralAIClientSettings()
            )
            "Ollama" -> OllamaClient(baseUrl = settingsManager.baseUrl)
            "OpenRouter" -> OpenRouterLLMClient(
                apiKey = settingsManager.apiKey,
                settings = OpenRouterClientSettings()
            )
            else -> DeepSeekLLMClient(settingsManager.apiKey)
        }

        // Select the appropriate model based on provider and user selection
        val llmModel = getLLModel(settingsManager.providerType, settingsManager.model)


        // Create an agent
        return AIAgent(
            promptExecutor = MultiLLMPromptExecutor(llmClient),
            llmModel = llmModel,
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

    private fun getLLModel(provider: String, modelId: String): ai.koog.prompt.llm.LLModel {
        return when (provider) {
            "DeepSeek" -> getModelFromListOrFirst(DeepSeekModels.models, modelId)
            "Anthropic" -> getModelFromListOrFirst(AnthropicModels.models, modelId)
            "OpenAI" -> getModelFromListOrFirst(OpenAIModels.models, modelId)
            "Google" -> getModelFromListOrFirst(GoogleModels.models, modelId)
            "Dashscope" -> getModelFromListOrFirst(DashscopeModels.models, modelId)
            "Mistral AI" -> getModelFromListOrFirst(MistralAIModels.models, modelId)
            "Ollama" -> getModelFromListOrFirst(OllamaModels.models, modelId)
            "OpenRouter" -> getModelFromListOrFirst(OpenRouterModels.models, modelId)
            else -> DeepSeekModels.DeepSeekChat
        }
    }

    private fun getModelFromListOrFirst(
        models: List<LLModel>,
        modelId: String
    ): LLModel {
        // Try to find exact match
        return models.find { it.id == modelId }
            // Try case-insensitive match
            ?: models.find { it.id.equals(modelId, ignoreCase = true) }
            // Fallback to first available model
            ?: models.firstOrNull()
            // Ultimate fallback (shouldn't happen)
            ?: throw IllegalStateException("No models available for provider")
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
        } else {
            clearMessages()
        }

        sessionId = id
    }
}
