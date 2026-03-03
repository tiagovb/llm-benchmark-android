package com.tiagoviana.llmbenchmark.api

import com.squareup.gson.annotations.SerializedName

/**
 * Anthropic-compatible API request
 */
data class MessageRequest(
    val model: String,
    val messages: List<Message>,
    val max_tokens: Int = 1024,
    val stream: Boolean = true
)

data class Message(
    val role: String = "user",
    val content: String
)

/**
 * Streaming event types
 */
sealed class StreamEvent {
    data class Token(val text: String) : StreamEvent()
    data class Done(val stopReason: String?) : StreamEvent()
    data class Error(val exception: Exception) : StreamEvent()
}

/**
 * SSE event from API
 */
data class SSEEvent(
    val event: String?,
    val data: String?
)

/**
 * Anthropic response format
 */
data class MessageResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<ContentBlock>,
    val model: String,
    @SerializedName("stop_reason")
    val stopReason: String?,
    val usage: Usage?
)

data class ContentBlock(
    val type: String,
    val text: String?
)

data class Usage(
    @SerializedName("input_tokens")
    val inputTokens: Int,
    @SerializedName("output_tokens")
    val outputTokens: Int
)

/**
 * Streaming delta
 */
data class StreamDelta(
    val type: String?,
    val delta: DeltaContent?,
    val message: MessageResponse?
)

data class DeltaContent(
    val type: String?,
    val text: String?,
    @SerializedName("stop_reason")
    val stopReason: String?
)
