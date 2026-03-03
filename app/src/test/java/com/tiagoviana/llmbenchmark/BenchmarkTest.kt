package com.tiagoviana.llmbenchmark

import org.junit.Test
import kotlinx.coroutines.runBlocking
import com.tiagoviana.llmbenchmark.benchmark.BenchmarkRunner
import org.junit.Assert.*

class BenchmarkTest {
    @Test
    fun testBenchmarkRunner() = runBlocking {
        try {
            val runner = BenchmarkRunner(
                endpoint = "https://api.openai.com/v1/chat/completions",
                apiKey = "test-key",
                model = "gpt-3.5-turbo",
                onProgress = { progress ->
                    println("Progress: ${progress.currentTier}")
                }
            )
            
            val results = runner.runBenchmark(
                maxTiers = 2,
                prompt = "Test prompt"
            )
            
            assertNotNull(results)
            assertEquals(2, results.size)
            println("Test passed")
        } catch (e: Exception) {
            e.printStackTrace()
            fail("Exception thrown: ${e.message}")
        }
    }
}