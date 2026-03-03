# LLM Benchmark Android

Benchmark tool for measuring LLM API performance with progressive concurrency.

## 🎯 Features

- Progressive tier testing (1-N concurrent requests)
- Real-time metrics: TTFT, Tokens/sec, Throughput
- Plain text configuration (SharedPreferences)
- CSV export for results
- Fail-fast error handling
- Clean, minimal UI

## 🏗️ Architecture

```
app/
├── MainActivity.kt          # Single Activity
├── MainScreen.kt            # Compose UI
├── BenchmarkViewModel.kt    # State management
├── api/
│   ├── LLMClient.kt         # Retrofit client
│   └── LLMModels.kt         # Request/Response types
├── benchmark/
│   ├── BenchmarkRunner.kt   # Orchestration
│   ├── RequestExecutor.kt   # Request execution
│   └── MetricsCalculator.kt # Metrics
├── persistence/
│   ├── ConfigManager.kt     # SharedPreferences
│   └── CSVExporter.kt       # Export results
└── types/
    ├── BenchmarkResult.kt   # Data classes
    └── TierMetrics.kt       # Aggregated metrics
```

## 📱 Usage

1. Configure API endpoint and key
2. Select model and max tiers
3. Click "Start Benchmark"
4. View results in real-time
5. Export to CSV

## 🔧 Stack

- Kotlin 1.9.22
- Jetpack Compose
- Retrofit + OkHttp
- Coroutines + Flow
- SharedPreferences (plain text)

## 🚀 Build

```bash
./gradlew assembleDebug
```

## 📄 License

Personal use - Tiago Viana
