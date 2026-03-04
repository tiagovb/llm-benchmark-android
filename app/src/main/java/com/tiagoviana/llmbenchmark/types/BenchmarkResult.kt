package com.tiagoviana.llmbenchmark.types

/**
 * Result of a single benchmark request
 */
data class RequestResult(
    val id: Int,
    val ttftMs: Long,
    val tokensPerSec: Double,
    val totalTokens: Int,
    val durationMs: Long,
    val status: String,
    val errorDetail: String? = null
)

/**
 * Result of a complete tier (N concurrent requests)
 */
data class TierResult(
    val concurrency: Int,
    val requests: List<RequestResult>,
    val avgTTFT: Double,
    val avgTokensPerSec: Double,
    val totalTokens: Int,
    val successCount: Int,
    val errorCount: Int
)

/**
 * Complete benchmark data
 */
data class BenchmarkData(
    val timestamp: Long,
    val model: String,
    val endpoint: String,
    val tiers: List<TierResult>
)

/**
 * Real-time progress information
 */
data class BenchmarkProgress(
    val currentTier: Int,
    val totalTiers: Int,
    val concurrency: Int,
    val completedRequests: Int,
    val elapsed: Long,
    val currentTps: Double = 0.0,
    val tierResult: TierResult? = null
)

/**
 * UI State
 */
data class BenchmarkState(
    val endpoint: String = "https://api.z.ai/api/anthropic",
    val apiKey: String = "",
    val model: String = "glm-5",
    val maxTiers: Int = 10,
    val isRunning: Boolean = false,
    val currentTier: Int = 0,
    val completedRequests: Int = 0,
    val elapsed: Long = 0,
    val currentTps: Double = 0.0,
    val results: List<TierResult> = emptyList(),
    val error: String? = null
)
