package com.noexcs.localagent.agent.koog

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import com.noexcs.localagent.agent.TermuxExecutor
import kotlinx.serialization.Serializable

object KoogExecuteCommandTool : SimpleTool<KoogExecuteCommandTool.Args>(
    argsType = typeToken<Args>(),
    name = "execute_command",
    description = "Execute a shell command in Termux"
) {
    private lateinit var executor: TermuxExecutor

    fun init(executor: TermuxExecutor) {
        this.executor = executor
    }

    @Serializable
    data class Args(
        @property:LLMDescription("The shell command to execute")
        val command: String,
        @property:LLMDescription("Working directory (defaults to home)")
        val workdir: String = "/data/data/com.termux/files/home"
    )

    override suspend fun execute(args: Args): String {
        val result = executor.execute(args.command, args.workdir)
        return buildString {
            if (result.stdout.isNotEmpty()) appendLine(result.stdout)
            if (result.stderr.isNotEmpty()) appendLine("STDERR: ${result.stderr}")
            appendLine("Exit code: ${result.exitCode}")
        }.trim()
    }
}
