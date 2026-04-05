package com.noexcs.localagent.agent.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import kotlinx.serialization.Serializable

@SuppressLint("StaticFieldLeak")
object GetAppInfoTool : SimpleTool<GetAppInfoTool.Args>(
    argsType = typeToken<Args>(),
    name = "get_app_info",
    description = "Get list of all installed apps or detailed information about a specific app"
) {
    private lateinit var context: Context

    fun init(context: Context) {
        this.context = context.applicationContext
    }

    @Serializable
    data class Args(
        @property:LLMDescription("Package name of the app to get details for. Leave empty to get list of all installed apps")
        val packageName: String = ""
    )

    override suspend fun execute(args: Args): String {
        return try {
            if (args.packageName.isBlank()) {
                getAllInstalledApps()
            } else {
                getAppDetails(args.packageName)
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun getAllInstalledApps(): String {
        val pm = context.packageManager
        
        // Get ALL installed packages (not just launcher apps)
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        
        if (packages.isEmpty()) {
            return "No applications found."
        }

        val appList = packages.map { appInfo ->
            val label = appInfo.loadLabel(pm).toString()
            val packageName = appInfo.packageName
            val version = getAppVersion(packageName)
            val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            val systemTag = if (isSystem) " [System]" else ""
            "- $label$systemTag ($packageName) v$version"
        }.sorted()

        return buildString {
            appendLine("Installed Applications (${appList.size}):")
            appendLine("=".repeat(50))
            appList.forEach { appendLine(it) }
        }
    }

    private fun getAppDetails(packageName: String): String {
        val pm = context.packageManager
        
        val packageInfo = try {
            pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES or 
                PackageManager.GET_SERVICES or 
                PackageManager.GET_RECEIVERS or 
                PackageManager.GET_PROVIDERS)
        } catch (e: PackageManager.NameNotFoundException) {
            return "Error: App '$packageName' is not installed."
        }

        val appInfo = packageInfo.applicationInfo ?: return "Error: Cannot retrieve app info."
        val label = appInfo.loadLabel(pm).toString()
        val versionName = packageInfo.versionName ?: "Unknown"
        val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        
        val installTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(packageInfo.firstInstallTime))
        
        val updateTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(packageInfo.lastUpdateTime))

        val isSystemApp = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
        val isEnabled = appInfo.enabled
        val targetSdkVersion = appInfo.targetSdkVersion
        val minSdkVersion = appInfo.minSdkVersion

        val sourceDir = appInfo.sourceDir
        val dataDir = appInfo.dataDir

        val activities = packageInfo.activities?.size ?: 0
        val services = packageInfo.services?.size ?: 0
        val receivers = packageInfo.receivers?.size ?: 0
        val providers = packageInfo.providers?.size ?: 0

        val requestedPermissions = packageInfo.requestedPermissions?.toList() ?: emptyList()

        return buildString {
            appendLine("App Information:")
            appendLine("=".repeat(50))
            appendLine("Name: $label")
            appendLine("Package: $packageName")
            appendLine("Version: $versionName ($versionCode)")
            appendLine("Target SDK: $targetSdkVersion")
            appendLine("Min SDK: $minSdkVersion")
            appendLine("System App: $isSystemApp")
            appendLine("Enabled: $isEnabled")
            appendLine("Installed: $installTime")
            appendLine("Last Updated: $updateTime")
            appendLine("")
            appendLine("Components:")
            appendLine("  Activities: $activities")
            appendLine("  Services: $services")
            appendLine("  Receivers: $receivers")
            appendLine("  Providers: $providers")
            appendLine("")
            appendLine("Paths:")
            appendLine("  Source: $sourceDir")
            appendLine("  Data: $dataDir")
            
            if (requestedPermissions.isNotEmpty()) {
                appendLine("")
                appendLine("Permissions (${requestedPermissions.size}):")
                requestedPermissions.take(10).forEach { perm ->
                    appendLine("  - $perm")
                }
                if (requestedPermissions.size > 10) {
                    appendLine("  ... and ${requestedPermissions.size - 10} more")
                }
            }
        }
    }

    private fun getAppVersion(packageName: String): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
            packageInfo.versionName ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }
}
