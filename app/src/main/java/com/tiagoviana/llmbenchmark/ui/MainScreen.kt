package com.tiagoviana.llmbenchmark.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.tiagoviana.llmbenchmark.BenchmarkViewModel
import com.tiagoviana.llmbenchmark.types.BenchmarkState
import com.tiagoviana.llmbenchmark.types.RequestLog
import com.tiagoviana.llmbenchmark.types.TierResult
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: BenchmarkViewModel) {
    val state by viewModel.state.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    
    var showApiKey by remember { mutableStateOf(false) }
    var selectedTabIndex by remember { mutableStateOf(0) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // Tabs
        TabRow(selectedTabIndex = selectedTabIndex) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = { selectedTabIndex = 0 },
                text = { Text("Benchmark") },
                icon = { Icon(Icons.Default.Speed, contentDescription = null) }
            )
            Tab(
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1 },
                text = { Text("Logs") },
                icon = { 
                    Badge(
                        containerColor = if (logs.any { it.level == RequestLog.LogLevel.ERROR }) 
                            MaterialTheme.colorScheme.error 
                        else 
                            MaterialTheme.colorScheme.primary
                    ) {
                        Text(logs.size.toString())
                    }
                }
            )
        }
        
        when (selectedTabIndex) {
            0 -> BenchmarkTab(
                state = state,
                viewModel = viewModel,
                showApiKey = showApiKey,
                onToggleApiKey = { showApiKey = !showApiKey }
            )
            1 -> LogsTab(
                logs = logs,
                onCopyAll = {
                    try {
                        val text = viewModel.getLogsText()
                        clipboardManager.setText(AnnotatedString(text))
                        Toast.makeText(context, "Logs copiados!", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Erro ao copiar: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                },
                onClear = { viewModel.clearLogs() }
            )
        }
    }
}

@Composable
fun BenchmarkTab(
    state: BenchmarkState,
    viewModel: BenchmarkViewModel,
    showApiKey: Boolean,
    onToggleApiKey: () -> Unit
) {
    val context = LocalContext.current
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Config Section
        item {
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
                        onValueChange = { 
                            try {
                                viewModel.updateEndpoint(it)
                            } catch (e: Exception) {
                                // Ignore update errors
                            }
                        },
                        label = { Text("API Endpoint") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !state.isRunning,
                        isError = state.endpoint.isBlank()
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // API Key
                    OutlinedTextField(
                        value = state.apiKey,
                        onValueChange = { 
                            try {
                                viewModel.updateApiKey(it)
                            } catch (e: Exception) {
                                // Ignore update errors
                            }
                        },
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (showApiKey) 
                            VisualTransformation.None 
                        else 
                            PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = onToggleApiKey) {
                                Icon(
                                    imageVector = if (showApiKey) 
                                        Icons.Default.VisibilityOff 
                                    else 
                                        Icons.Default.Visibility,
                                    contentDescription = "Toggle visibility"
                                )
                            }
                        },
                        enabled = !state.isRunning,
                        isError = state.apiKey.isBlank()
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Model and Tiers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = state.model,
                            onValueChange = { 
                                try {
                                    viewModel.updateModel(it)
                                } catch (e: Exception) {
                                    // Ignore
                                }
                            },
                            label = { Text("Model") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            enabled = !state.isRunning
                        )
                        
                        OutlinedTextField(
                            value = state.maxTiers.toString(),
                            onValueChange = { 
                                val value = it.toIntOrNull()
                                if (value != null && value in 1..20) {
                                    viewModel.updateMaxTiers(value)
                                }
                            },
                            label = { Text("Max Tiers (1-20)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            enabled = !state.isRunning
                        )
                    }
                }
            }
        }
        
        // Start Button
        item {
            Button(
                onClick = { 
                    try {
                        viewModel.startBenchmark()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                },
                enabled = !state.isRunning && state.apiKey.isNotBlank() && state.endpoint.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (state.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (state.isRunning) "Running..." else "Start Benchmark")
            }
        }
        
        // Progress
        if (state.isRunning || state.results.isNotEmpty()) {
            item {
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
                        
                        if (state.isRunning && state.maxTiers > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { 
                                    val progress = state.currentTier.toFloat() / state.maxTiers.coerceAtLeast(1)
                                    progress.coerceIn(0f, 1f)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
            
            // Results Table
            if (state.results.isNotEmpty()) {
                item {
                    ResultsTable(state.results)
                }
            }
            
            // Export Button
            if (state.results.isNotEmpty() && !state.isRunning) {
                item {
                    Button(
                        onClick = {
                            try {
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
                            } catch (e: Exception) {
                                Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_SHORT).show()
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
        }
        
        // Error
        state.error?.let { error ->
            item {
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogsTab(
    logs: List<RequestLog>,
    onCopyAll: () -> Unit,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Actions Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onCopyAll,
                enabled = logs.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copiar Tudo")
            }
            
            OutlinedButton(
                onClick = onClear,
                enabled = logs.isNotEmpty()
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Limpar")
            }
        }
        
        // Logs List
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Article,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Nenhum log ainda",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        "Execute um benchmark para ver os logs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(logs) { log ->
                    LogEntryCard(log = log)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogEntryCard(log: RequestLog) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    
    val backgroundColor = when (log.level) {
        RequestLog.LogLevel.INFO -> MaterialTheme.colorScheme.surfaceVariant
        RequestLog.LogLevel.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
        RequestLog.LogLevel.ERROR -> MaterialTheme.colorScheme.errorContainer
        RequestLog.LogLevel.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
    }
    
    val contentColor = when (log.level) {
        RequestLog.LogLevel.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
        RequestLog.LogLevel.SUCCESS -> MaterialTheme.colorScheme.onPrimaryContainer
        RequestLog.LogLevel.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        RequestLog.LogLevel.WARNING -> MaterialTheme.colorScheme.onTertiaryContainer
    }
    
    val levelIcon = when (log.level) {
        RequestLog.LogLevel.INFO -> Icons.Default.Info
        RequestLog.LogLevel.SUCCESS -> Icons.Default.CheckCircle
        RequestLog.LogLevel.ERROR -> Icons.Default.Error
        RequestLog.LogLevel.WARNING -> Icons.Default.Warning
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { },
                onLongClick = {
                    try {
                        clipboardManager.setText(AnnotatedString(log.formattedLog()))
                        Toast.makeText(context, "Copiado!", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor,
            contentColor = contentColor
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = levelIcon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = log.formattedTime(),
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "T${log.tier}#${log.requestId}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = log.message,
                style = MaterialTheme.typography.bodyMedium
            )
            log.details?.let { details ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun ResultsTable(results: List<TierResult>) {
    Card(
        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column {
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
            
            results.forEach { tier ->
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
