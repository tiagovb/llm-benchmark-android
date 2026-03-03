package com.tiagoviana.llmbenchmark.persistence

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.tiagoviana.llmbenchmark.types.BenchmarkData
import com.tiagoviana.llmbenchmark.types.TierResult
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Export benchmark results to CSV format
 */
class CSVExporter(private val context: Context) {
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
    
    /**
     * Export benchmark data to CSV file
     * Returns URI to the created file
     */
    fun export(data: BenchmarkData): Uri? {
        return try {
            val fileName = "benchmark_${dateFormat.format(Date(data.timestamp))}.csv"
            val file = File(context.filesDir, fileName)
            
            file.printWriter().use { writer ->
                // Header
                writer.println("tier,req_id,ttft_ms,tokens_per_sec,total_tokens,duration_ms,status,error_detail")
                
                // Data rows
                data.tiers.forEach { tier ->
                    tier.requests.forEach { req ->
                        writer.println(
                            "${tier.concurrency}," +
                            "${req.id}," +
                            "${req.ttftMs}," +
                            "${String.format("%.2f", req.tokensPerSec)}," +
                            "${req.totalTokens}," +
                            "${req.durationMs}," +
                            "${req.status}," +
                            "${req.errorDetail ?: ""}"
                        )
                    }
                    
                    // Aggregated summary row per tier
                    writer.println(
                        "${tier.concurrency}," +
                        "summary," +
                        "${String.format("%.2f", tier.avgTTFT)}," +
                        "${String.format("%.2f", tier.avgTokensPerSec)}," +
                        "${tier.totalTokens}," +
                        "0," +
                        "success=${tier.successCount},error=${tier.errorCount}," +
                        ""
                    )
                }
            }
            
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            null
        }
    }
}
