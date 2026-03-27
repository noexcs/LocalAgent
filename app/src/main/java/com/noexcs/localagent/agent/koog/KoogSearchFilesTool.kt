package com.noexcs.localagent.agent.koog

import ai.koog.agents.tools.Tool
import com.noexcs.localagent.agent.TermuxExecutor
import kotlinx.serialization.Serializable

@Serializable
data class SearchFilesArgs(val pattern: String, val path: String = ".")

@Serializable
data class SearchFilesResult(val matches: String, val error: String? = null)

class KoogSearchFilesTool(private val executor: TermuxExecutor) : Tool<SearchFilesArgs, SearchFilesResult> {
    override val name = "search_files"
    override val description = "Search for files matching a pattern"

    override suspend fun execute(args: SearchFilesArgs): SearchFilesResult {
        val result = executor.execute("find '${args.path.replace("'", "'\\''")}'  -name '${args.pattern.replace("'", "'\\''")}'")
        return if (result.exitCode == 0) {
            SearchFilesResult(matches = result.stdout)
        } else {
            SearchFilesResult(matches = "", error = result.stderr)
        }
    }
}
