package com.tiagoviana.llmbenchmark.benchmark

import android.util.Log
import com.tiagoviana.llmbenchmark.api.LLMClient
import com.tiagoviana.llmbenchmark.api.StreamEvent
import com.tiagoviana.llmbenchmark.types.RequestResult
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Executes individual benchmark requests
 * Thread-safe and crash-resistant
 */
class RequestExecutor(
    private val client: LLMClient,
    private val model: String
) {
    companion object {
        private const val TAG = "RequestExecutor"
    }
    
    /**
     * Execute a single benchmark request with streaming
     * Returns metrics about the request
     */
    suspend fun execute(
        requestId: Int,
        prompt: String,
        maxTokens: Int = 1024,
        onTokenReceived: () -> Unit = {}
    ): RequestResult {
        Log.d(TAG, "=== execute START requestId=$requestId ===")
        
        val startTime = System.currentTimeMillis()
        val firstTokenTime = AtomicLong(0)
        val tokenCount = AtomicInteger(0)
        val error = AtomicReference<Throwable?>(null)
        val completed = AtomicBoolean(false)
        
        try {
            client.streamMessage(model, prompt, maxTokens)
                .catch { e ->
                    Log.e(TAG, "Flow error for request $requestId", e)
                    error.set(e)
                    completed.set(true)
                }
                .collect { event ->
                    try {
                        when (event) {
                            is StreamEvent.Token -> {
                                if (firstTokenTime.get() == 0L) {
                                    firstTokenTime.set(System.currentTimeMillis())
                                }
                                tokenCount.incrementAndGet()
                                onTokenReceived()
                            }
                            is StreamEvent.Done -> {
                                Log.d(TAG, "Request $requestId completed with ${tokenCount.get()} tokens")
                                completed.set(true)
                            }
                            is StreamEvent.Error -> {
                                Log.e(TAG, "Request $requestId error: ${event.exception?.message}")
                                error.set(event.exception)
                                completed.set(true)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing event for request $requestId", e)
                        error.set(e)
                        completed.set(true)
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Exception collecting flow for request $requestId", e)
            error.set(e)
        }
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        val ttft = if (firstTokenTime.get() > 0) firstTokenTime.get() - startTime else 0L
        val tokens = tokenCount.get()
        val tokensPerSec = if (duration > 0) (tokens * 1000.0 / duration) else 0.0
        
        val resultError = error.get()
        
        return if (resultError != null) {
            Log.d(TAG, "Request $requestId failed: ${resultError.message}")
            RequestResult(
                id = requestId,
                ttftMs = 0,
                tokensPerSec = 0.0,
                totalTokens = 0,
                durationMs = duration,
                status = "error",
                errorDetail = "${resultError.javaClass.simpleName}: ${resultError.message}"
            )
        } else {
            Log.d(TAG, "Request $requestId success: $tokens tokens, $tokensPerSec tks/s")
            RequestResult(
                id = requestId,
                ttftMs = ttft,
                tokensPerSec = tokensPerSec,
                totalTokens = tokens,
                durationMs = duration,
                status = "success"
            )
        }
    }
    
    // Helper class for atomic long with default value
    private class AtomicLong(initialValue: Long = 0) {
        private var value = initialValue
        
        @Synchronized
        fun get(): Long = value
        
        @Synchronized
        fun set(newValue: Long) {
            value = newValue
        }
    }
    
    // Helper class for atomic reference
    private class AtomicReference<T>(initialValue: T?) {
        private var value = initialValue
        
        @Synchronized
        fun get(): T? = value
        
        @Synchronized
        fun set(newValue: T?) {
            value = newValue
        }
    }
}