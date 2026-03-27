package com.noexcs.localagent.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.tool.AskUser
import ai.koog.agents.ext.tool.ExitTool
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.ext.tool.file.ListDirectoryTool
import ai.koog.agents.ext.tool.file.ReadFileTool
import ai.koog.agents.ext.tool.file.WriteFileTool
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.rag.base.files.JVMFileSystemProvider
import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noexcs.localagent.agent.koog.*
import com.noexcs.localagent.data.ConversationRepository
import com.noexcs.localagent.data.MemoryManager
import com.noexcs.localagent.data.SettingsManager
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.util.UUID

data class Message(val role: String, val content: String)

@Serializable
data class SerializableMessage(val role: String, val content: String)

class AgentViewModel(
    context: Context,
    private val memoryManager: MemoryManager,
    private val settingsManager: SettingsManager,
    private val conversationRepository: ConversationRepository
) : ViewModel() {

    private val executor = TermuxExecutor(context.applicationContext)

    val messages = mutableStateListOf<Message>()
    val isLoading = mutableStateOf(false)
    val error = mutableStateOf<String?>(null)

    private var currentConversationId: String = UUID.randomUUID().toString()
    private var agent: AIAgent<String, String>? = null

    private fun buildAgent(): AIAgent<String, String> {
        val memory = memoryManager.read()
        val systemPrompt = buildString {
            if (settingsManager.userSystemPrompt.isNotBlank()) {
                appendLine(settingsManager.userSystemPrompt)
                appendLine()
            }
            appendLine("You are a helpful Android assistant with Termux shell access.")
            appendLine("Use tools to execute commands, read/write files, and manage memory.")
            if (memory.isNotBlank()) {
                appendLine()
                appendLine("<memory>")
                appendLine(memory)
                appendLine("</memory>")
            }
        }

        val deepSeekClient = DeepSeekLLMClient(settingsManager.apiKey)

        // Initialize tools
        KoogExecuteCommandTool.init(executor)
        KoogReadFileTool.init(executor)
        KoogWriteFileTool.init(executor)
        KoogUpdateMemoryTool.init(memoryManager)

        // Create an agent
        return AIAgent(
            // Create a prompt executor using the LLM client
            promptExecutor = MultiLLMPromptExecutor(deepSeekClient),
            // Provide a model
            llmModel = DeepSeekModels.DeepSeekChat,
            systemPrompt = systemPrompt,
            toolRegistry = ToolRegistry {
                tool(KoogExecuteCommandTool)
                tool(KoogReadFileTool)
                tool(KoogWriteFileTool)
                tool(KoogUpdateMemoryTool)
            }
        )
    }

    fun sendMessage(userText: String) {
        if (userText.isBlank() || isLoading.value) return

        error.value = null
        messages.add(Message(role = "user", content = userText))

        viewModelScope.launch {
            isLoading.value = true
            try {
                if (agent == null) {
                    agent = buildAgent()
                }

                val response = agent!!.run(userText)
                messages.add(Message(role = "assistant", content = response))

                saveConversation()
            } catch (e: Exception) {
                error.value = e.message ?: "Unknown error"
            } finally {
                isLoading.value = false
            }
        }
    }

    private suspend fun saveConversation() {
        // Save conversation logic
    }

    fun clearMessages() {
        messages.clear()
        agent = null
        currentConversationId = UUID.randomUUID().toString()
    }

    fun newConversation() {
        clearMessages()
    }

    fun loadConversation(id: String) {
        clearMessages()
        currentConversationId = id
    }
}
