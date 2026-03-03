package com.tiagoviana.llmbenchmark

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tiagoviana.llmbenchmark.api.LLMClient
import com.tiagoviana.llmbenchmark.benchmark.BenchmarkRunner
import com.tiagoviana.llmbenchmark.persistence.CSVExporter
import com.tiagoviana.llmbenchmark.persistence.ConfigManager
import com.tiagoviana.llmbenchmark.types.BenchmarkData
import com.tiagoviana.llmbenchmark.types.BenchmarkState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BenchmarkViewModel(application: Application) : AndroidViewModel(application) {
    
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
    
    fun updateEndpoint(endpoint: String) {
        configManager.saveEndpoint(endpoint)
        _state.value = _state.value.copy(endpoint = endpoint)
    }
    
    fun updateApiKey(key: String) {
        configManager.saveAPIKey(key)
        _state.value = _state.value.copy(apiKey = key)
    }
    
    fun updateModel(model: String) {
        configManager.saveModel(model)
        _state.value = _state.value.copy(model = model)
    }
    
    fun updateMaxTiers(tiers: Int) {
        configManager.saveMaxTiers(tiers)
        _state.value = _state.value.copy(maxTiers = tiers)
    }
    
    fun startBenchmark() {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(
                    isRunning = true,
                    error = null,
                    results = emptyList(),
                    currentTier = 0,
                    elapsed = 0
                )
                
                val client = LLMClient(
                    endpoint = _state.value.endpoint,
                    apiKey = _state.value.apiKey
                )
                
                val runner = BenchmarkRunner(
                    client = client,
                    model = _state.value.model,
                    onProgress = { progress ->
                        _state.value = _state.value.copy(
                            currentTier = progress.currentTier,
                            elapsed = progress.elapsed,
                            completedRequests = progress.completedRequests
                        )
                    }
                )
                
                val startTime = System.currentTimeMillis()
                val tiers = runner.runBenchmark(
                    maxTiers = _state.value.maxTiers,
                    prompt = ConfigManager.DEFAULT_PROMPT
                )
                
                val data = BenchmarkData(
                    timestamp = startTime,
                    model = _state.value.model,
                    endpoint = _state.value.endpoint,
                    tiers = tiers
                )
                
                _state.value = _state.value.copy(
                    isRunning = false,
                    results = tiers,
                    elapsed = System.currentTimeMillis() - startTime
                )
                
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isRunning = false,
                    error = "Benchmark failed: ${e.message}"
                )
            }
        }
    }
    
    fun exportCSV(): Uri? {
        val currentState = _state.value
        if (currentState.results.isEmpty()) return null
        
        val data = BenchmarkData(
            timestamp = System.currentTimeMillis(),
            model = currentState.model,
            endpoint = currentState.endpoint,
            tiers = currentState.results
        )
        
        return csvExporter.export(data)
    }
}
