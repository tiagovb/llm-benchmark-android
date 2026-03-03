package com.tiagoviana.llmbenchmark.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.tiagoviana.llmbenchmark.BenchmarkViewModel
import com.tiagoviana.llmbenchmark.types.BenchmarkState
import com.tiagoviana.llmbenchmark.types.TierResult
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: BenchmarkViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    
    var showApiKey by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Config Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Configuration",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Endpoint
                OutlinedTextField(
                    value = state.endpoint,
                    onValueChange = { viewModel.updateEndpoint(it) },
                    label = { Text("API Endpoint") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !state.isRunning
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // API Key
                OutlinedTextField(
                    value = state.apiKey,
                    onValueChange = { viewModel.updateApiKey(it) },
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showApiKey) 
                        VisualTransformation.None 
                    else 
                        PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                imageVector = if (showApiKey) 
                                    Icons.Default.VisibilityOff 
                                else 
                                    Icons.Default.Visibility,
                                contentDescription = "Toggle visibility"
                            )
                        }
                    },
                    enabled = !state.isRunning
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Model and Tiers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = state.model,
                        onValueChange = { viewModel.updateModel(it) },
                        label = { Text("Model") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        enabled = !state.isRunning
                    )
                    
                    OutlinedTextField(
                        value = state.maxTiers.toString(),
                        onValueChange = { 
                            viewModel.updateMaxTiers(it.toIntOrNull() ?: 10)
                        },
                        label = { Text("Max Tiers") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = !state.isRunning
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Start Button
        Button(
            onClick = { viewModel.startBenchmark() },
            enabled = !state.isRunning && state.apiKey.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = if (state.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (state.isRunning) "Running..." else "Start Benchmark")
        }
        
        // Progress
        if (state.isRunning || state.results.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Progress",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Tier: ${state.currentTier}/${state.maxTiers}")
                        Text("Concurrency: ${state.currentTier} requests")
                        Text(formatDuration(state.elapsed))
                    }
                    
                    if (state.isRunning) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = state.currentTier.toFloat() / state.maxTiers,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Results Table
            ResultsTable(state.results)
            
            // Export Button
            if (state.results.isNotEmpty() && !state.isRunning) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = {
                        val uri = viewModel.exportCSV()
                        if (uri != null) {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(
                                Intent.createChooser(shareIntent, "Export Results")
                            )
                        } else {
                            Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export CSV")
                }
            }
        }
        
        // Error
        state.error?.let { error ->
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
fun ResultsTable(results: List<TierResult>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        LazyColumn {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Text(
                        "Tier",
                        modifier = Modifier.weight(0.15f),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        "TTFT",
                        modifier = Modifier.weight(0.2f),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        "Tks/s",
                        modifier = Modifier.weight(0.2f),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        "Status",
                        modifier = Modifier.weight(0.45f),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            
            items(results) { tier ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${tier.concurrency}",
                        modifier = Modifier.weight(0.15f)
                    )
                    Text(
                        "${String.format("%.2f", tier.avgTTFT / 1000.0)}s",
                        modifier = Modifier.weight(0.2f)
                    )
                    Text(
                        String.format("%.1f", tier.avgTokensPerSec),
                        modifier = Modifier.weight(0.2f)
                    )
                    Text(
                        if (tier.errorCount == 0) "✓ Success" 
                        else "⚠ ${tier.errorCount} errors",
                        modifier = Modifier.weight(0.45f),
                        color = if (tier.errorCount == 0) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return String.format("%02d:%02d", minutes, seconds)
}
