package com.noexcs.localagent.agent

import com.noexcs.localagent.api.FunctionDefinition
import com.noexcs.localagent.api.ToolDefinition
import kotlinx.serialization.json.JsonObject

interface Tool {
    val name: String
    val description: String
    val parameters: JsonObject
    val requiresConfirmation: Boolean get() = false
    suspend fun execute(arguments: JsonObject): String
}

class ToolRegistry {
    private val tools = mutableMapOf<String, Tool>()

    fun register(tool: Tool) {
        tools[tool.name] = tool
    }

    fun getAll(): List<Tool> = tools.values.toList()

    fun getTool(name: String): Tool? = tools[name]

    fun toToolDefinitions(): List<ToolDefinition> = tools.values.map { tool ->
        ToolDefinition(
            function = FunctionDefinition(
                name = tool.name,
                description = tool.description,
                parameters = tool.parameters
            )
        )
    }
}
