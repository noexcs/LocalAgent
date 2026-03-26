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

class ReadFileTool(private val executor: TermuxExecutor) : Tool {
    override val name = "read_file"
    override val description = "Read the contents of a file at the given path."
    override val parameters: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("path") {
                put("type", "string")
                put("description", "Absolute or relative path to the file")
            }
        }
        putJsonArray("required") { add(JsonPrimitive("path")) }
    }

    override suspend fun execute(arguments: JsonObject): String {
        val path = arguments["path"]?.jsonPrimitive?.content
            ?: return "Error: missing 'path' argument"

        val result = executor.execute("cat ${shellEscape(path)}")
        return if (result.exitCode == 0) {
            result.stdout
        } else {
            "Error reading file: ${result.stderr}".trim()
        }
    }
}

internal fun shellEscape(s: String): String = "'${s.replace("'", "'\\''")}'"
