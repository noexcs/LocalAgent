package com.noexcs.localagent.agent.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import com.noexcs.localagent.data.MemoryManager
import kotlinx.serialization.Serializable

object UpdateMemoryTool : SimpleTool<UpdateMemoryTool.Args>(
    argsType = typeToken<Args>(),
    name = "update_memory",
    description = "Store information in persistent memory"
) {
    private lateinit var memoryManager: MemoryManager

    fun init(memoryManager: MemoryManager) {
        this.memoryManager = memoryManager
    }

    @Serializable
    data class Args(
        @property:LLMDescription("Memory key")
        val key: String,
        @property:LLMDescription("Memory value")
        val value: String
    )

    override suspend fun execute(args: Args): String {
        memoryManager.saveMemory(args.key, args.value)
        return "Memory updated successfully"
    }
}
