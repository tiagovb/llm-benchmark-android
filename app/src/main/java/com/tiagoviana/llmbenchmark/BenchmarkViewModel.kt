package com.tiagoviana.llmbenchmark

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tiagoviana.llmbenchmark.benchmark.BenchmarkRunner
import com.tiagoviana.llmbenchmark.persistence.CSVExporter
import com.tiagoviana.llmbenchmark.persistence.ConfigManager
import com.tiagoviana.llmbenchmark.types.BenchmarkData
import com.tiagoviana.llmbenchmark.types.BenchmarkState
import com.tiagoviana.llmbenchmark.types.RequestLog
import com.tiagoviana.llmbenchmark.types.RequestLogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

class BenchmarkViewModel(application: Application) : AndroidViewModel(application) {
    
    companion object {
        private const val TAG = "BenchmarkViewModel"
    }
    
    private val configManager = ConfigManager(application)
    private val csvExporter = CSVExporter(application)
    
    private val _state = MutableStateFlow(
        BenchmarkState(
            endpoint = configManager.getEndpoint(),
            apiKey = configManager.getAPIKey() ?: "",
            model = configManager.getModel(),
            maxTiers = configManager.getMaxTiers()
        )
    )
    val state: StateFlow<BenchmarkState> = _state.asStateFlow()
    
    private val _logs = MutableStateFlow<List<RequestLog>>(emptyList())
    val logs: StateFlow<List<RequestLog>> = _logs.asStateFlow()
    
    private var logUpdateJob: Job? = null
    private var benchmarkJob: Job? = null
    
    init {
        Log.d(TAG, "ViewModel initialized")
        
        // Check for previous crashes
        val prefs = application.getSharedPreferences("crash_prefs", android.content.Context.MODE_PRIVATE)
        val lastCrash = prefs.getString("last_crash", null)
        if (lastCrash != null) {
            _state.value = _state.value.copy(
                error = "ÚLTIMO CRASH DO APP:\n\n$lastCrash"
            )
            prefs.edit().remove("last_crash").apply()
        }
        
        startLogPolling()
    }
    
    private fun startLogPolling() {
        logUpdateJob = viewModelScope.launch {
            while (isActive) {
                try {
                    val currentLogs = RequestLogManager.logs
                    if (currentLogs != _logs.value) {
                        _logs.value = currentLogs
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error polling logs", e)
                }
                delay(200)
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel cleared")
        logUpdateJob?.cancel()
        benchmarkJob?.cancel()
    }
    
    fun updateEndpoint(endpoint: String) {
        Log.d(TAG, "Updating endpoint: $endpoint")
        configManager.saveEndpoint(endpoint)
        _state.value = _state.value.copy(endpoint = endpoint)
    }
    
    fun updateApiKey(key: String) {
        Log.d(TAG, "Updating API key")
        configManager.saveAPIKey(key)
        _state.value = _state.value.copy(apiKey = key)
    }
    
    fun updateModel(model: String) {
        Log.d(TAG, "Updating model: $model")
        configManager.saveModel(model)
        _state.value = _state.value.copy(model = model)
    }
    
    fun updateMaxTiers(tiers: Int) {
        val validTiers = tiers.coerceIn(1, 20)
        Log.d(TAG, "Updating max tiers: $validTiers")
        configManager.saveMaxTiers(validTiers)
        _state.value = _state.value.copy(maxTiers = validTiers)
    }
    
    fun clearLogs() {
        RequestLogManager.clear()
        _logs.value = emptyList()
    }
    
    fun getLogsText(): String {
        return RequestLogManager.getFullLogText()
    }
    
    fun startBenchmark() {
        Log.d(TAG, "=== START BENCHMARK CALLED ===")
        
        // Cancel any existing benchmark
        benchmarkJob?.cancel()
        
        // Validate inputs before starting
        val currentState = _state.value
        
        if (currentState.apiKey.isBlank()) {
            Log.e(TAG, "API Key is blank")
            _state.value = _state.value.copy(error = "API Key é obrigatória")
            return
        }
        
        if (currentState.endpoint.isBlank()) {
            Log.e(TAG, "Endpoint is blank")
            _state.value = _state.value.copy(error = "Endpoint é obrigatório")
            return
        }
        
        // Validate URL format
        try {
            URL(currentState.endpoint)
        } catch (e: Exception) {
            Log.e(TAG, "Invalid endpoint URL: ${currentState.endpoint}", e)
            _state.value = _state.value.copy(error = "Endpoint inválido: ${e.message}")
            return
        }
        
        Log.d(TAG, "Starting benchmark with endpoint=${currentState.endpoint}, model=${currentState.model}, maxTiers=${currentState.maxTiers}")
        
        // Clear previous logs
        RequestLogManager.clear()
        
        // Reset state
        _state.value = _state.value.copy(
            isRunning = true,
            error = null,
            results = emptyList(),
            currentTier = 0,
            elapsed = 0,
            currentTps = 0.0
        )
        
        benchmarkJob = viewModelScope.launch {
            try {
                Log.d(TAG, "Launching benchmark coroutine")
                
                val runner = BenchmarkRunner(
                    endpoint = currentState.endpoint,
                    apiKey = currentState.apiKey,
                    model = currentState.model,
                    onProgress = { progress ->
                        try {
                            _state.value = _state.value.copy(
                                currentTier = progress.currentTier,
                                elapsed = progress.elapsed,
                                completedRequests = progress.completedRequests,
                                currentTps = progress.currentTps
                            )
                            Log.d(TAG, "Progress: tier ${progress.currentTier}/${progress.totalTiers}")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error updating progress", e)
                        }
                    }
                )
                
                val startTime = System.currentTimeMillis()
                
                val tiers = withContext(Dispatchers.IO) {
                    Log.d(TAG, "Running benchmark on IO thread")
                    runner.runBenchmark(
                        maxTiers = currentState.maxTiers,
                        prompt = ConfigManager.DEFAULT_PROMPT
                    )
                }
                
                Log.d(TAG, "Benchmark completed with ${tiers.size} tiers")
                
                _state.value = _state.value.copy(
                    isRunning = false,
                    results = tiers,
                    elapsed = System.currentTimeMillis() - startTime,
                    currentTps = 0.0
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Benchmark failed with exception", e)
                _state.value = _state.value.copy(
                    isRunning = false,
                    currentTps = 0.0,
                    error = "Benchmark failed: ${e.javaClass.simpleName}: ${e.message}"
                )
            }
        }
    }
    
    fun exportCSV(): Uri? {
        return try {
            val currentState = _state.value
            if (currentState.results.isEmpty()) return null
            
            val data = BenchmarkData(
                timestamp = System.currentTimeMillis(),
                model = currentState.model,
                endpoint = currentState.endpoint,
                tiers = currentState.results
            )
            
            csvExporter.export(data)
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting CSV", e)
            null
        }
    }
}