package com.noexcs.localagent.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class ScheduledTaskRepository(context: Context) {
    private val dir = File(context.filesDir, "scheduled_tasks").also { it.mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun save(task: ScheduledTask) {
        File(dir, "${task.id}.json").writeText(json.encodeToString(task))
    }

    fun load(id: String): ScheduledTask? {
        val file = File(dir, "$id.json")
        if (!file.exists()) return null
        return try {
            json.decodeFromString<ScheduledTask>(file.readText())
        } catch (_: Exception) {
            null
        }
    }

    fun listAll(): List<ScheduledTask> {
        return dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { file ->
                try {
                    json.decodeFromString<ScheduledTask>(file.readText())
                } catch (_: Exception) {
                    null
                }
            }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()
    }

    fun delete(id: String) {
        File(dir, "$id.json").delete()
    }
}
