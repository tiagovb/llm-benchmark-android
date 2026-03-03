package com.tiagoviana.llmbenchmark.api

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import com.google.gson.Gson
import java.util.concurrent.TimeUnit

/**
 * Simple HTTP client for LLM API with SSE streaming
 */
class LLMClient(
    private val endpoint: String,
    private val apiKey: String
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    
    /**
     * Stream message response with real-time token events
     */
    fun streamMessage(
        model: String,
        prompt: String,
        maxTokens: Int = 1024
    ): Flow<StreamEvent> = callbackFlow {
        
        val request = MessageRequest(
            model = model,
            messages = listOf(Message(content = prompt)),
            max_tokens = maxTokens,
            stream = true
        )
        
        val json = gson.toJson(request)
        
        val httpRequest = Request.Builder()
            .url(endpoint)
            .header("Content-Type", "application/json")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()
        
        var eventSink: okhttp3.EventSource? = null
        
        try {
            val eventSourceFactory = okhttp3.sse.EventSources.createFactory(client)
            val eventSourceListener = object : okhttp3.sse.EventSourceListener() {
                
                override fun onEvent(
                    eventSource: okhttp3.EventSource,
                    id: String?,
                    type: String?,
                    data: String
                ) {
                    when (type) {
                        "content_block_delta" -> {
                            try {
                                val delta = gson.fromJson(data, StreamDelta::class.java)
                                delta.delta?.text?.let { text ->
                                    trySend(StreamEvent.Token(text))
                                }
                            } catch (e: Exception) {
                                // Skip parse errors
                            }
                        }
                        "message_stop" -> {
                            trySend(StreamEvent.Done(null))
                            close()
                        }
                        "error" -> {
                            trySend(StreamEvent.Error(Exception(data)))
                            close()
                        }
                    }
                }
                
                override fun onClosed(eventSource: okhttp3.EventSource) {
                    close()
                }
                
                override fun onFailure(
                    eventSource: okhttp3.EventSource,
                    t: Throwable?,
                    response: okhttp3.Response?
                ) {
                    val error = t ?: Exception("HTTP ${response?.code}: ${response?.message}")
                    trySend(StreamEvent.Error(error as Exception))
                    close()
                }
            }
            
            eventSink = eventSourceFactory.newEventSource(httpRequest, eventSourceListener)
            
        } catch (e: Exception) {
            trySend(StreamEvent.Error(e))
            close()
        }
        
        awaitClose {
            eventSink?.cancel()
        }
    }
}
