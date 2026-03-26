package com.noexcs.localagent.agent.tools

import com.noexcs.localagent.agent.TermuxExecutor
import com.noexcs.localagent.agent.Tool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class ExecuteCommandTool(private val executor: TermuxExecutor) : Tool {
    override val name = "execute_command"
    override val description = "Execute a shell command in Termux. Use this for general-purpose command execution."
    override val requiresConfirmation = true
    override val parameters: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("command") {
                put("type", "string")
                put("description", "The shell command to execute")
            }
            putJsonObject("workdir") {
                put("type", "string")
                put("description", "Working directory (defaults to home)")
            }
        }
        putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("command")) }
    }

    override suspend fun execute(arguments: JsonObject): String {
        val command = arguments["command"]?.jsonPrimitive?.content
            ?: return "Error: missing 'command' argument"
        val workdir = arguments["workdir"]?.jsonPrimitive?.content
            ?: "/data/data/com.termux/files/home"

        val result = executor.execute(command, workdir)
        return buildString {
            if (result.errorMessage != null) appendLine("Error: ${result.errorMessage}")
            if (result.stdout.isNotEmpty()) appendLine(result.stdout)
            if (result.stderr.isNotEmpty()) appendLine("STDERR: ${result.stderr}")
            appendLine("Exit code: ${result.exitCode}")
        }.trim()
    }
}
