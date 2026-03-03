package com.tiagoviana.llmbenchmark.types

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Collections

/**
 * Log entry for a single request
 */
data class RequestLog(
    val timestamp: Long = System.currentTimeMillis(),
    val tier: Int,
    val requestId: Int,
    val level: LogLevel,
    val message: String,
    val details: String? = null
) {
    enum class LogLevel {
        INFO, SUCCESS, ERROR, WARNING
    }
    
    fun formattedTime(): String {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        return sdf.format(Date(timestamp))
    }
    
    fun formattedLog(): String {
        val prefix = when (level) {
            LogLevel.INFO -> "INFO"
            LogLevel.SUCCESS -> "SUCCESS"
            LogLevel.ERROR -> "ERROR"
            LogLevel.WARNING -> "WARNING"
        }
        return "${formattedTime()} [T${tier}#${requestId}] [$prefix] $message" + 
            (details?.let { "\n  $it" } ?: "")
    }
}

/**
 * Thread-safe Manager for collecting and exposing request logs
 */
object RequestLogManager {
    private val _logs = Collections.synchronizedList(mutableListOf<RequestLog>())
    val logs: List<RequestLog> 
        get() = synchronized(_logs) { _logs.toList() }
    
    fun log(
        tier: Int,
        requestId: Int,
        level: RequestLog.LogLevel,
        message: String,
        details: String? = null
    ) {
        val entry = RequestLog(
            tier = tier,
            requestId = requestId,
            level = level,
            message = message,
            details = details
        )
        _logs.add(entry)
    }
    
    fun clear() {
        _logs.clear()
    }
    
    fun getFullLogText(): String {
        return logs.joinToString("\n") { it.formattedLog() }
    }
}