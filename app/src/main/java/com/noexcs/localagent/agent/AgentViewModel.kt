package com.noexcs.localagent.agent

import ai.koog.agents.AIAgent
import ai.koog.agents.tools.ToolRegistry
import ai.koog.prompt.executors.simpleOpenAIExecutor
import ai.koog.prompt.models.OpenAIModels
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
import java.util.UUID

data class Message(val role: String, val content: String)

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
    private var agent: AIAgent? = null

    private fun buildAgent(): AIAgent {
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

        return AIAgent(
            promptExecutor = simpleOpenAIExecutor(
                apiKey = settingsManager.apiKey,
                baseUrl = settingsManager.baseUrl
            ),
            systemPrompt = systemPrompt,
            llmModel = when {
                settingsManager.model.contains("gpt-4o") -> OpenAIModels.Chat.GPT4o
                settingsManager.model.contains("gpt-4") -> OpenAIModels.Chat.GPT4Turbo
                else -> OpenAIModels.Chat.GPT35Turbo
            },
            toolRegistry = ToolRegistry {
                tool(KoogExecuteCommandTool(executor))
                tool(KoogReadFileTool(executor))
                tool(KoogWriteFileTool(executor))
                tool(KoogListDirectoryTool(executor))
                tool(KoogSearchFilesTool(executor))
                tool(KoogUpdateMemoryTool(memoryManager))
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
        viewModelScope.launch {
            conversationRepository.getConversation(id)?.let { conv ->
                clearMessages()
                currentConversationId = conv.id
                // Load messages from conversation
            }
        }
    }
}
