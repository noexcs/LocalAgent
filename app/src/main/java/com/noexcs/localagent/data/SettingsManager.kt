package com.noexcs.localagent.data

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList

class SettingsManager(context: Context) {
    private val prefs = context.getSharedPreferences("agent_settings", Context.MODE_PRIVATE)
    private val appContext = context.applicationContext

    var userSystemPrompt: String
        get() = prefs.getString("user_system_prompt", "") ?: ""
        set(value) = prefs.edit().putString("user_system_prompt", value).apply()

    var providerType: String
        get() = prefs.getString("provider_type", "OpenAI-Compatible") ?: "OpenAI-Compatible"
        set(value) = prefs.edit().putString("provider_type", value).apply()

    var baseUrl: String
        get() = prefs.getString("base_url", "https://api.deepseek.com") ?: "https://api.deepseek.com"
        set(value) = prefs.edit().putString("base_url", value).apply()

    var apiKey: String
        get() = prefs.getString("api_key", "") ?: ""
        set(value) = prefs.edit().putString("api_key", value).apply()

    var model: String
        get() = prefs.getString("model", "deepseek-chat") ?: "deepseek-chat"
        set(value) = prefs.edit().putString("model", value).apply()

    /** "ask" = confirm before executing dangerous tools, "always_allow" = skip confirmation */
    var toolConfirmMode: String
        get() = prefs.getString("tool_confirm_mode", "ask") ?: "ask"
        set(value) = prefs.edit().putString("tool_confirm_mode", value).apply()

    /** Language tag: "" = system default, "en" = English, "zh-Hans" = Simplified Chinese */
    var language: String
        get() = prefs.getString("language", "") ?: ""
        set(value) {
            prefs.edit().putString("language", value).apply()
            applyLocale(value)
        }

    fun applyLocale(tag: String = language) {
        val localeManager = appContext.getSystemService(LocaleManager::class.java)
        localeManager.applicationLocales = if (tag.isEmpty()) {
            LocaleList.getEmptyLocaleList()
        } else {
            LocaleList.forLanguageTags(tag)
        }
    }
}
