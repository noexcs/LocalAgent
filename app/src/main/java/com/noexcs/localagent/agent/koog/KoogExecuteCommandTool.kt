package com.noexcs.localagent.agent.koog

import ai.koog.agents.tools.Tool
import com.noexcs.localagent.agent.TermuxExecutor
import kotlinx.serialization.Serializable

@Serializable
data class ExecuteCommandArgs(
    val command: String,
    val workdir: String = "/data/data/com.termux/files/home"
)

@Serializable
data class CommandResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int
)

class KoogExecuteCommandTool(private val executor: TermuxExecutor) : Tool<ExecuteCommandArgs, CommandResult> {
    override val name = "execute_command"
    override val description = "Execute a shell command in Termux"

    override suspend fun execute(args: ExecuteCommandArgs): CommandResult {
        val result = executor.execute(args.command, args.workdir)
        return CommandResult(
            stdout = result.stdout,
            stderr = result.stderr,
            exitCode = result.exitCode
        )
    }
}
