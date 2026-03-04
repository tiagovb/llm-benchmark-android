package com.tiagoviana.llmbenchmark.benchmark

import android.util.Log
import com.tiagoviana.llmbenchmark.api.LLMClient
import com.tiagoviana.llmbenchmark.types.BenchmarkProgress
import com.tiagoviana.llmbenchmark.types.TierResult
import com.tiagoviana.llmbenchmark.types.RequestLogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Orchestrates benchmark execution with progressive concurrency
 */
class BenchmarkRunner(
    private val endpoint: String,
    private val apiKey: String,
    private val model: String,
    private val onProgress: (BenchmarkProgress) -> Unit
) {
    companion object {
        private const val TAG = "BenchmarkRunner"
    }
    
    /**
     * Run benchmark from 1 to maxTiers with progressive concurrency
     * Tier N = N concurrent requests
     */
    suspend fun runBenchmark(
        maxTiers: Int,
        prompt: String
    ): List<TierResult> = withContext(Dispatchers.IO) {
        Log.d(TAG, "=== runBenchmark START === maxTiers=$maxTiers")
        
        try {
            // Clear previous logs
            RequestLogManager.clear()
            
            val results = mutableListOf<TierResult>()
            val startTime = System.currentTimeMillis()
            
            for (tier in 1..maxTiers) {
                Log.d(TAG, "Starting tier $tier with $tier concurrent requests")
                
                try {
                    // Execute tier with N concurrent requests
                    val tierResult = executeTier(
                        concurrency = tier,
                        prompt = prompt,
                        benchmarkStart = startTime,
                        totalTiers = maxTiers
                    )
                    results.add(tierResult)
                    
                    Log.d(TAG, "Tier $tier completed: ${tierResult.successCount} success, ${tierResult.errorCount} errors")
                    
                    // Send progress update (safely)
                    try {
                        onProgress(
                            BenchmarkProgress(
                                currentTier = tier,
                                totalTiers = maxTiers,
                                concurrency = tier,
                                completedRequests = tier,
                                elapsed = System.currentTimeMillis() - startTime,
                                currentTps = tierResult.avgTokensPerSec,
                                tierResult = tierResult
                            )
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in progress callback", e)
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error executing tier $tier", e)
                    // Add an error result for this tier
                    results.add(TierResult(
                        concurrency = tier,
                        requests = emptyList(),
                        avgTTFT = 0.0,
                        avgTokensPerSec = 0.0,
                        totalTokens = 0,
                        successCount = 0,
                        errorCount = tier
                    ))
                }
            }
            
            Log.d(TAG, "=== runBenchmark COMPLETE === ${results.size} tiers")
            results
            
        } catch (e: Exception) {
            Log.e(TAG, "Benchmark failed completely", e)
            throw e
        }
    }
    
    /**
     * Execute a single tier with N concurrent requests
     * Uses supervisorScope to isolate failures
     */
    private suspend fun executeTier(
        concurrency: Int,
        prompt: String,
        benchmarkStart: Long,
        totalTiers: Int
    ): TierResult = supervisorScope {
        Log.d(TAG, "executeTier: concurrency=$concurrency")

        val tierTokenCount = AtomicInteger(0)
        val firstTokenAt = AtomicLong(0L)

        // Lightweight ticker for real-time TPS (debounced)
        val tickerJob = launch(Dispatchers.Default) {
            while (isActive) {
                try {
                    val now = System.currentTimeMillis()
                    val first = firstTokenAt.get()
                    val currentTps = if (first > 0L) {
                        val elapsedStreamingMs = maxOf(1L, now - first)
                        tierTokenCount.get() * 1000.0 / elapsedStreamingMs
                    } else {
                        0.0
                    }

                    onProgress(
                        BenchmarkProgress(
                            currentTier = concurrency,
                            totalTiers = totalTiers,
                            concurrency = concurrency,
                            completedRequests = 0,
                            elapsed = now - benchmarkStart,
                            currentTps = currentTps,
                            tierResult = null
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error in realtime TPS ticker", e)
                }
                delay(250)
            }
        }

        try {
            // Launch N concurrent requests
            val deferredResults = (1..concurrency).map { reqId ->
                async(Dispatchers.IO) {
                    try {
                        val client = LLMClient(
                            endpoint = endpoint,
                            apiKey = apiKey,
                            tier = concurrency,
                            requestId = reqId
                        )
                        val executor = RequestExecutor(client, model)
                        executor.execute(
                            requestId = reqId,
                            prompt = prompt,
                            onTokenReceived = {
                                val now = System.currentTimeMillis()
                                firstTokenAt.compareAndSet(0L, now)
                                tierTokenCount.incrementAndGet()
                            }
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Request $reqId failed", e)
                        // Return error result
                        com.tiagoviana.llmbenchmark.types.RequestResult(
                            id = reqId,
                            ttftMs = 0,
                            tokensPerSec = 0.0,
                            totalTokens = 0,
                            durationMs = 0,
                            status = "error",
                            errorDetail = "${e.javaClass.simpleName}: ${e.message}"
                        )
                    }
                }
            }

            // Wait for all requests to complete (even if some fail)
            val requestResults = try {
                deferredResults.awaitAll()
            } catch (e: Exception) {
                Log.e(TAG, "Error awaiting results", e)
                emptyList()
            }

            // Calculate aggregated metrics
            val successfulResults = requestResults.filter { it.status == "success" }
            val errorResults = requestResults.filter { it.status != "success" }

            TierResult(
                concurrency = concurrency,
                requests = requestResults,
                avgTTFT = if (successfulResults.isNotEmpty()) {
                    successfulResults.map { it.ttftMs.toDouble() }.average()
                } else 0.0,
                avgTokensPerSec = if (successfulResults.isNotEmpty()) {
                    successfulResults.map { it.tokensPerSec }.average()
                } else 0.0,
                totalTokens = successfulResults.sumOf { it.totalTokens },
                successCount = successfulResults.size,
                errorCount = errorResults.size
            )
        } finally {
            tickerJob.cancel()
        }
    }
}