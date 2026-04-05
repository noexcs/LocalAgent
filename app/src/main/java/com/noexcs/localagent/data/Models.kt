package com.noexcs.localagent.data

import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.dashscope.DashscopeClientSettings
import ai.koog.prompt.executor.clients.dashscope.DashscopeLLMClient
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
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.json.JSONArray

fun stringToLLMProvider(name: String): LLMProvider {
    return when (name) {
        "DeepSeek" -> LLMProvider.DeepSeek
        "Google" -> LLMProvider.Google
        "OpenAI" -> LLMProvider.OpenAI
        "Ollama" -> LLMProvider.Ollama
        "Anthropic" -> LLMProvider.Anthropic
        "Alibaba" -> LLMProvider.Alibaba
        "Azure" -> LLMProvider.Azure
        "Bedrock" -> LLMProvider.Bedrock
        "HuggingFace" -> LLMProvider.HuggingFace
        "Meta" -> LLMProvider.Meta
        "MiniMax" -> LLMProvider.MiniMax
        "MistralAI" -> LLMProvider.MistralAI
        "OCI" -> LLMProvider.OCI
        "OpenRouter" -> LLMProvider.OpenRouter
        "Vertex" -> LLMProvider.Vertex
        "ZhipuAI" -> LLMProvider.ZhipuAI
        else -> LLMProvider.DeepSeek
    }
}

/** Helper function to convert LLMProvider to String */
fun llmProviderToString(provider: LLMProvider): String {
    return when (provider) {
        LLMProvider.DeepSeek -> "DeepSeek"
        LLMProvider.Google -> "Google"
        LLMProvider.OpenAI -> "OpenAI"
        LLMProvider.Ollama -> "Ollama"
        LLMProvider.Anthropic -> "Anthropic"
        LLMProvider.Alibaba -> "Alibaba"
        LLMProvider.Azure -> "Azure"
        LLMProvider.Bedrock -> "Bedrock"
        LLMProvider.HuggingFace -> "HuggingFace"
        LLMProvider.Meta -> "Meta"
        LLMProvider.MiniMax -> "MiniMax"
        LLMProvider.MistralAI -> "MistralAI"
        LLMProvider.OCI -> "OCI"
        LLMProvider.OpenRouter -> "OpenRouter"
        LLMProvider.Vertex -> "Vertex"
        LLMProvider.ZhipuAI -> "ZhipuAI"
        else -> "DeepSeek" // Default fallback
    }
}

/**
 * Get all available provider names
 */
fun getProviders(): List<LLMProvider> {
    return listOf(
        LLMProvider.DeepSeek,
        LLMProvider.Google,
        LLMProvider.OpenAI,
        LLMProvider.Ollama,
        LLMProvider.Anthropic,
        LLMProvider.Alibaba,
//        LLMProvider.Azure,
//        LLMProvider.Bedrock,
//        LLMProvider.HuggingFace,
//        LLMProvider.Meta,
//        LLMProvider.MiniMax,
        LLMProvider.MistralAI,
//        LLMProvider.OCI,
        LLMProvider.OpenRouter,
//        LLMProvider.Vertex,
//        LLMProvider.ZhipuAI
    )
}

fun getPredefinedProviderModels(provider: LLMProvider): List<LLModel> {
    return when (provider) {
        LLMProvider.DeepSeek -> DeepSeekModels.models
        LLMProvider.Google -> GoogleModels.models
        LLMProvider.OpenAI -> OpenAIModels.models
        LLMProvider.Ollama -> OllamaModels.models
        LLMProvider.Anthropic -> AnthropicModels.models
        LLMProvider.Alibaba -> listOf()
        LLMProvider.Azure -> listOf()
        LLMProvider.Bedrock -> listOf()
        LLMProvider.HuggingFace -> listOf()
        LLMProvider.Meta -> listOf()
        LLMProvider.MiniMax -> listOf()
        LLMProvider.MistralAI -> MistralAIModels.models
        LLMProvider.OCI -> listOf()
        LLMProvider.OpenRouter -> OpenRouterModels.models
        LLMProvider.Vertex -> listOf()
        LLMProvider.ZhipuAI -> listOf()
        else -> listOf()
    }
}

