package com.noexcs.localagent.agent.koog

import ai.koog.agents.tools.Tool
import com.noexcs.localagent.agent.TermuxExecutor
import kotlinx.serialization.Serializable

@Serializable
data class ListDirectoryArgs(val path: String = ".")

@Serializable
data class ListDirectoryResult(val files: String, val error: String? = null)

class KoogListDirectoryTool(private val executor: TermuxExecutor) : Tool<ListDirectoryArgs, ListDirectoryResult> {
    override val name = "list_directory"
    override val description = "List files in a directory"

    override suspend fun execute(args: ListDirectoryArgs): ListDirectoryResult {
        val result = executor.execute("ls -lah '${args.path.replace("'", "'\\''")}'")
        return if (result.exitCode == 0) {
            ListDirectoryResult(files = result.stdout)
        } else {
            ListDirectoryResult(files = "", error = result.stderr)
        }
    }
}
