package com.noexcs.localagent.agent.koog

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import com.noexcs.localagent.agent.TermuxExecutor
import kotlinx.serialization.Serializable

object KoogWriteFileTool : SimpleTool<KoogWriteFileTool.Args>(
    argsType = typeToken<Args>(),
    name = "write_file",
    description = "Write content to a file"
) {
    private lateinit var executor: TermuxExecutor

    fun init(executor: TermuxExecutor) {
        this.executor = executor
    }

    @Serializable
    data class Args(
        @property:LLMDescription("Path to the file")
        val path: String,
        @property:LLMDescription("Content to write")
        val content: String
    )

    override suspend fun execute(args: Args): String {
        val escapedPath = args.path.replace("'", "'\\''")
        val escapedContent = args.content.replace("'", "'\\''")
        val result = executor.execute("echo '$escapedContent' > '$escapedPath'")
        return if (result.exitCode == 0) "File written successfully" else "Error: ${result.stderr}"
    }
}
