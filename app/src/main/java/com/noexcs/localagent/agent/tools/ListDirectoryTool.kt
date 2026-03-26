package com.noexcs.localagent.agent.tools

import com.noexcs.localagent.agent.TermuxExecutor
import com.noexcs.localagent.agent.Tool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class ListDirectoryTool(private val executor: TermuxExecutor) : Tool {
    override val name = "list_directory"
    override val description = "List files and directories at the given path."
    override val parameters: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("path") {
                put("type", "string")
                put("description", "Directory path to list (defaults to home directory)")
            }
        }
    }

    override suspend fun execute(arguments: JsonObject): String {
        val path = arguments["path"]?.jsonPrimitive?.content ?: "."

        val result = executor.execute("ls -la ${shellEscape(path)}")
        return if (result.exitCode == 0) {
            result.stdout
        } else {
            "Error listing directory: ${result.stderr}".trim()
        }
    }
}
