package com.noexcs.localagent.agent.koog

import ai.koog.agents.tools.Tool
import com.noexcs.localagent.agent.TermuxExecutor
import kotlinx.serialization.Serializable

@Serializable
data class ReadFileArgs(val path: String)

@Serializable
data class ReadFileResult(val content: String, val error: String? = null)

class KoogReadFileTool(private val executor: TermuxExecutor) : Tool<ReadFileArgs, ReadFileResult> {
    override val name = "read_file"
    override val description = "Read the contents of a file"

    override suspend fun execute(args: ReadFileArgs): ReadFileResult {
        val result = executor.execute("cat '${args.path.replace("'", "'\\''")}'")
        return if (result.exitCode == 0) {
            ReadFileResult(content = result.stdout)
        } else {
            ReadFileResult(content = "", error = result.stderr)
        }
    }
}
