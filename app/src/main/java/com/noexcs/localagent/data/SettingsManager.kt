package com.noexcs.localagent.data

import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.core.content.edit
import kotlinx.serialization.json.Json


class SettingsManager(context: Context) {
    private val prefs = context.getSharedPreferences("agent_settings", Context.MODE_PRIVATE)
    private val appContext = context.applicationContext

    var userSystemPrompt: String
        get() = prefs.getString("user_system_prompt", "") ?: ""
        set(value) = prefs.edit { putString("user_system_prompt", value) }

    var providerType: LLMProvider?
        get() {
            val stringValue = prefs.getString("provider_type", "DeepSeek") ?: "DeepSeek"
            return stringToLLMProvider(stringValue)
        }
        set(value) {
            if (value == null)
                return
            val stringValue = llmProviderToString(value)
            prefs.edit { putString("provider_type", stringValue) }
        }

    var baseUrl: String?
        get() = prefs.getString("base_url", "")
        set(value) = prefs.edit { putString("base_url", value) }

    var apiKey: String?
        get() = prefs.getString("api_key", "")
        set(value) = prefs.edit { putString("api_key", value) }

    var model: String?
        get() {
            return prefs.getString("model", "")
        }
        set(value) {
            prefs.edit { putString("model", value) }
        }

    /** Helper function to convert String to LLMProvider */


    private fun stringToModel(modelId: String): LLModel {
        return Json.decodeFromString<LLModel>(modelId)
    }

    private fun modelToString(model: LLModel): String {
        return Json.encodeToString( model)
    }

    /** Language tag: "" = system default, "en" = English, "zh-Hans" = Simplified Chinese */
    var language: String
        get() = prefs.getString("language", "") ?: ""
        set(value) {
            prefs.edit { putString("language", value) }
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
