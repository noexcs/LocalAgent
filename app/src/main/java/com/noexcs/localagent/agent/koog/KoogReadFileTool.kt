package com.noexcs.localagent.agent.koog

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import com.noexcs.localagent.agent.TermuxExecutor
import kotlinx.serialization.Serializable

object KoogReadFileTool : SimpleTool<KoogReadFileTool.Args>(
    argsType = typeToken<Args>(),
    name = "read_file",
    description = "Read the contents of a file"
) {
    private lateinit var executor: TermuxExecutor

    fun init(executor: TermuxExecutor) {
        this.executor = executor
    }

    @Serializable
    data class Args(
        @property:LLMDescription("Path to the file")
        val path: String
    )

    override suspend fun execute(args: Args): String {
        val result = executor.execute("cat '${args.path.replace("'", "'\\''")}'")
        return if (result.exitCode == 0) result.stdout else "Error: ${result.stderr}"
    }
}
