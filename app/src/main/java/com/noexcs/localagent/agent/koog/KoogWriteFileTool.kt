package com.noexcs.localagent.agent.koog

import ai.koog.agents.tools.Tool
import com.noexcs.localagent.agent.TermuxExecutor
import kotlinx.serialization.Serializable

@Serializable
data class WriteFileArgs(val path: String, val content: String)

@Serializable
data class WriteFileResult(val success: Boolean, val error: String? = null)

class KoogWriteFileTool(private val executor: TermuxExecutor) : Tool<WriteFileArgs, WriteFileResult> {
    override val name = "write_file"
    override val description = "Write content to a file"

    override suspend fun execute(args: WriteFileArgs): WriteFileResult {
        val escapedPath = args.path.replace("'", "'\\''")
        val escapedContent = args.content.replace("'", "'\\''")
        val result = executor.execute("echo '$escapedContent' > '$escapedPath'")
        return WriteFileResult(
            success = result.exitCode == 0,
            error = if (result.exitCode != 0) result.stderr else null
        )
    }
}
