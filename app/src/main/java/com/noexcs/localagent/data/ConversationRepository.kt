package com.noexcs.localagent.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class ConversationRepository(context: Context) {
    private val dir = File(context.filesDir, "conversations").also { it.mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun save(conversation: Conversation) {
        File(dir, "${conversation.id}.json").writeText(json.encodeToString(conversation))
    }

    fun load(id: String): Conversation? {
        val file = File(dir, "$id.json")
        if (!file.exists()) return null
        return try {
            json.decodeFromString<Conversation>(file.readText())
        } catch (_: Exception) {
            null
        }
    }

    fun listAll(): List<ConversationMeta> {
        return dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { file ->
                try {
                    val conv = json.decodeFromString<Conversation>(file.readText())
                    ConversationMeta(conv.id, conv.title, conv.createdAt, conv.updatedAt)
                } catch (_: Exception) {
                    null
                }
            }
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()
    }

    fun delete(id: String) {
        File(dir, "$id.json").delete()
    }

    fun rename(id: String, newTitle: String) {
        val conv = load(id) ?: return
        save(conv.copy(title = newTitle))
    }
}
