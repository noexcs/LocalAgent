package com.noexcs.localagent.agent

import ai.koog.agents.chatMemory.feature.ChatMemory
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.message.Message.Role
import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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


class AgentViewModel(
    context: Context,
    private val memoryManager: MemoryManager,
    private val settingsManager: SettingsManager,
    private val fileChatHistoryProvider: FileChatHistoryProvider
) : ViewModel() {

    private val executor = TermuxExecutor(context.applicationContext)

    val messages = mutableStateListOf<MessageViewModel>()
    val isLoading = mutableStateOf(false)
    val error = mutableStateOf<String?>(null)

    private var sessionId: String = UUID.randomUUID().toString()
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
        TermuxExecuteCommandTool.init(executor)
        TermuxReadFileTool.init(executor)
        TermuxWriteFileTool.init(executor)
        UpdateMemoryTool.init(memoryManager)

        // Create an agent
        return AIAgent(
            // Create a prompt executor using the LLM client
            promptExecutor = MultiLLMPromptExecutor(deepSeekClient),
            // Provide a model
            llmModel = DeepSeekModels.DeepSeekChat,
            systemPrompt = systemPrompt,
            toolRegistry = ToolRegistry {
                tool(TermuxExecuteCommandTool)
                tool(TermuxReadFileTool)
                tool(TermuxWriteFileTool)
                tool(UpdateMemoryTool)
            },
        ) {
            install(ChatMemory) {
                chatHistoryProvider = fileChatHistoryProvider
                windowSize(20)
            }
        }
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
