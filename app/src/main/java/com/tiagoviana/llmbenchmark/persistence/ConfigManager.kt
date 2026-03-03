package com.tiagoviana.llmbenchmark.persistence

import android.content.Context

/**
 * Simple plain-text configuration manager using SharedPreferences
 * NO encryption, NO complexity - just plain text storage
 */
class ConfigManager(context: Context) {
    
    private val prefs = context.getSharedPreferences(
        "llm_benchmark_config",
        Context.MODE_PRIVATE
    )
    
    // API Key - plain text, no drama
    fun saveAPIKey(key: String) {
        prefs.edit().putString("api_key", key).apply()
    }
    
    fun getAPIKey(): String? {
        return prefs.getString("api_key", null)
    }
    
    // API Endpoint
    fun saveEndpoint(url: String) {
        prefs.edit().putString("endpoint", url).apply()
    }
    
    fun getEndpoint(): String {
        return prefs.getString("endpoint", "https://api.z.ai/api/anthropic")!!
    }
    
    // Model selection
    fun saveModel(model: String) {
        prefs.edit().putString("model", model).apply()
    }
    
    fun getModel(): String {
        return prefs.getString("model", "glm-5")!!
    }
    
    // Max tiers (1-20)
    fun saveMaxTiers(tiers: Int) {
        prefs.edit().putInt("max_tiers", tiers.coerceIn(1, 20)).apply()
    }
    
    fun getMaxTiers(): Int {
        return prefs.getInt("max_tiers", 10)
    }
    
    // Prompt template
    fun savePrompt(prompt: String) {
        prefs.edit().putString("prompt", prompt).apply()
    }
    
    fun getPrompt(): String {
        return prefs.getString("prompt", DEFAULT_PROMPT)!!
    }
    
    companion object {
        const val DEFAULT_PROMPT = """
            Write a comprehensive technical essay about distributed systems architecture.
            
            Cover the following topics with depth:
            1. Fundamental concepts (CAP theorem, eventual consistency)
            2. Communication patterns (message queues, event sourcing)
            3. Scalability strategies (sharding, replication)
            4. Resilience challenges (circuit breaker, retry patterns)
            
            Provide practical examples and production considerations for each topic.
        """.trimIndent()
    }
}
