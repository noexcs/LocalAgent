package com.noexcs.localagent.agent.tools

import com.noexcs.localagent.agent.Tool
import com.noexcs.localagent.data.MemoryManager
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class UpdateMemoryTool(private val memoryManager: MemoryManager) : Tool {
    override val name = "update_memory"
    override val description =
        "Update your persistent memory. The content is markdown text that will be included in your system prompt across all conversations. " +
        "Use this to remember user preferences, important facts, or context. " +
        "This replaces the entire memory, so include everything you want to keep."
    override val parameters: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("content") {
                put("type", "string")
                put("description", "The full markdown content to store as memory")
            }
        }
        putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("content")) }
    }

    override suspend fun execute(arguments: JsonObject): String {
        val content = arguments["content"]?.jsonPrimitive?.content
            ?: return "Error: missing 'content' argument"
        memoryManager.write(content)
        return "Memory updated successfully."
    }
}
