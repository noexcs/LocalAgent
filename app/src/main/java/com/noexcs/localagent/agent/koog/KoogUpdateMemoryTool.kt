package com.noexcs.localagent.agent.koog

import ai.koog.agents.tools.Tool
import com.noexcs.localagent.data.MemoryManager
import kotlinx.serialization.Serializable

@Serializable
data class UpdateMemoryArgs(val key: String, val value: String)

@Serializable
data class UpdateMemoryResult(val success: Boolean)

class KoogUpdateMemoryTool(private val memoryManager: MemoryManager) : Tool<UpdateMemoryArgs, UpdateMemoryResult> {
    override val name = "update_memory"
    override val description = "Store information in persistent memory"

    override suspend fun execute(args: UpdateMemoryArgs): UpdateMemoryResult {
        memoryManager.saveMemory(args.key, args.value)
        return UpdateMemoryResult(success = true)
    }
}
