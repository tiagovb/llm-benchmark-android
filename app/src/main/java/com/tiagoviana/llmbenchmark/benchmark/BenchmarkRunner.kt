package com.tiagoviana.llmbenchmark.benchmark

import com.tiagoviana.llmbenchmark.api.LLMClient
import com.tiagoviana.llmbenchmark.types.BenchmarkProgress
import com.tiagoviana.llmbenchmark.types.TierResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

/**
 * Orchestrates benchmark execution with progressive concurrency
 */
class BenchmarkRunner(
    private val client: LLMClient,
    private val model: String,
    private val onProgress: (BenchmarkProgress) -> Unit
) {
    /**
     * Run benchmark from 1 to maxTiers with progressive concurrency
     * Tier N = N concurrent requests
     */
    suspend fun runBenchmark(
        maxTiers: Int,
        prompt: String
    ): List<TierResult> = withContext(Dispatchers.IO) {
        
        val results = mutableListOf<TierResult>()
        val startTime = System.currentTimeMillis()
        
        for (tier in 1..maxTiers) {
            // Execute tier with N concurrent requests
            val tierResult = executeTier(tier, prompt, startTime)
            results.add(tierResult)
            
            // Send progress update
            onProgress(
                BenchmarkProgress(
                    currentTier = tier,
                    totalTiers = maxTiers,
                    concurrency = tier,
                    completedRequests = tier,
                    elapsed = System.currentTimeMillis() - startTime,
                    tierResult = tierResult
                )
            )
        }
        
        results
    }
    
    /**
     * Execute a single tier with N concurrent requests
     */
    private suspend fun executeTier(
        concurrency: Int,
        prompt: String,
        benchmarkStart: Long
    ): TierResult = withContext(Dispatchers.IO) {
        
        val executor = RequestExecutor(client, model)
        
        // Launch N concurrent requests
        val deferredResults = (1..concurrency).map { reqId ->
            async {
                executor.execute(reqId, prompt)
            }
        }
        
        // Wait for all requests to complete
        val requestResults = deferredResults.awaitAll()
        
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
    }
}
