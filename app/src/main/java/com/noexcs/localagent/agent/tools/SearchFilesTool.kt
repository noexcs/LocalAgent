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

class SearchFilesTool(private val executor: TermuxExecutor) : Tool {
    override val name = "search_files"
    override val description = "Search for a pattern in files using grep. Returns matching lines with file paths and line numbers."
    override val parameters: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("pattern") {
                put("type", "string")
                put("description", "Search pattern (grep regex)")
            }
            putJsonObject("path") {
                put("type", "string")
                put("description", "Directory to search in (defaults to current directory)")
            }
        }
        putJsonArray("required") { add(JsonPrimitive("pattern")) }
    }

    override suspend fun execute(arguments: JsonObject): String {
        val pattern = arguments["pattern"]?.jsonPrimitive?.content
            ?: return "Error: missing 'pattern' argument"
        val path = arguments["path"]?.jsonPrimitive?.content ?: "."

        val result = executor.execute("grep -rn ${shellEscape(pattern)} ${shellEscape(path)}")
        return when {
            result.exitCode == 0 -> result.stdout
            result.exitCode == 1 -> "No matches found."
            else -> "Error searching: ${result.stderr}".trim()
        }
    }
}
