package com.noexcs.localagent.agent

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noexcs.localagent.agent.tools.*
import com.noexcs.localagent.api.ChatMessage
import com.noexcs.localagent.api.FunctionCall
import com.noexcs.localagent.api.OpenAiClient
import com.noexcs.localagent.api.ToolCall
import com.noexcs.localagent.data.Conversation
import com.noexcs.localagent.data.ConversationRepository
import com.noexcs.localagent.data.MemoryManager
import com.noexcs.localagent.data.SettingsManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.util.UUID

/** Represents a tool call waiting for user approval. */
data class PendingToolApproval(
    val toolName: String,
    val arguments: JsonObject,
    val displaySummary: String
)

class AgentViewModel(
    context: Context,
    private val memoryManager: MemoryManager,
    private val settingsManager: SettingsManager,
    private val conversationRepository: ConversationRepository
) : ViewModel() {

    private val toolRegistry = ToolRegistry()
    private val executor = TermuxExecutor(context.applicationContext)
    private val json = Json { ignoreUnknownKeys = true }

    val messages = mutableStateListOf<ChatMessage>()
    val isLoading = mutableStateOf(false)
    val error = mutableStateOf<String?>(null)

    /** Non-null when waiting for user to approve/deny a tool call. */
    val pendingApproval = mutableStateOf<PendingToolApproval?>(null)
    private var approvalDeferred: CompletableDeferred<Boolean>? = null

    private var currentConversationId: String = UUID.randomUUID().toString()
    private var conversationCreatedAt: Long = System.currentTimeMillis()

    init {
        toolRegistry.register(ExecuteCommandTool(executor))
        toolRegistry.register(ReadFileTool(executor))
        toolRegistry.register(WriteFileTool(executor))
        toolRegistry.register(ListDirectoryTool(executor))
        toolRegistry.register(SearchFilesTool(executor))
        toolRegistry.register(UpdateMemoryTool(memoryManager))
    }

    private fun buildClient(): OpenAiClient {
        return OpenAiClient(
            baseUrl = settingsManager.baseUrl,
            apiKey = settingsManager.apiKey,
            model = settingsManager.model
        )
    }

    private fun buildSystemPrompt(): ChatMessage {
        val parts = mutableListOf<String>()

        val userPrompt = settingsManager.userSystemPrompt
        if (userPrompt.isNotBlank()) {
            parts.add(userPrompt)
        }

        parts.add(
            "You are a helpful assistant running on an Android device with access to a Termux terminal. " +
                    "You can execute shell commands, read/write files, list directories, and search files. " +
                    "You also have a persistent memory that carries across conversations — use the update_memory tool to save important information. " +
                    "Use the available tools to help the user with their tasks. " +
                    "Be concise in your responses."
        )

        val memory = memoryManager.read()
        if (memory.isNotBlank()) {
            parts.add("<memory>\n$memory\n</memory>")
        }

        return ChatMessage(role = "system", content = parts.joinToString("\n\n"))
    }

    fun sendMessage(userText: String) {
        if (userText.isBlank() || isLoading.value) return

        error.value = null
        messages.add(ChatMessage(role = "user", content = userText))
        runAgentLoop()
    }

    /** Called by UI when user approves the pending tool call. */
    fun approveToolCall() {
        approvalDeferred?.complete(true)
        approvalDeferred = null
        pendingApproval.value = null
    }

    /** Called by UI when user denies the pending tool call. */
    fun denyToolCall() {
        approvalDeferred?.complete(false)
        approvalDeferred = null
        pendingApproval.value = null
    }

    private suspend fun requestApproval(toolName: String, args: JsonObject, summary: String): Boolean {
        return withContext(Dispatchers.Main) {
            val deferred = CompletableDeferred<Boolean>()
            approvalDeferred = deferred
            pendingApproval.value = PendingToolApproval(toolName, args, summary)
            deferred.await()
        }
    }

    private fun needsConfirmation(tool: Tool): Boolean {
        if (!tool.requiresConfirmation) return false
        return settingsManager.toolConfirmMode == "ask"
    }

    private fun buildApprovalSummary(toolName: String, args: JsonObject): String {
        return when (toolName) {
            "execute_command" -> args["command"]?.toString()?.trim('"') ?: toolName
            "write_file" -> "write_file → ${args["path"]?.toString()?.trim('"') ?: "?"}"
            else -> toolName
        }
    }

    private fun runAgentLoop() {
        viewModelScope.launch {
            isLoading.value = true
            try {
                val client = buildClient()
                val maxIterations = 15
                var iterations = 0

                while (iterations < maxIterations) {
                    iterations++

                    val systemPrompt = buildSystemPrompt()
                    val allMessages = listOf(systemPrompt) + messages.toList()

                    val contentBuilder = StringBuilder()
                    val toolCallMap = mutableMapOf<Int, Triple<String, StringBuilder, StringBuilder>>()

                    val placeholderIndex = messages.size
                    messages.add(ChatMessage(role = "assistant", content = ""))

                    client.chatStream(allMessages, toolRegistry.toToolDefinitions())
                        .flowOn(Dispatchers.IO)
                        .collect { chunk ->
                            val delta = chunk.choices.firstOrNull()?.delta ?: return@collect

                            delta.content?.let { text ->
                                contentBuilder.append(text)
                                messages[placeholderIndex] = ChatMessage(
                                    role = "assistant",
                                    content = contentBuilder.toString(),
                                    toolCalls = buildToolCalls(toolCallMap)
                                )
                            }

                            delta.toolCalls?.forEach { tc ->
                                val idx = tc.index
                                if (idx !in toolCallMap) {
                                    // First chunk for this tool call — initialize
                                    toolCallMap[idx] = Triple(
                                        tc.id ?: "",
                                        StringBuilder(tc.function?.name ?: ""),
                                        StringBuilder(tc.function?.arguments ?: "")
                                    )
                                } else {
                                    // Subsequent chunks — append deltas
                                    val existing = toolCallMap[idx]!!
                                    tc.id?.let { id ->
                                        if (id.isNotEmpty() && existing.first.isEmpty()) {
                                            toolCallMap[idx] = existing.copy(first = id)
                                        }
                                    }
                                    tc.function?.name?.let { name ->
                                        if (name.isNotEmpty()) existing.second.append(name)
                                    }
                                    tc.function?.arguments?.let { args ->
                                        existing.third.append(args)
                                    }
                                }

                                messages[placeholderIndex] = ChatMessage(
                                    role = "assistant",
                                    content = contentBuilder.toString().ifEmpty { null },
                                    toolCalls = buildToolCalls(toolCallMap)
                                )
                            }
                        }

                    val finalToolCalls = buildToolCalls(toolCallMap)
                    val finalContent = contentBuilder.toString().ifEmpty { null }
                    messages[placeholderIndex] = ChatMessage(
                        role = "assistant",
                        content = finalContent,
                        toolCalls = finalToolCalls
                    )

                    if (finalToolCalls.isNullOrEmpty()) break

                    // Execute tool calls (with optional confirmation)
                    for (toolCall in finalToolCalls) {
                        val tool = toolRegistry.getTool(toolCall.function.name)
                        val result = if (tool != null) {
                            try {
                                val args = json.decodeFromString<JsonObject>(toolCall.function.arguments)

                                if (needsConfirmation(tool)) {
                                    val summary = buildApprovalSummary(toolCall.function.name, args)
                                    val approved = requestApproval(toolCall.function.name, args, summary)
                                    if (!approved) {
                                        "Tool call denied by user."
                                    } else {
                                        tool.execute(args)
                                    }
                                } else {
                                    tool.execute(args)
                                }
                            } catch (e: Exception) {
                                "Error executing tool: ${e.message}"
                            }
                        } else {
                            "Unknown tool: ${toolCall.function.name}"
                        }

                        messages.add(
                            ChatMessage(
                                role = "tool",
                                content = result,
                                toolCallId = toolCall.id
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                error.value = e.message ?: "Unknown error"
            } finally {
                isLoading.value = false
                pendingApproval.value = null
                approvalDeferred = null
                saveCurrentConversation()
            }
        }
    }

    private fun buildToolCalls(
        map: Map<Int, Triple<String, StringBuilder, StringBuilder>>
    ): List<ToolCall>? {
        if (map.isEmpty()) return null
        return map.entries.sortedBy { it.key }.map { (_, triple) ->
            ToolCall(
                id = triple.first,
                function = FunctionCall(
                    name = triple.second.toString(),
                    arguments = triple.third.toString()
                )
            )
        }
    }

    private fun saveCurrentConversation() {
        val msgs = messages.toList()
        if (msgs.isEmpty()) return

        val title = msgs.firstOrNull { it.role == "user" }?.content
            ?.take(50) ?: "Untitled"

        val conversation = Conversation(
            id = currentConversationId,
            title = title,
            createdAt = conversationCreatedAt,
            updatedAt = System.currentTimeMillis(),
            messages = msgs
        )
        conversationRepository.save(conversation)
    }

    fun loadConversation(id: String) {
        val conversation = conversationRepository.load(id) ?: return
        messages.clear()
        messages.addAll(conversation.messages)
        currentConversationId = conversation.id
        conversationCreatedAt = conversation.createdAt
        error.value = null
    }

    fun newConversation() {
        if (messages.isNotEmpty()) {
            saveCurrentConversation()
        }
        messages.clear()
        error.value = null
        currentConversationId = UUID.randomUUID().toString()
        conversationCreatedAt = System.currentTimeMillis()
    }

    fun regenerateLastResponse() {
        if (isLoading.value) return
        while (messages.isNotEmpty() && messages.last().role != "user") {
            messages.removeAt(messages.lastIndex)
        }
        if (messages.isNotEmpty()) {
            error.value = null
            runAgentLoop()
        }
    }
}
