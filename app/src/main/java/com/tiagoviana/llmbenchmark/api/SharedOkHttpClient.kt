package com.tiagoviana.llmbenchmark.api

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Shared singleton client to prevent OOM / thread exhaustion crashes
 * when creating hundreds of concurrent clients.
 */
object SharedOkHttpClient {
    val instance: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}