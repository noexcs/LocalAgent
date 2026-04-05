package com.noexcs.localagent.agent.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import android.annotation.SuppressLint
import com.noexcs.localagent.agent.TermuxExecutor
import kotlinx.serialization.Serializable

@SuppressLint("StaticFieldLeak")
object TermuxDialogTool : SimpleTool<TermuxDialogTool.Args>(
    argsType = typeToken<Args>(),
    name = "show_dialog",
    description = """
        Display interactive dialog widgets to get user input.
        
        Supported widget types:
        - text: Input text (single or multiple lines, password, numbers)
        - confirm: Yes/No confirmation dialog
        - checkbox: Select multiple values using checkboxes
        - radio: Pick a single value from radio buttons
        - spinner: Pick a single value from dropdown
        - sheet: Pick a value from sliding bottom sheet
        - counter: Pick a number in specified range
        - date: Pick a date
        - time: Pick a time
        - speech: Obtain speech using microphone
        
        Returns JSON with user's selection in 'text' field.
    """.trimIndent()
) {
    private lateinit var executor: TermuxExecutor

    fun init(executor: TermuxExecutor) {
        this.executor = executor
    }

    @Serializable
    data class Args(
        @property:LLMDescription("Widget type: text, confirm, checkbox, radio, spinner, sheet, counter, date, time, speech")
        val widget: String = "text",
        
        @property:LLMDescription("Title of the dialog (optional)")
        val title: String = "",
        
        @property:LLMDescription("Hint text for text/speech widgets (optional)")
        val hint: String = "",
        
        @property:LLMDescription("Comma-separated values for checkbox/radio/spinner/sheet (e.g., 'Option1,Option2,Option3')")
        val values: String = "",
        
        @property:LLMDescription("Range for counter widget as 'min,max,start' (e.g., '0,100,50')")
        val range: String = "",
        
        @property:LLMDescription("Date format pattern for date widget (e.g., 'yyyy-MM-dd')")
        val dateFormat: String = "",
        
        @property:LLMDescription("For text widget: true for multi-line input")
        val multiLine: Boolean = false,
        
        @property:LLMDescription("For text widget: true for numeric input")
        val numeric: Boolean = false,
        
        @property:LLMDescription("For text widget: true for password input")
        val password: Boolean = false
    )

    override suspend fun execute(args: Args): String {
        return try {
            val command = buildCommand(args)
            val result = executor.execute(command)
            
            if (result.exitCode != 0) {
                return "Error showing dialog: ${result.stderr.ifEmpty { "Unknown error" }}"
            }
            
            val output = result.stdout.trim()
            if (output.isEmpty()) {
                return "Dialog was cancelled or no input provided."
            }
            
            // Parse JSON response from termux-dialog
            parseDialogResult(output)
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun buildCommand(args: Args): String {
        val cmd = StringBuilder("termux-dialog ${args.widget}")
        
        // Add title if provided
        if (args.title.isNotBlank()) {
            cmd.append(" -t \"${args.title}\"")
        }
        
        when (args.widget.lowercase()) {
            "text" -> {
                if (args.hint.isNotBlank()) {
                    cmd.append(" -i \"${args.hint}\"")
                }
                if (args.multiLine) {
                    cmd.append(" -m")
                }
                if (args.numeric) {
                    cmd.append(" -n")
                }
                if (args.password) {
                    cmd.append(" -p")
                }
            }
            
            "speech" -> {
                if (args.hint.isNotBlank()) {
                    cmd.append(" -i \"${args.hint}\"")
                }
            }
            
            "checkbox", "radio", "spinner", "sheet" -> {
                if (args.values.isBlank()) {
                    throw IllegalArgumentException("Values are required for ${args.widget} widget")
                }
                cmd.append(" -v \"${args.values}\"")
            }
            
            "counter" -> {
                if (args.range.isNotBlank()) {
                    cmd.append(" -r \"${args.range}\"")
                }
            }
            
            "date" -> {
                if (args.dateFormat.isNotBlank()) {
                    cmd.append(" -d \"${args.dateFormat}\"")
                }
            }
            
            "confirm" -> {
                if (args.hint.isNotBlank()) {
                    cmd.append(" -i \"${args.hint}\"")
                }
            }
            
            "time" -> {
                // No additional options for time widget
            }
            
            else -> {
                throw IllegalArgumentException("Unsupported widget type: ${args.widget}")
            }
        }
        
        return cmd.toString()
    }

    private fun parseDialogResult(jsonOutput: String): String {
        return try {
            // termux-dialog returns JSON like: {"text": "user input"}
            // Extract the text value
            val textMatch = Regex("\"text\"\\s*:\\s*\"([^\"]*)\"").find(jsonOutput)
            val codeMatch = Regex("\"code\"\\s*:\\s*(\\d+)").find(jsonOutput)
            
            val code = codeMatch?.groupValues?.get(1)?.toIntOrNull()
            
            if (code == 1) {
                return "Dialog was cancelled by user."
            }
            
            val text = textMatch?.groupValues?.get(1) ?: ""
            
            if (text.isEmpty()) {
                return "No input provided."
            }
            
            "User input: $text"
        } catch (e: Exception) {
            // If JSON parsing fails, return raw output
            "Dialog result: $jsonOutput"
        }
    }
}
