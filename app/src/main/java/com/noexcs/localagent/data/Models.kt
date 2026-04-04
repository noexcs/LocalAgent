package com.noexcs.localagent.data

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.dashscope.DashscopeClientSettings
import ai.koog.prompt.executor.clients.dashscope.DashscopeLLMClient
import ai.koog.prompt.executor.clients.dashscope.DashscopeModels
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.executor.clients.google.GoogleClientSettings
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.mistralai.MistralAIClientSettings
import ai.koog.prompt.executor.clients.mistralai.MistralAILLMClient
import ai.koog.prompt.executor.clients.mistralai.MistralAIModels
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.openrouter.OpenRouterClientSettings
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.prompt.llm.LLModel
import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Get all available provider names
 */
fun getProviders(): List<String> {
    return listOf("DeepSeek", "Anthropic", "OpenAI", "Google", "Dashscope", "Mistral AI", "Ollama", "OpenRouter")
}

/**
 * Get local (offline) models for a provider from static Models constants
 */
fun getLocalModels(provider: String): List<LLModel> {
    return when (provider) {
        "DeepSeek" -> DeepSeekModels.models
        "Anthropic" -> AnthropicModels.models
        "OpenAI" -> OpenAIModels.models
        "Google" -> GoogleModels.models
        "Dashscope" -> DashscopeModels.models
        "Mistral AI" -> MistralAIModels.models
        "Ollama" -> OllamaModels.models
        "OpenRouter" -> OpenRouterModels.models
        else -> throw IllegalArgumentException("Invalid provider: $provider")
    }
}

/**
 * Get online models for a provider by making network request
 * This function fetches models from the API and caches them
 */
suspend fun getOnlineModels(
    context: Context,
    provider: String,
    apiKey: String,
    baseUrl: String = ""
): List<LLModel> = withContext(Dispatchers.IO) {
    try {
        val models = when (provider) {
            "DeepSeek" -> DeepSeekLLMClient(apiKey).models()
            "Anthropic" -> AnthropicLLMClient(apiKey, AnthropicClientSettings()).models()
            "OpenAI" -> OpenAILLMClient(apiKey, OpenAIClientSettings()).models()
            "Google" -> GoogleLLMClient(apiKey, GoogleClientSettings()).models()
            "Dashscope" -> DashscopeLLMClient(apiKey, DashscopeClientSettings()).models()
            "Mistral AI" -> MistralAILLMClient(apiKey, MistralAIClientSettings()).models()
            "Ollama" -> OllamaClient(baseUrl).models()
            "OpenRouter" -> OpenRouterLLMClient(apiKey, OpenRouterClientSettings()).models()
            else -> DeepSeekLLMClient(apiKey).models()
        }
        
        // Cache the fetched models
        cacheModels(context, provider, models)
        
        models
    } catch (e: Exception) {
        emptyList()
    }
}

/**
 * Get cached models from persistent storage
 * Returns cached models if available, otherwise returns empty list
 */
fun getCachedModels(context: Context, provider: String): List<LLModel> {
    val prefs = context.getSharedPreferences("model_cache", Context.MODE_PRIVATE)
    val modelsJson = prefs.getString(getCacheKey(provider), null) ?: return emptyList()
    
    return try {
        parseModelsFromJson(modelsJson)
    } catch (e: Exception) {
        emptyList()
    }
}

/**
 * Cache models to persistent storage
 * Saves the list of models for later retrieval
 */
fun cacheModels(context: Context, provider: String, models: List<LLModel>) {
    val prefs = context.getSharedPreferences("model_cache", Context.MODE_PRIVATE)
    val editor = prefs.edit()
    
    try {
        val jsonArray = JSONArray()
        models.forEach { model ->
            val modelObj = JSONObject().apply {
                put("id", model.id)
                put("provider", model.provider.toString())
                put("contextLength", (model.contextLength ?: 8192L).toLong())
                put("maxOutputTokens", (model.maxOutputTokens ?: 4096L).toLong())
            }
            jsonArray.put(modelObj)
        }
        
        editor.putString(getCacheKey(provider), jsonArray.toString())
        editor.apply()
    } catch (e: Exception) {
        // Silently fail - caching is not critical
    }
}

/**
 * Get cache key for a provider
 */
private fun getCacheKey(provider: String): String {
    return "models_cache_$provider"
}

/**
 * Parse LLModel list from JSON string
 */
private fun parseModelsFromJson(jsonString: String): List<LLModel> {
    val jsonArray = JSONArray(jsonString)
    val models = mutableListOf<LLModel>()
    
    for (i in 0 until jsonArray.length()) {
        val modelObj = jsonArray.getJSONObject(i)
        val id = modelObj.getString("id")
        val providerName = modelObj.getString("provider")
        val contextLength = modelObj.optLong("contextLength", 8192L)
        val maxOutputTokens = modelObj.optLong("maxOutputTokens", 4096L)
        
        // Create LLModel from parsed data
        val provider = ai.koog.prompt.llm.LLMProvider.DeepSeek // Default fallback
        
        models.add(
            LLModel(
                id = id,
                provider = provider,
                contextLength = contextLength,
                maxOutputTokens = maxOutputTokens
            )
        )
    }
    
    return models
}

/**
 * Get all available models for a provider
 * Priority: Local > Cached > Online (with fallback)
 */
suspend fun getAllModels(
    context: Context,
    provider: String,
    apiKey: String,
    baseUrl: String = ""
): List<LLModel> {
    // First try local models (fastest, always available)
    val localModels = getLocalModels(provider)
    if (localModels.isNotEmpty()) {
        return localModels
    }
    
    // Then try cached models (fast, may be stale)
    val cachedModels = getCachedModels(context, provider)
    if (cachedModels.isNotEmpty()) {
        return cachedModels
    }
    
    // Finally fetch from network (slow, most up-to-date)
    return getOnlineModels(context, provider, apiKey, baseUrl)
}

/**
 * Get a specific model by ID
 * Searches in local, cached, and online models in that order
 */
suspend fun getModelById(
    context: Context,
    provider: String,
    modelId: String,
    apiKey: String,
    baseUrl: String = ""
): LLModel? {
    // Search in local models first
    val localModels = getLocalModels(provider)
    localModels.find { it.id == modelId }?.let { return it }
    
    // Search in cached models
    val cachedModels = getCachedModels(context, provider)
    cachedModels.find { it.id == modelId }?.let { return it }
    
    // Search in online models
    val onlineModels = getOnlineModels(context, provider, apiKey, baseUrl)
    onlineModels.find { it.id == modelId }?.let { return it }
    
    return null
}

/**
 * Get default model for a provider
 * Returns the first available model from local list
 */
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

