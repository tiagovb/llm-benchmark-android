package com.tiagoviana.llmbenchmark.benchmark

import com.tiagoviana.llmbenchmark.api.LLMClient
import com.tiagoviana.llmbenchmark.api.StreamEvent
import com.tiagoviana.llmbenchmark.types.RequestResult
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import java.util.concurrent.atomic.AtomicInteger

/**
 * Executes individual benchmark requests
 */
class RequestExecutor(
    private val client: LLMClient,
    private val model: String
) {
    /**
     * Execute a single benchmark request with streaming
     * Returns metrics about the request
     */
    suspend fun execute(
        requestId: Int,
        prompt: String,
        maxTokens: Int = 1024
    ): RequestResult {
        val startTime = System.currentTimeMillis()
        var firstTokenTime: Long? = null
        val tokenCount = AtomicInteger(0)
        var error: Exception? = null
        
        client.streamMessage(model, prompt, maxTokens)
            .catch { e ->
                error = e
            }
            .collect { event ->
                when (event) {
                    is StreamEvent.Token -> {
                        if (firstTokenTime == null) {
                            firstTokenTime = System.currentTimeMillis()
                        }
                        tokenCount.incrementAndGet()
                    }
                    is StreamEvent.Done -> {
                        // Finished
                    }
                    is StreamEvent.Error -> {
                        error = event.exception
                    }
                }
            }
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        val ttft = firstTokenTime?.let { it - startTime } ?: 0L
        val tokens = tokenCount.get()
        val tokensPerSec = if (duration > 0) (tokens * 1000.0 / duration) else 0.0
        
        return if (error != null) {
            RequestResult(
                id = requestId,
                ttftMs = 0,
                tokensPerSec = 0.0,
                totalTokens = 0,
                durationMs = duration,
                status = "error",
                errorDetail = error?.message
            )
        } else {
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
}