suspend fun getOnlineModels(
    context: Context,
    provider: LLMProvider,
    apiKey: String = "sk-no-key-required",
    baseUrl: String = ""
): List<LLModel> = withContext(Dispatchers.IO) {

    val models = when (provider) {
        LLMProvider.DeepSeek -> DeepSeekLLMClient(apiKey).models()
        LLMProvider.Google -> GoogleLLMClient(apiKey, GoogleClientSettings()).models()
        LLMProvider.OpenAI -> OpenAILLMClient(apiKey, OpenAIClientSettings()).models()
        LLMProvider.Ollama -> OllamaClient(baseUrl).models()
        LLMProvider.Anthropic -> AnthropicLLMClient(apiKey, AnthropicClientSettings()).models()
        LLMProvider.Alibaba -> DashscopeLLMClient(apiKey, DashscopeClientSettings()).models()
        LLMProvider.Azure -> listOf()
        LLMProvider.Bedrock -> listOf()
        LLMProvider.HuggingFace -> listOf()
        LLMProvider.Meta -> listOf()
        LLMProvider.MiniMax -> listOf()
        LLMProvider.MistralAI -> MistralAILLMClient(apiKey, MistralAIClientSettings()).models()
        LLMProvider.OCI -> listOf()
        LLMProvider.OpenRouter -> OpenRouterLLMClient(apiKey, OpenRouterClientSettings()).models()
        LLMProvider.Vertex -> listOf()
        LLMProvider.ZhipuAI -> listOf()
        else -> listOf()
    }

    // Cache the fetched models
    cacheModels(context, provider, models)

    val element = models + getCachedModels(context, provider)
    element.toSet().toList()
}


/**
 * Get cached models from persistent storage
 * Returns cached models if available, otherwise returns empty list
 */
fun getCachedModels(context: Context, provider: LLMProvider): List<LLModel> {
    val prefs = context.getSharedPreferences("model_cache", Context.MODE_PRIVATE)
    val jsonString = prefs.getString(getCacheKey(provider), null) ?: return emptyList()
    val jsonArray = JSONArray(jsonString)
    val serializer = LLModel.serializer()
    val models = mutableListOf<LLModel>()
    for (i in 0 until jsonArray.length()) {
        val modelObj = jsonArray.getString(i)
        val model = Json.decodeFromString(serializer, modelObj)
        models.add(model)
    }
    return models
}

/**
 * Cache models to persistent storage
 * Saves the list of models for later retrieval
 */
fun cacheModels(context: Context, provider: LLMProvider, models: List<LLModel>) {
    val prefs = context.getSharedPreferences("model_cache", Context.MODE_PRIVATE)
    val editor = prefs.edit()
    val serializer = LLModel.serializer()
    try {
        val jsonArray = JSONArray()
        models.forEach { model ->
            jsonArray.put(Json.encodeToString(serializer, model))
        }
        editor.putString(getCacheKey(provider), jsonArray.toString())
        editor.apply()
    } catch (e: Exception) {
        // Silently fail - caching is not critical
    }
}

private fun getCacheKey(provider: LLMProvider): String {
    return "models_cache_${llmProviderToString(provider)}"
}

suspend fun getModelById(
    context: Context,
    provider: LLMProvider,
    modelId: String,
    apiKey: String = "sk-no-key-required",
    baseUrl: String = ""
): LLModel {
    val predefinedModels = getPredefinedProviderModels(provider)
    predefinedModels.find { it.id == modelId }?.let { return it }

    val cachedModels = getCachedModels(context, provider)
    cachedModels.find { it.id == modelId }?.let { return it }
    
    // Search in online models
    val onlineModels = getOnlineModels(context, provider, apiKey, baseUrl)
    onlineModels.find { it.id == modelId }?.let { return it }
    
    return LLModel(provider, modelId)
}

