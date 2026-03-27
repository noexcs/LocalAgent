package com.noexcs.localagent.data

import android.content.Context
import java.io.File

class MemoryManager(context: Context) {
    private val file = File(context.filesDir, "memory.md")

    fun read(): String = if (file.exists()) file.readText() else ""

    fun write(content: String) {
        file.writeText(content)
    }

    fun saveMemory(key: String, value: String) {
        val current = read()
        val updated = "$current\n## $key\n$value\n"
        write(updated)
    }
}
