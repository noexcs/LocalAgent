package com.noexcs.localagent.data

import ai.koog.prompt.message.Message
import kotlinx.serialization.Serializable
import ai.koog.prompt.message.Message.Role

@Serializable
data class Session(
    val sessionId: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<Message>
)

@Serializable
data class MessageViewModel(
    val role: Role,
    var content: String
)