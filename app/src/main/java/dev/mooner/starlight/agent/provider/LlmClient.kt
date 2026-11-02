package dev.mooner.starlight.agent.provider

import dev.mooner.starlight.agent.model.ChatMessage
import dev.mooner.starlight.agent.model.Part
import dev.mooner.starlight.agent.model.RawTurn
import dev.mooner.starlight.agent.model.TurnUsage
import kotlinx.serialization.json.JsonObject

/** The wire format of a provider's API. */
enum class Protocol(val id: String) {
    /** Anthropic Messages API. */
    ANTHROPIC("anthropic"),
    /** OpenAI Responses API, also behind ChatGPT sign-in. */
    RESPONSES("responses"),
    /** OpenAI-compatible Chat Completions. */
    CHAT_COMPLETIONS("chat_completions"),
}

/** A tool the model may call. */
class ToolSpec(
    val name: String,
    val description: String,
    /** JSON Schema of the arguments, an object schema. */
    val parameters: JsonObject,
)

/** Reads attachment files for a request. */
interface AttachmentSource {

    fun bytes(attachment: Part.Attachment): ByteArray

    /** Pages of a PDF as JPEG images, for APIs that take images but not PDFs. */
    fun pdfPages(attachment: Part.Attachment): List<ByteArray>
}

class LlmRequest(
    val conversationId: String,
    val model: String,
    /** Stays the same for every request, so that it can be cached. */
    val system: String,
    /** Sorted by name, and the same for every request. */
    val tools: List<ToolSpec>,
    val messages: List<ChatMessage>,
    /** low / medium / high / xhigh / max, or null for the model's default. */
    val effort: String?,
    val maxOutputTokens: Int,
    val attachments: AttachmentSource,
    /** Lets the model search the web with its provider's own search, where the provider has one. */
    val webSearch: Boolean = false,
)

/** What a request streams while the model writes. Called on a background thread. */
interface StreamSink {
    fun onText(delta: String) {}
    fun onThinking(delta: String) {}
    fun onToolCall(id: String, name: String) {}
    fun onToolArguments(id: String, delta: String) {}
}

enum class StopReason {
    END,
    /** The provider paused a long turn, such as one with its own web searches; sent back as it is, the turn goes on. */
    PAUSE,
    TOOL_USE,
    MAX_TOKENS,
    /** The model or a safety classifier declined. */
    REFUSAL,
    OTHER,
}

/** One finished assistant turn. */
class LlmResult(
    /** Text, thinking and tool call parts, in order. */
    val parts: List<Part>,
    val raw: RawTurn?,
    val usage: TurnUsage,
    val stopReason: StopReason,
    /** The model that answered; may differ from the requested one after a fallback. */
    val model: String?,
    val refusal: String? = null,
)

/** An error the provider reported inside a stream, such as an overload. */
class StreamErrorException(
    val type: String?,
    message: String,
    val isRetryable: Boolean,
) : java.io.IOException(message)

interface LlmClient {

    suspend fun complete(request: LlmRequest, sink: StreamSink): LlmResult

    /** Model ids the account can use, best first when the API says nothing about order. */
    suspend fun listModels(): List<ModelInfo>
}

class ModelInfo(
    val id: String,
    val name: String = id,
    val contextWindow: Int? = null,
)
