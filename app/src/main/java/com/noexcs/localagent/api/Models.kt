package com.noexcs.localagent.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val tools: List<ToolDefinition>? = null,
    @SerialName("tool_choice") val toolChoice: String? = null,
    val stream: Boolean? = null
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    val name: String? = null
)

@Serializable
data class ToolCall(
    val id: String,
    val type: String = "function",
    val function: FunctionCall
)

@Serializable
data class FunctionCall(
    val name: String,
    val arguments: String // JSON string
)

@Serializable
data class ChatResponse(
    val id: String,
    val choices: List<Choice>,
    val usage: Usage? = null
)

@Serializable
data class Choice(
    val index: Int,
    val message: ChatMessage,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0
)

@Serializable
data class ToolDefinition(
    val type: String = "function",
    val function: FunctionDefinition
)

@Serializable
data class FunctionDefinition(
    val name: String,
    val description: String,
    val parameters: JsonObject
)

@Serializable
data class ApiError(
    val error: ApiErrorDetail? = null
)

@Serializable
data class ApiErrorDetail(
    val message: String = "",
    val type: String? = null,
    val code: String? = null
)

// --- Streaming (SSE) models ---

@Serializable
data class StreamChunk(
    val id: String = "",
    val choices: List<StreamChoice> = emptyList()
)

@Serializable
data class StreamChoice(
    val index: Int = 0,
    val delta: StreamDelta = StreamDelta(),
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class StreamDelta(
    val role: String? = null,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<StreamToolCall>? = null
)

@Serializable
data class StreamToolCall(
    val index: Int = 0,
    val id: String? = null,
    val type: String? = null,
    val function: StreamFunctionCall? = null
)

@Serializable
data class StreamFunctionCall(
    val name: String? = null,
    val arguments: String? = null
)
