package com.noexcs.localagent.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.BufferedReader
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OpenAiClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun chat(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>? = null
    ): ChatResponse {
        val request = ChatRequest(
            model = model,
            messages = messages,
            tools = tools?.ifEmpty { null }
        )

        val body = json.encodeToString(request)
            .toRequestBody("application/json".toMediaType())

        val httpRequest = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        val responseBody = suspendCancellableCoroutine { cont ->
            val call = client.newCall(httpRequest)
            cont.invokeOnCancellation { call.cancel() }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val responseStr = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        val error = try {
                            json.decodeFromString<ApiError>(responseStr)
                        } catch (_: Exception) { null }
                        cont.resumeWithException(
                            IOException("API error ${response.code}: ${error?.error?.message ?: responseStr}")
                        )
                        return
                    }
                    cont.resume(responseStr)
                }
            })
        }

        return withContext(Dispatchers.Default) {
            json.decodeFromString<ChatResponse>(responseBody)
        }
    }

    fun chatStream(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>? = null
    ): Flow<StreamChunk> = callbackFlow {
        val request = ChatRequest(
            model = model,
            messages = messages,
            tools = tools?.ifEmpty { null },
            stream = true
        )

        val body = json.encodeToString(request)
            .toRequestBody("application/json".toMediaType())

        val httpRequest = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        val call = client.newCall(httpRequest)

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                close(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    val responseStr = response.body?.string() ?: ""
                    val error = try {
                        json.decodeFromString<ApiError>(responseStr)
                    } catch (_: Exception) { null }
                    close(IOException("API error ${response.code}: ${error?.error?.message ?: responseStr}"))
                    return
                }

                try {
                    val reader: BufferedReader = response.body!!.source().inputStream().bufferedReader()
                    reader.useLines { lines ->
                        for (line in lines) {
                            if (line.startsWith("data: ")) {
                                val data = line.removePrefix("data: ").trim()
                                if (data == "[DONE]") break
                                try {
                                    val chunk = json.decodeFromString<StreamChunk>(data)
                                    trySend(chunk)
                                } catch (_: Exception) {
                                    // skip malformed chunks
                                }
                            }
                        }
                    }
                    close()
                } catch (e: Exception) {
                    close(e)
                }
            }
        })

        awaitClose { call.cancel() }
    }
}
