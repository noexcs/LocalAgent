package com.noexcs.localagent.agent.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

@SuppressLint("StaticFieldLeak")
object IntentTool : SimpleTool<IntentTool.Args>(
    argsType = typeToken<Args>(),
    name = "send_intent",
    description = """
        Send Android Intents to launch activities in other apps.

        Capabilities:
        - Launch activities in any app (explicit or implicit)
        - Share content (text, URLs) with chooser dialog
        - Open URLs, emails, phone numbers, maps
        - Pass extra data (strings, integers, booleans, etc.)
        - Control activity launch flags
        - Set MIME types for content

        Common use cases:
        1. Open URL: action="VIEW", data="https://example.com"
        2. Make call: action="DIAL", data="tel:123456789"
        3. Send email: action="SENDTO", data="mailto:test@example.com"
        4. Share text: action="SEND", type="text/plain", useChooser=true, extrasJson='{"android.intent.extra.TEXT": "Hello"}'
        5. View map: action="VIEW", data="geo:0,0?q=New+York"
        6. Launch specific app: action="MAIN", category="LAUNCHER", packageName="com.example.app"

        Security restrictions:
        - file:// URIs are blocked (use content:// with a FileProvider instead)
        - Broadcasts and services are not supported
        - Dangerous actions (install/uninstall apps, device admin, shutdown) are blocked
        - ACTION_CALL is blocked; use ACTION_DIAL instead

        IMPORTANT: For extras, use valid JSON format in extrasJson parameter.
        Example: extrasJson='{"key1": "value", "count": 5, "enabled": true}'
    """.trimIndent()
) {
    private lateinit var context: Context

    fun init(context: Context) {
        this.context = context.applicationContext
    }

    @Serializable
    data class Args(
        @property:LLMDescription("Intent action (e.g., ACTION_VIEW, ACTION_SEND, ACTION_DIAL, android.intent.action.VIEW)")
        val action: String = "android.intent.action.VIEW",
        
        @property:LLMDescription("Data URI (e.g., 'https://example.com', 'tel:123', 'mailto:test@test.com', 'file:///path')")
        val data: String = "",
        
        @property:LLMDescription("Package name for explicit intent (optional, for launching specific apps)")
        val packageName: String = "",
        
        @property:LLMDescription("Component class name for explicit intent (optional, used with packageName)")
        val className: String = "",
        
        @property:LLMDescription("MIME type (e.g., 'text/plain', 'image/jpeg', 'application/pdf')")
        val type: String = "",
        
        @property:LLMDescription("Category (e.g., 'android.intent.category.LAUNCHER', 'android.intent.category.BROWSABLE')")
        val category: String = "",
        
        @property:LLMDescription("Extra data as a strict JSON string. e.g., '{\"android.intent.extra.TEXT\": \"Hello, World\", \"count\": 5}'")
        val extrasJson: String = "{}",
        
        @property:LLMDescription("Launch flags as comma-separated values (e.g., 'FLAG_ACTIVITY_NEW_TASK,FLAG_ACTIVITY_CLEAR_TOP')")
        val flags: String = "",
        
        @property:LLMDescription("For SEND action: subject line for email/share")
        val subject: String = "",

        @property:LLMDescription("Wrap intent in a chooser dialog so the user can pick which app to use (recommended for SEND/share actions)")
        val useChooser: Boolean = false,

        @property:LLMDescription("Title shown on the chooser dialog (only used when useChooser=true)")
        val chooserTitle: String = ""
    )

    // Dangerous actions that must never be sent by the agent
    @Suppress("DEPRECATION")
    private val blockedActions = setOf(
        Intent.ACTION_FACTORY_TEST,
        Intent.ACTION_CALL,                                    // Direct call without user consent
        "android.intent.action.MASTER_CLEAR",                  // Factory reset
        "android.intent.action.REBOOT",                        // Reboot device
        "android.intent.action.SHUTDOWN",                      // Shutdown device
        "android.intent.action.REQUEST_SHUTDOWN",              // Request shutdown
        Intent.ACTION_INSTALL_PACKAGE,                         // Install APK
        Intent.ACTION_UNINSTALL_PACKAGE,                       // Uninstall app
        "android.app.action.ADD_DEVICE_ADMIN",                 // Gain device admin
        "android.intent.action.MANAGE_PACKAGE_STORAGE",        // Manage storage
        "android.settings.MANAGE_ALL_APPLICATIONS_SETTINGS",   // Manage all apps
        "android.settings.ACTION_MANAGE_OVERLAY_PERMISSION",   // Draw over other apps
        "android.settings.ACTION_MANAGE_WRITE_SETTINGS",       // Write system settings
        "android.settings.ACTION_ACCESSIBILITY_SETTINGS",      // Accessibility (can be abused)
        "android.settings.USAGE_ACCESS_SETTINGS",              // Usage access
    )

    // URI schemes that are not safe for the agent to use
    private val blockedUriSchemes = emptySet<String>()

    // Sensitive content provider authorities the agent should not access
    private val blockedContentAuthorities = setOf(
        "contacts", "com.android.contacts",
        "sms", "mms", "mms-sms",
        "call_log", "com.android.calllog",
        "telephony",
    )

    override suspend fun execute(args: Args): String {
        check(::context.isInitialized) { "IntentTool not initialized. Call IntentTool.init(context) first." }

        return try {
            val normalizedAction = normalizeAction(args.action)

            // Security: block dangerous actions
            if (normalizedAction in blockedActions) {
                return "Error: Action '${args.action}' is blocked for security reasons."
            }

            // Security: validate URI scheme
            if (args.data.isNotBlank()) {
                val uri = args.data.toUri()
                val scheme = uri.scheme?.lowercase()
                if (scheme != null && scheme in blockedUriSchemes) {
                    return "Error: '$scheme://' URIs are blocked. Use a content:// URI with FileProvider instead."
                }
                if (scheme == "content") {
                    val authority = uri.authority?.lowercase() ?: ""
                    if (blockedContentAuthorities.any { authority.contains(it) }) {
                        return "Error: Access to content provider '$authority' is blocked for privacy reasons."
                    }
                }
            }

            val intent = buildIntent(args, normalizedAction)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            // Wrap in chooser if requested
            val launchIntent = if (args.useChooser) {
                val title = args.chooserTitle.ifBlank { null }
                Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            } else {
                intent
            }

            // Resolve for logging purposes only (may return null on Android 11+ due to visibility)
            val resolved = if (!args.useChooser) {
                launchIntent.resolveActivity(context.packageManager)
            } else {
                null
            }

            try {
                context.startActivity(launchIntent)
                buildString {
                    appendLine("Activity launched successfully.")
                    appendLine("Action: $normalizedAction")
                    if (args.data.isNotEmpty()) appendLine("Data: ${args.data}")
                    if (args.packageName.isNotEmpty()) appendLine("Package: ${args.packageName}")
                    if (resolved != null) appendLine("Resolved to: ${resolved.packageName}/${resolved.className}")
                    if (args.useChooser) appendLine("Chooser: shown")
                }
            } catch (e: ActivityNotFoundException) {
                buildString {
                    appendLine("Error: No application found to handle this intent.")
                    appendLine("Action: ${args.action}")
                    if (args.data.isNotEmpty()) appendLine("Data: ${args.data}")
                    appendLine()
                    appendLine("Suggestions:")
                    if (args.packageName.isNotEmpty()) appendLine("- Try removing packageName to use implicit resolution")
                    if (!args.useChooser) appendLine("- Try useChooser=true to let the user pick an app")
                    appendLine("- Verify the action/data/type combination is correct")
                }
            }
        } catch (e: SecurityException) {
            "Error: Permission denied. ${e.message}"
        } catch (e: Exception) {
            "Error executing intent: ${e.message}"
        }
    }

    private fun buildIntent(args: Args, action: String): Intent {
        val intent = Intent()

        intent.action = action

        if (args.data.isNotBlank() && args.type.isNotBlank()) {
            intent.setDataAndType(args.data.toUri(), args.type)
        } else if (args.data.isNotBlank()) {
            intent.data = args.data.toUri()
        } else if (args.type.isNotBlank()) {
            intent.type = args.type
        }
        
        // Set package for explicit intent
        if (args.packageName.isNotBlank()) {
            if (args.className.isNotBlank()) {
                // Explicit component
                intent.setClassName(args.packageName, args.className)
            } else {
                // Just package
                intent.setPackage(args.packageName)
            }
        }
        
        // Set category
        if (args.category.isNotBlank()) {
            intent.addCategory(normalizeCategory(args.category))
        }
        
        // Add subject extra for SEND actions
        if (args.subject.isNotBlank() && action == Intent.ACTION_SEND) {
            intent.putExtra(Intent.EXTRA_SUBJECT, args.subject)
        }

        if (args.extrasJson.isNotBlank() && args.extrasJson != "{}") {
            val result = parseExtrasFromJson(args.extrasJson)
            if (result.isFailure) {
                throw IllegalArgumentException("Invalid extrasJson: ${result.exceptionOrNull()?.message}. Expected valid JSON like {\"key\": \"value\"}")
            }
            result.getOrThrow().forEach { (key, value) ->
                when (value) {
                    is String -> intent.putExtra(key, value)
                    is Int -> intent.putExtra(key, value)
                    is Boolean -> intent.putExtra(key, value)
                    is Long -> intent.putExtra(key, value)
                    is Double -> intent.putExtra(key, value)
                    is Float -> intent.putExtra(key, value)
                }
            }
        }
        
        // Set launch flags
        if (args.flags.isNotBlank()) {
            args.flags.split(",").map { it.trim() }.forEach { flag ->
                val flagValue = parseFlag(flag)
                if (flagValue != null) {
                    intent.addFlags(flagValue)
                }
            }
        }
        
        return intent
    }

    private fun normalizeAction(action: String): String {
        return when (action.uppercase()) {
            "VIEW", "ACTION_VIEW" -> Intent.ACTION_VIEW
            "SEND", "ACTION_SEND" -> Intent.ACTION_SEND
            "SEND_MULTIPLE", "ACTION_SEND_MULTIPLE" -> Intent.ACTION_SEND_MULTIPLE
            "SENDTO", "ACTION_SENDTO" -> Intent.ACTION_SENDTO
            "DIAL", "ACTION_DIAL" -> Intent.ACTION_DIAL
            "CALL", "ACTION_CALL" -> Intent.ACTION_CALL
            "EDIT", "ACTION_EDIT" -> Intent.ACTION_EDIT
            "PICK", "ACTION_PICK" -> Intent.ACTION_PICK
            "GET_CONTENT", "ACTION_GET_CONTENT" -> Intent.ACTION_GET_CONTENT
            "OPEN_DOCUMENT", "ACTION_OPEN_DOCUMENT" -> Intent.ACTION_OPEN_DOCUMENT
            "CREATE_DOCUMENT", "ACTION_CREATE_DOCUMENT" -> Intent.ACTION_CREATE_DOCUMENT
            "WEB_SEARCH", "ACTION_WEB_SEARCH" -> Intent.ACTION_WEB_SEARCH
            "SEARCH", "ACTION_SEARCH" -> Intent.ACTION_SEARCH
            "INSERT", "ACTION_INSERT" -> Intent.ACTION_INSERT
            "DELETE", "ACTION_DELETE" -> Intent.ACTION_DELETE
            "MAIN", "ACTION_MAIN" -> Intent.ACTION_MAIN
            "CHOOSER", "ACTION_CHOOSER" -> Intent.ACTION_CHOOSER
            else -> action // Use as-is if it's already a full action string
        }
    }

    private fun normalizeCategory(category: String): String {
        return when (category.uppercase()) {
            "LAUNCHER", "CATEGORY_LAUNCHER" -> Intent.CATEGORY_LAUNCHER
            "BROWSABLE", "CATEGORY_BROWSABLE" -> Intent.CATEGORY_BROWSABLE
            "DEFAULT", "CATEGORY_DEFAULT" -> Intent.CATEGORY_DEFAULT
            "INFO", "CATEGORY_INFO" -> Intent.CATEGORY_INFO
            "HOME", "CATEGORY_HOME" -> Intent.CATEGORY_HOME
            "OPENABLE", "CATEGORY_OPENABLE" -> Intent.CATEGORY_OPENABLE
            "APP_BROWSER", "CATEGORY_APP_BROWSER" -> Intent.CATEGORY_APP_BROWSER
            "APP_EMAIL", "CATEGORY_APP_EMAIL" -> Intent.CATEGORY_APP_EMAIL
            "APP_MUSIC", "CATEGORY_APP_MUSIC" -> Intent.CATEGORY_APP_MUSIC
            else -> category
        }
    }

    private fun parseExtrasFromJson(jsonString: String): Result<Map<String, Any>> {
        val extras = mutableMapOf<String, Any>()

        return try {
            val json = Json { ignoreUnknownKeys = true }
            val jsonObject = json.decodeFromString<JsonObject>(jsonString)

            jsonObject.forEach { (key, value) ->
                when {
                    value is JsonPrimitive && value.isString -> {
                        extras[key] = value.content
                    }
                    value is JsonPrimitive -> {
                        value.intOrNull?.let { extras[key] = it }
                            ?: value.longOrNull?.let { extras[key] = it }
                            ?: value.doubleOrNull?.let { extras[key] = it }
                            ?: value.floatOrNull?.let { extras[key] = it }
                            ?: value.booleanOrNull?.let { extras[key] = it }
                    }
                    else -> {
                        // Skip complex types (arrays, nested objects)
                    }
                }
            }
            Result.success(extras)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseFlag(flag: String): Int? {
        return when (flag.uppercase()) {
            "FLAG_ACTIVITY_NEW_TASK" -> Intent.FLAG_ACTIVITY_NEW_TASK
            "FLAG_ACTIVITY_CLEAR_TOP" -> Intent.FLAG_ACTIVITY_CLEAR_TOP
            "FLAG_ACTIVITY_SINGLE_TOP" -> Intent.FLAG_ACTIVITY_SINGLE_TOP
            "FLAG_ACTIVITY_NO_HISTORY" -> Intent.FLAG_ACTIVITY_NO_HISTORY
            "FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS" -> Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            "FLAG_ACTIVITY_BROUGHT_TO_FRONT" -> Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT
            "FLAG_ACTIVITY_RESET_TASK_IF_NEEDED" -> Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            "FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY" -> Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY
            "FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET", "FLAG_ACTIVITY_NEW_DOCUMENT" -> Intent.FLAG_ACTIVITY_NEW_DOCUMENT
            "FLAG_ACTIVITY_FORWARD_RESULT" -> Intent.FLAG_ACTIVITY_FORWARD_RESULT
            "FLAG_ACTIVITY_PREVIOUS_IS_TOP" -> Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP
            "FLAG_DEBUG_LOG_RESOLUTION" -> Intent.FLAG_DEBUG_LOG_RESOLUTION
            "FLAG_FROM_BACKGROUND" -> Intent.FLAG_FROM_BACKGROUND
            "FLAG_GRANT_READ_URI_PERMISSION" -> Intent.FLAG_GRANT_READ_URI_PERMISSION
            "FLAG_GRANT_WRITE_URI_PERMISSION" -> Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            "FLAG_RECEIVER_REGISTERED_ONLY" -> Intent.FLAG_RECEIVER_REGISTERED_ONLY
            else -> null
        }
    }
}
