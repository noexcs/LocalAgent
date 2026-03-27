package com.noexcs.localagent.data

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.dashscope.DashscopeModels
import ai.koog.prompt.executor.clients.mistralai.MistralAIModels
import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels

class SettingsManager(context: Context) {
    private val prefs = context.getSharedPreferences("agent_settings", Context.MODE_PRIVATE)
    private val appContext = context.applicationContext

    var userSystemPrompt: String
        get() = prefs.getString("user_system_prompt", "") ?: ""
        set(value) = prefs.edit().putString("user_system_prompt", value).apply()

    var providerType: String
        get() = prefs.getString("provider_type", "DeepSeek") ?: "DeepSeek"
        set(value) = prefs.edit().putString("provider_type", value).apply()

    var baseUrl: String
        get() = prefs.getString("base_url", getDefaultBaseUrl(providerType)) ?: getDefaultBaseUrl(providerType)
        set(value) = prefs.edit().putString("base_url", value).apply()

    var apiKey: String
        get() = prefs.getString("api_key", "") ?: ""
        set(value) = prefs.edit().putString("api_key", value).apply()

    var model: String
        get() = prefs.getString("model", getDefaultModel(providerType)) ?: getDefaultModel(providerType)
        set(value) = prefs.edit().putString("model", value).apply()

    /** Get default base URL for provider */
    fun getDefaultBaseUrl(provider: String): String {
        return when (provider) {
            "DeepSeek" -> "https://api.deepseek.com"
            "Anthropic" -> "https://api.anthropic.com"
            "OpenAI" -> "https://api.openai.com"
            "Google" -> "https://generativelanguage.googleapis.com"
            "Dashscope" -> "https://dashscope.aliyuncs.com"
            "Mistral AI" -> "https://api.mistral.ai"
            "Ollama" -> "http://localhost:11434"
            "OpenRouter" -> "https://openrouter.ai/api"
            else -> "https://api.deepseek.com"
        }
    }

    /** Get default model for provider */
    fun getDefaultModel(provider: String): String {
        return when (provider) {
            "DeepSeek" -> DeepSeekModels.models.first().id
            "Anthropic" -> AnthropicModels.models.first().id
            "OpenAI" -> OpenAIModels.models.first().id
            "Google" -> GoogleModels.models.first().id
            "Dashscope" -> DashscopeModels.models.first().id
            "Mistral AI" -> MistralAIModels.models.first().id
            "Ollama" -> OllamaModels.models.first().id
            "OpenRouter" -> OpenRouterModels.models.first().id
            else -> DeepSeekModels.models.first().id
        }
    }

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
