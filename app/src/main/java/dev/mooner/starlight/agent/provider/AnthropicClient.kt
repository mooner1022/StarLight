package dev.mooner.starlight.agent.provider

import dev.mooner.starlight.agent.model.AgentJson
import dev.mooner.starlight.agent.model.AttachmentKind
import dev.mooner.starlight.agent.model.ChatMessage
import dev.mooner.starlight.agent.model.Part
import dev.mooner.starlight.agent.model.RawTurn
import dev.mooner.starlight.agent.model.Role
import dev.mooner.starlight.agent.model.TurnUsage
import dev.mooner.starlight.agent.net.Http
import dev.mooner.starlight.agent.net.SseEvent
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The Anthropic Messages API over plain HTTP and server-sent events.
 *
 * Caching: one breakpoint on the system prompt, which tools render before, and automatic caching (top-level
 * `cache_control`) for the growing conversation. Assistant turns are sent back exactly as they came, thinking blocks
 * and their signatures included.
 */
class AnthropicClient(
    private val apiKey: String,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : LlmClient {

    override suspend fun complete(request: LlmRequest, sink: StreamSink): LlmResult {
        val body = buildBody(request)
        val headers = buildMap {
            put("x-api-key", apiKey)
            put("anthropic-version", API_VERSION)
            if (supportsFallbacks(request.model))
                put("anthropic-beta", FALLBACK_BETA)
        }
        val stream = StreamState(sink)
        Http.postSse("$baseUrl/v1/messages", headers, body.toString()) { stream.onEvent(it) }
        return stream.result()
    }

    override suspend fun listModels(): List<ModelInfo> {
        val response = Http.get("$baseUrl/v1/models?limit=100", mapOf("x-api-key" to apiKey, "anthropic-version" to API_VERSION))
        return AgentJson.parseToJsonElement(response).jsonObject["data"]?.jsonArray.orEmpty().map { element ->
            val model = element.jsonObject
            ModelInfo(
                id = model.string("id") ?: "",
                name = model.string("display_name") ?: model.string("id") ?: "",
                contextWindow = model["max_input_tokens"]?.jsonPrimitive?.longOrNull?.toInt(),
            )
        }.filter { it.id.isNotEmpty() }
    }

    /** Folds recorded stream events into a turn, as [complete] does with a live stream. */
    internal fun parse(events: List<SseEvent>, sink: StreamSink = object : StreamSink {}): LlmResult {
        val stream = StreamState(sink)
        events.forEach(stream::onEvent)
        return stream.result()
    }

    internal fun buildBody(request: LlmRequest): JsonObject = buildJsonObject {
        put("model", request.model)
        put("max_tokens", request.maxOutputTokens)
        put("stream", true)
        put("system", buildJsonArray {
            addJsonObject {
                put("type", "text")
                put("text", request.system)
                put("cache_control", EPHEMERAL)
            }
        })
        if (request.tools.isNotEmpty())
            put("tools", buildJsonArray {
                for (tool in request.tools.sortedBy(ToolSpec::name))
                    addJsonObject {
                        put("name", tool.name)
                        put("description", tool.description)
                        put("input_schema", tool.parameters)
                        // Lets file bodies stream as they are written; arguments are checked before a tool runs.
                        put("eager_input_streaming", true)
                    }
            })
        put("messages", messages(request))
        // Automatic caching keeps a breakpoint on the end of the conversation as it grows.
        put("cache_control", EPHEMERAL)
        if (supportsAdaptiveThinking(request.model))
            putJsonObject("thinking") {
                put("type", "adaptive")
                put("display", "summarized")
            }
        if (request.effort != null && supportsEffort(request.model))
            putJsonObject("output_config") { put("effort", request.effort) }
        if (supportsFallbacks(request.model))
            put("fallbacks", "default")
    }

    private fun messages(request: LlmRequest): JsonArray {
        val turns = mutableListOf<Pair<String, MutableList<JsonElement>>>()
        fun add(role: String, blocks: List<JsonElement>) {
            if (blocks.isEmpty())
                return
            // Turns of the same role are merged, as when a run stopped before the model answered.
            val last = turns.lastOrNull()
            if (last != null && last.first == role)
                last.second += blocks
            else
                turns += role to blocks.toMutableList()
        }
        for (message in RequestContent.visibleMessages(request.messages)) {
            when (message.role) {
                Role.USER      -> add("user", userBlocks(message, request.attachments))
                Role.ASSISTANT -> add("assistant", assistantBlocks(message))
                Role.TOOL      -> add("user", toolResultBlocks(message, request.attachments))
            }
        }
        return buildJsonArray {
            for ((role, blocks) in turns)
                addJsonObject {
                    put("role", role)
                    put("content", JsonArray(blocks))
                }
        }
    }

    private fun userBlocks(message: ChatMessage, source: AttachmentSource): List<JsonElement> {
        val media = mutableListOf<JsonElement>()
        val texts = mutableListOf<String>()
        for (part in message.parts) {
            when (part) {
                is Part.Attachment -> when (part.kind) {
                    AttachmentKind.IMAGE -> {
                        texts += RequestContent.attachmentLabel(part)
                        media += imageBlock(part, source)
                    }
                    AttachmentKind.PDF -> {
                        texts += RequestContent.attachmentLabel(part)
                        media += buildJsonObject {
                            put("type", "document")
                            putJsonObject("source") {
                                put("type", "base64")
                                put("media_type", "application/pdf")
                                put("data", RequestContent.base64(source.bytes(part)))
                            }
                            put("title", part.name)
                        }
                    }
                    AttachmentKind.TEXT -> texts += RequestContent.textAttachment(part, source)
                    AttachmentKind.FILE -> texts += RequestContent.fileAttachmentNote(part)
                }
                is Part.Text -> texts += part.text
                is Part.Context -> texts += part.text
                else -> Unit
            }
        }
        // Images and documents go before the text that refers to them.
        return media + texts.filter(String::isNotBlank).map { text ->
            buildJsonObject {
                put("type", "text")
                put("text", text)
            }
        }
    }

    private fun assistantBlocks(message: ChatMessage): List<JsonElement> {
        val raw = message.raw
        if (raw != null && raw.protocol == Protocol.ANTHROPIC.id)
            return raw.items
        return message.parts.mapNotNull { part ->
            when (part) {
                is Part.Text -> buildJsonObject {
                    put("type", "text")
                    put("text", part.text)
                }
                is Part.ToolCall -> buildJsonObject {
                    put("type", "tool_use")
                    put("id", toolUseId(part.id))
                    put("name", part.name)
                    put("input", RequestContent.parseArguments(part.arguments) ?: JsonObject(emptyMap()))
                }
                else -> null
            }
        }
    }

    private fun imageBlock(image: Part.Attachment, source: AttachmentSource): JsonObject = buildJsonObject {
        put("type", "image")
        putJsonObject("source") {
            put("type", "base64")
            put("media_type", image.mimeType)
            put("data", RequestContent.base64(source.bytes(image)))
        }
    }

    private fun toolResultBlocks(message: ChatMessage, source: AttachmentSource): List<JsonElement> =
        message.parts.filterIsInstance<Part.ToolResult>().map { result ->
            buildJsonObject {
                put("type", "tool_result")
                put("tool_use_id", toolUseId(result.callId))
                if (result.images.isEmpty())
                    put("content", result.output)
                else
                    // A tool result can hold images; like the user's, they go before the text.
                    putJsonArray("content") {
                        for (image in result.images)
                            add(imageBlock(image, source))
                        addJsonObject {
                            put("type", "text")
                            put("text", (listOf(result.output) + result.images.map(RequestContent::attachmentLabel)).joinToString("\n"))
                        }
                    }
                if (result.isError)
                    put("is_error", true)
            }
        }

    /** Stream events folded into one assistant turn. */
    private class StreamState(private val sink: StreamSink) {

        private val blocks = sortedMapOf<Int, Block>()
        private var usage = TurnUsage()
        private var stopReason: String? = null
        private var refusal: String? = null
        private var model: String? = null

        private class Block(var json: MutableMap<String, JsonElement>) {
            val text = StringBuilder()
            val thinking = StringBuilder()
            val signature = StringBuilder()
            val arguments = StringBuilder()
            val type: String
                get() = (json["type"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        }

        fun onEvent(event: SseEvent) {
            if (event.data.isBlank())
                return
            val data = AgentJson.parseToJsonElement(event.data).jsonObject
            when (data.string("type")) {
                "message_start" -> {
                    val message = data["message"]?.jsonObject ?: return
                    model = message.string("model")
                    message["usage"]?.jsonObject?.let(::readUsage)
                }
                "content_block_start" -> {
                    val index = data["index"]!!.jsonPrimitive.int
                    val block = Block(data["content_block"]!!.jsonObject.toMutableMap())
                    blocks[index] = block
                    when (block.type) {
                        "tool_use" -> sink.onToolCall(block.json.string("id").orEmpty(), block.json.string("name").orEmpty())
                        "fallback" -> block.json["to"]?.jsonObject?.string("model")?.let { model = it }
                    }
                }
                "content_block_delta" -> {
                    val block = blocks[data["index"]!!.jsonPrimitive.int] ?: return
                    val delta = data["delta"]?.jsonObject ?: return
                    when (delta.string("type")) {
                        "text_delta" -> delta.string("text")?.let {
                            block.text.append(it)
                            sink.onText(it)
                        }
                        "thinking_delta" -> delta.string("thinking")?.let {
                            block.thinking.append(it)
                            sink.onThinking(it)
                        }
                        "signature_delta" -> delta.string("signature")?.let(block.signature::append)
                        "input_json_delta" -> delta.string("partial_json")?.let {
                            block.arguments.append(it)
                            sink.onToolArguments(block.json.string("id").orEmpty(), it)
                        }
                    }
                }
                "message_delta" -> {
                    data["delta"]?.jsonObject?.let { delta ->
                        delta.string("stop_reason")?.let { stopReason = it }
                        (delta["stop_details"] as? JsonObject)?.let { details ->
                            refusal = listOfNotNull(details.string("category"), details.string("explanation"))
                                .joinToString(": ")
                                .ifEmpty { null }
                        }
                    }
                    data["usage"]?.jsonObject?.let(::readUsage)
                }
                "error" -> {
                    val error = data["error"]?.jsonObject
                    val type = error?.string("type")
                    throw StreamErrorException(
                        type,
                        error?.string("message") ?: event.data,
                        isRetryable = type == "overloaded_error" || type == "api_error" || type == "rate_limit_error",
                    )
                }
            }
        }

        private fun readUsage(json: JsonObject) {
            fun value(name: String, current: Long) = json[name]?.jsonPrimitive?.longOrNull ?: current
            usage = TurnUsage(
                input = value("input_tokens", usage.input),
                cacheRead = value("cache_read_input_tokens", usage.cacheRead),
                cacheWrite = value("cache_creation_input_tokens", usage.cacheWrite),
                output = value("output_tokens", usage.output),
            )
        }

        fun result(): LlmResult {
            val parts = mutableListOf<Part>()
            val raw = buildJsonArray {
                for (block in blocks.values) {
                    val json = block.json
                    when (block.type) {
                        "text" -> {
                            json["text"] = JsonPrimitive(block.text.toString())
                            if (block.text.isNotEmpty())
                                parts += Part.Text(block.text.toString())
                        }
                        "thinking" -> {
                            json["thinking"] = JsonPrimitive(block.thinking.toString())
                            json["signature"] = JsonPrimitive(block.signature.toString())
                            if (block.thinking.isNotBlank())
                                parts += Part.Thinking(block.thinking.toString())
                        }
                        "tool_use" -> {
                            val arguments = block.arguments.toString()
                            json["input"] = RequestContent.parseArguments(arguments) ?: JsonObject(emptyMap())
                            parts += Part.ToolCall(json.string("id").orEmpty(), json.string("name").orEmpty(), arguments.ifBlank { "{}" })
                        }
                    }
                    add(JsonObject(json))
                }
            }
            val reason = when (stopReason) {
                "end_turn", "stop_sequence", "pause_turn" -> StopReason.END
                "tool_use"                                -> StopReason.TOOL_USE
                "max_tokens", "model_context_window_exceeded" -> StopReason.MAX_TOKENS
                "refusal"                                 -> StopReason.REFUSAL
                else                                      -> StopReason.OTHER
            }
            return LlmResult(parts, RawTurn(Protocol.ANTHROPIC.id, raw), usage, reason, model, refusal)
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.anthropic.com"
        const val API_VERSION = "2026-10-21"
        private const val FALLBACK_BETA = "server-side-fallback-2026-07-01"

        private val EPHEMERAL = buildJsonObject { put("type", "ephemeral") }

        val DEFAULT_MODELS = listOf(
            ModelInfo("claude-opus-5-5", "Claude Opus 5.5", 1_000_000),
            ModelInfo("claude-sonnet-5-5", "Claude Sonnet 5.5", 1_000_000),
            ModelInfo("claude-haiku-5-5", "Claude Haiku 5.5", 1_000_000),
            ModelInfo("claude-fable-5-1", "Claude Fable 5.1", 1_000_000),
        )

        private val ADAPTIVE_THINKING = listOf(
            "claude-opus-5", "claude-sonnet-5", "claude-haiku-5", "claude-fable-5", "claude-mythos-5",
            "claude-opus-4-6", "claude-opus-4-7", "claude-opus-4-8", "claude-sonnet-4-6",
        )

        /** Models that take `thinking: {type: "adaptive"}`; others run without the field. */
        fun supportsAdaptiveThinking(model: String): Boolean =
            ADAPTIVE_THINKING.any(model::startsWith)

        fun supportsEffort(model: String): Boolean =
            supportsAdaptiveThinking(model) || model.startsWith("claude-opus-4-5")

        /** Models with server-side fallbacks on a safety decline. */
        fun supportsFallbacks(model: String): Boolean =
            model == "claude-opus-5-5" || model == "claude-opus-5" || model == "claude-fable-5-1" || model == "claude-sonnet-5-5"

        /** Tool use ids must match `^[a-zA-Z0-9_-]+$`; ids from other providers may not. */
        fun toolUseId(id: String): String =
            id.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifEmpty { "call" }
    }
}

internal fun Map<String, JsonElement>.string(name: String): String? =
    (this[name] as? JsonPrimitive)?.contentOrNull
