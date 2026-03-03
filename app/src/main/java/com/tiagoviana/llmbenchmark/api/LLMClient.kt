package com.tiagoviana.llmbenchmark.api

import android.util.Log
import com.tiagoviana.llmbenchmark.types.RequestLog
import com.tiagoviana.llmbenchmark.types.RequestLogManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSourceListener
import com.google.gson.Gson
import java.net.URL

/**
 * HTTP client for LLM API with SSE streaming
 * Thread-safe and crash-resistant
 */
class LLMClient(
    private val endpoint: String,
    private val apiKey: String,
    private val tier: Int = 0,
    private val requestId: Int = 0
) {
    companion object {
        private const val TAG = "LLMClient"
    }
    
    private val client = SharedOkHttpClient.instance
    private val gson = Gson()
    
    private fun log(level: RequestLog.LogLevel, message: String, details: String? = null) {
        try {
            RequestLogManager.log(tier, requestId, level, message, details)
            Log.d(TAG, "[T$tier#$requestId] $message ${details ?: ""}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log: ${e.message}")
        }
    }
    
    /**
     * Stream message response with real-time token events
     * Returns a Flow that emits StreamEvent objects
     */
    fun streamMessage(
        model: String,
        prompt: String,
        maxTokens: Int = 1024
    ): Flow<StreamEvent> = callbackFlow {
        Log.d(TAG, "=== streamMessage START ===")
        
        var eventSource: okhttp3.sse.EventSource? = null
        var tokenCount = 0
        var closed = false
        
        try {
            // Validate endpoint
            try {
                URL(endpoint)
            } catch (e: Exception) {
                val error = "Invalid endpoint URL: $endpoint"
                log(RequestLog.LogLevel.ERROR, error, e.message)
                trySendBlocking(StreamEvent.Error(IllegalArgumentException(error)))
                close()
                return@callbackFlow
            }
            
            // Build request
            val request = MessageRequest(
                model = model,
                messages = listOf(Message(content = prompt)),
                max_tokens = maxTokens,
                stream = true
            )
            
            val json = try {
                gson.toJson(request)
            } catch (e: Exception) {
                val error = "Failed to serialize request"
                log(RequestLog.LogLevel.ERROR, error, e.message)
                trySendBlocking(StreamEvent.Error(e))
                close()
                return@callbackFlow
            }
            
            log(RequestLog.LogLevel.INFO, "Building request", 
                "URL: $endpoint\nModel: $model\nMaxTokens: $maxTokens")
            log(RequestLog.LogLevel.INFO, "Request body", json)
            
            val httpRequest = try {
                Request.Builder()
                    .url(endpoint)
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .post(json.toRequestBody("application/json".toMediaType()))
                    .build()
            } catch (e: Exception) {
                val error = "Failed to build HTTP request"
                log(RequestLog.LogLevel.ERROR, error, e.message)
                trySendBlocking(StreamEvent.Error(e))
                close()
                return@callbackFlow
            }
            
            log(RequestLog.LogLevel.INFO, "Request headers", 
                "Content-Type: application/json\nx-api-key: ${apiKey.take(10)}...\nanthropic-version: 2023-06-01")
            
            // Create SSE listener
            val listener = object : EventSourceListener() {
                
                override fun onOpen(eventSource: okhttp3.sse.EventSource, response: okhttp3.Response) {
                    if (closed) return
                    try {
                        log(RequestLog.LogLevel.INFO, "Connection opened", 
                            "HTTP ${response.code} ${response.message}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in onOpen", e)
                    }
                }
                
                override fun onEvent(
                    eventSource: okhttp3.sse.EventSource,
                    id: String?,
                    type: String?,
                    data: String
                ) {
                    if (closed) return
                    try {
                        log(RequestLog.LogLevel.INFO, "SSE Event", "type=$type")
                        
                        when (type) {
                            "content_block_delta" -> {
                                try {
                                    val delta = gson.fromJson(data, StreamDelta::class.java)
                                    delta?.delta?.text?.let { text ->
                                        tokenCount++
                                        trySendBlocking(StreamEvent.Token(text))
                                    }
                                } catch (e: Exception) {
                                    log(RequestLog.LogLevel.WARNING, "Parse error", e.message)
                                }
                            }
                            "message_start" -> {
                                log(RequestLog.LogLevel.INFO, "Message started", null)
                            }
                            "message_delta" -> {
                                log(RequestLog.LogLevel.INFO, "Message delta", null)
                            }
                            "message_stop" -> {
                                log(RequestLog.LogLevel.SUCCESS, "Message complete", 
                                    "Total tokens: $tokenCount")
                                trySendBlocking(StreamEvent.Done(null))
                                closed = true
                                close()
                            }
                            "error" -> {
                                log(RequestLog.LogLevel.ERROR, "API Error", data)
                                trySendBlocking(StreamEvent.Error(Exception(data)))
                                closed = true
                                close()
                            }
                            else -> {
                                log(RequestLog.LogLevel.INFO, "Event: $type", null)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing event", e)
                    }
                }
                
                override fun onClosed(eventSource: okhttp3.sse.EventSource) {
                    if (closed) return
                    try {
                        log(RequestLog.LogLevel.INFO, "Connection closed", null)
                        closed = true
                        close()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in onClosed", e)
                    }
                }
                
                override fun onFailure(
                    eventSource: okhttp3.sse.EventSource,
                    t: Throwable?,
                    response: okhttp3.Response?
                ) {
                    if (closed) return
                    try {
                        val errorDetails = StringBuilder()
                        errorDetails.append("Connection failed\n")
                        t?.let { 
                            errorDetails.append("Exception: ${it.javaClass.simpleName}: ${it.message}\n") 
                        }
                        response?.let { 
                            errorDetails.append("HTTP ${it.code}: ${it.message}\n")
                            try {
                                val body = it.peekBody(2048).string()
                                errorDetails.append("Body: $body")
                            } catch (e: Exception) {
                                errorDetails.append("Could not read body")
                            }
                        }
                        
                        log(RequestLog.LogLevel.ERROR, errorDetails.toString(), null)
                        
                        val error = t ?: Exception("HTTP ${response?.code}: ${response?.message}")
                        trySendBlocking(StreamEvent.Error(error))
                        closed = true
                        close()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in onFailure", e)
                    }
                }
            }
            
            // Create event source
            eventSource = okhttp3.sse.EventSources.createFactory(client)
                .newEventSource(httpRequest, listener)
            
            log(RequestLog.LogLevel.INFO, "Request sent, waiting for response...", null)
            
        } catch (e: Exception) {
            if (!closed) {
                log(RequestLog.LogLevel.ERROR, "Exception during request", 
                    "${e.javaClass.simpleName}: ${e.message}")
                try {
                    trySendBlocking(StreamEvent.Error(e))
                } catch (sendError: Exception) {
                    Log.e(TAG, "Error sending exception", sendError)
                }
                closed = true
                close()
            }
        }
        
        awaitClose {
            Log.d(TAG, "Flow closed, cancelling event source")
            closed = true
            try {
                eventSource?.cancel()
            } catch (e: Exception) {
                Log.e(TAG, "Error cancelling event source", e)
            }
        }
    }
}