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
            handleEvents {
//                onToolCallStarting { eventContext ->
//                    messages.add(MessageViewModel(role = Role.Tool, content = "Tool call starting: ${eventContext.toolName}\nArgs: ${eventContext.toolArgs.toString()}"))
//                }
//                onToolCallFailed { eventContext ->
//                    messages.add(MessageViewModel(role = Role.Tool, content = "Tool call failed: ${eventContext.toolName}"))
//                }
//                onToolCallCompleted { eventContext ->
//                    messages.add(MessageViewModel(role = Role.Tool, content = "Tool call completed: ${eventContext.toolName}\nResult: ${eventContext.toolResult}"))
//                }

                // Agent lifecycle
//                onAgentStarting { eventContext ->
//                    messages.add(MessageViewModel(role = Role.Tool, content = "Agent starting: ${eventContext.runId}"))
//
//                }
//                onAgentCompleted { eventContext ->
//                    messages.add(MessageViewModel(role = Role.Tool, content = "Agent completed: ${eventContext.agentId}"))
//                }
//                onAgentClosing { eventContext ->
//                    messages.add(MessageViewModel(role = Role.Tool, content = "Agent closing: ${eventContext.agentId}"))
//                }
//                onAgentExecutionFailed { eventContext ->
//                    messages.add(MessageViewModel(role = Role.Tool, content = "Agent execution failed: ${eventContext.agentId}"))
//                }

                // LLM streaming
//                onLLMStreamingStarting { eventContext ->
//                    messages.add(MessageViewModel(role = Role.Tool, content = "LLM streaming starting: ${eventContext.prompt}"))
//                }
//                onLLMStreamingCompleted { eventContext ->
//                    messages.add(MessageViewModel(role = Role.Tool, content = "LLM streaming completed: ${eventContext.prompt}"))
//                }
//                onLLMStreamingFailed { eventContext ->
//                    messages.add(MessageViewModel(role = Role.Tool, content = "LLM streaming failed: ${eventContext.prompt}"))
//                }
//                onLLMStreamingFrameReceived { eventContext ->
//                    when (val frame = eventContext.streamFrame) {
//                        is StreamFrame.TextDelta -> {
//                            messages.last().content += frame.text
//                        }
//                        is StreamFrame.TextComplete -> {
//                            messages.last().content += frame.text
//                        }
//                        is StreamFrame.ReasoningComplete -> {
//                            messages.last().content += frame.text
//                        }
//                        is StreamFrame.ToolCallComplete -> {
//                            messages.last().content += frame.content
//                        }
//                        is StreamFrame.ReasoningDelta -> {
//                            messages.last().content += frame.text
//                        }
//                        is StreamFrame.ToolCallDelta -> {
//                            messages.last().content += frame.content
//                        }
//                        is StreamFrame.End -> {
//                            messages.last().content += frame.metaInfo
//                        }
//                    }
//                }

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
