package com.noexcs.localagent.data

import com.noexcs.localagent.api.ChatMessage
import kotlinx.serialization.Serializable

@Serializable
data class Conversation(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<ChatMessage>
)

@Serializable
data class ConversationMeta(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long
)
