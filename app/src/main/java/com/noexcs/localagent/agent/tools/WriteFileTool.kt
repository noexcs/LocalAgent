package com.noexcs.localagent.agent.tools

import com.noexcs.localagent.agent.TermuxExecutor
import com.noexcs.localagent.agent.Tool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class WriteFileTool(private val executor: TermuxExecutor) : Tool {
    override val name = "write_file"
    override val description = "Write content to a file, creating it if it doesn't exist or overwriting if it does."
    override val requiresConfirmation = true
    override val parameters: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("path") {
                put("type", "string")
                put("description", "Absolute or relative path to the file")
            }
            putJsonObject("content") {
                put("type", "string")
                put("description", "Content to write to the file")
            }
        }
        putJsonArray("required") {
            add(JsonPrimitive("path"))
            add(JsonPrimitive("content"))
        }
    }

    override suspend fun execute(arguments: JsonObject): String {
        val path = arguments["path"]?.jsonPrimitive?.content
            ?: return "Error: missing 'path' argument"
        val content = arguments["content"]?.jsonPrimitive?.content
            ?: return "Error: missing 'content' argument"

        // Use heredoc to write content safely
        val command = "cat > ${shellEscape(path)} << 'LOCALAGENT_EOF'\n$content\nLOCALAGENT_EOF"
        val result = executor.execute(command)
        return if (result.exitCode == 0) {
            "File written successfully: $path"
        } else {
            "Error writing file: ${result.stderr}".trim()
        }
    }
}
