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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.UUID

/**
 * OpenAI-compatible Chat Completions, for Gemini, OpenRouter and endpoints the user enters.
 *
 * These providers cache prompt prefixes on their own (Gemini implicitly, OpenAI-style servers by prefix). Through
 * OpenRouter, Claude models need `cache_control` markers, which [markCache] adds.
 */
class ChatCompletionsClient(
    private val apiKey: String?,
    private val baseUrl: String,
    private val flavor: Flavor = Flavor.GENERIC,
) : LlmClient {

    enum class Flavor {
        GENERIC,
        GEMINI,
        OPENROUTER,
    }

    override suspend fun complete(request: LlmRequest, sink: StreamSink): LlmResult {
        val stream = StreamState(sink)
        Http.postSse("${baseUrl.trimEnd('/')}/chat/completions", headers(), buildBody(request).toString()) { stream.onEvent(it) }
        return stream.result()
    }

    override suspend fun listModels(): List<ModelInfo> {
        val response = Http.get("${baseUrl.trimEnd('/')}/models", headers())
        val root = AgentJson.parseToJsonElement(response)
        val data = (root as? JsonObject)?.get("data") ?: (root as? JsonObject)?.get("models") ?: root
        return (data as? JsonArray).orEmpty().mapNotNull { element ->
            val model = element as? JsonObject ?: return@mapNotNull null
            val id = model.string("id")?.removePrefix("models/") ?: return@mapNotNull null
            ModelInfo(
                id = id,
                name = model.string("name") ?: model.string("display_name") ?: id,
                contextWindow = model["context_length"]?.jsonPrimitive?.intOrNull,
            )
        }
    }

    private fun headers(): Map<String, String> = buildMap {
        if (!apiKey.isNullOrBlank())
            put("Authorization", "Bearer $apiKey")
        if (flavor == Flavor.OPENROUTER) {
            put("HTTP-Referer", "https://starlight.mooner.dev")
            put("X-Title", "StarLight")
        }
    }

    /** Folds recorded stream events into a turn, as [complete] does with a live stream. */
    internal fun parse(events: List<SseEvent>, sink: StreamSink = object : StreamSink {}): LlmResult {
        val stream = StreamState(sink)
        events.forEach(stream::onEvent)
        return stream.result()
    }

    internal fun buildBody(request: LlmRequest): JsonObject = buildJsonObject {
        val cacheMarkers = flavor == Flavor.OPENROUTER && request.model.startsWith("anthropic/")
        put("model", request.model)
        put("stream", true)
        putJsonObject("stream_options") { put("include_usage", true) }
        put("messages", buildJsonArray {
            addJsonObject {
                put("role", "system")
                if (cacheMarkers)
                    putJsonArray("content") { add(textPart(request.system, cache = true)) }
                else
                    put("content", request.system)
            }
            val messages = messages(request)
            if (cacheMarkers)
                markCache(messages).forEach { add(it) }
            else
                messages.forEach { add(it) }
        })
        if (request.tools.isNotEmpty()) {
            put("tools", buildJsonArray {
                for (tool in request.tools.sortedBy(ToolSpec::name))
                    addJsonObject {
                        put("type", "function")
                        putJsonObject("function") {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("parameters", tool.parameters)
                        }
                    }
            })
            put("tool_choice", "auto")
        }
        // OpenRouter searches the web for any model with its web plugin.
        if (request.webSearch && flavor == Flavor.OPENROUTER)
            putJsonArray("plugins") {
                addJsonObject {
                    put("id", "web")
                    put("max_results", OPENROUTER_SEARCH_RESULTS)
                }
            }
        if (request.effort != null) {
            when (flavor) {
                Flavor.OPENROUTER -> putJsonObject("reasoning") { put("effort", request.effort) }
                else              -> put("reasoning_effort", request.effort)
            }
        }
    }

    private fun messages(request: LlmRequest): List<JsonObject> {
        val list = mutableListOf<JsonObject>()
        for (message in RequestContent.visibleMessages(request.messages)) {
            when (message.role) {
                Role.USER      -> list += buildJsonObject {
                    put("role", "user")
                    put("content", userContent(message, request.attachments))
                }
                Role.ASSISTANT -> list += assistantMessage(message)
                Role.TOOL      -> {
                    val results = message.parts.filterIsInstance<Part.ToolResult>()
                    for (result in results)
                        list += buildJsonObject {
                            put("role", "tool")
                            put("tool_call_id", result.callId)
                            put("content", result.output)
                        }
                    toolImages(results, request.attachments)?.let { list += it }
                }
            }
        }
        return list
    }

    /** The images tools showed, as a user message after the tool messages, which can only hold text. */
    private fun toolImages(results: List<Part.ToolResult>, source: AttachmentSource): JsonObject? {
        val shown = results.filter { it.images.isNotEmpty() }
        if (shown.isEmpty())
            return null
        return buildJsonObject {
            put("role", "user")
            putJsonArray("content") {
                for (result in shown) {
                    for (image in result.images)
                        add(imagePart(image.mimeType, source.bytes(image)))
                    add(textPart(RequestContent.toolImagesLabel(result), cache = false))
                }
            }
        }
    }

    private fun userContent(message: ChatMessage, source: AttachmentSource): JsonArray = buildJsonArray {
        val texts = mutableListOf<String>()
        for (part in message.parts) {
            when (part) {
                is Part.Attachment -> when (part.kind) {
                    AttachmentKind.IMAGE -> {
                        texts += RequestContent.attachmentLabel(part)
                        add(imagePart(part.mimeType, source.bytes(part)))
                    }
                    AttachmentKind.PDF -> {
                        texts += RequestContent.attachmentLabel(part)
                        if (flavor == Flavor.OPENROUTER)
                            addJsonObject {
                                put("type", "file")
                                putJsonObject("file") {
                                    put("filename", part.name)
                                    put("file_data", RequestContent.dataUrl("application/pdf", source.bytes(part)))
                                }
                            }
                        else
                            // Not every compatible server reads PDFs, but they all read images.
                            for (page in source.pdfPages(part))
                                add(imagePart("image/jpeg", page))
                    }
                    AttachmentKind.TEXT -> texts += RequestContent.textAttachment(part, source)
                    AttachmentKind.FILE -> texts += RequestContent.fileAttachmentNote(part)
                }
                is Part.Text -> texts += part.text
                is Part.Context -> texts += part.text
                else -> Unit
            }
        }
        for (text in texts.filter(String::isNotBlank))
            add(textPart(text, cache = false))
    }

    private fun assistantMessage(message: ChatMessage): JsonObject {
        val raw = message.raw
        if (raw != null && raw.protocol == Protocol.CHAT_COMPLETIONS.id)
            (raw.items.firstOrNull() as? JsonObject)?.let { return it }
        val calls = message.toolCalls
        return buildJsonObject {
            put("role", "assistant")
            put("content", message.text.ifEmpty { null }?.let(::JsonPrimitive) ?: JsonNull)
            if (calls.isNotEmpty())
                putJsonArray("tool_calls") {
                    for (call in calls)
                        addJsonObject {
                            put("id", call.id)
                            put("type", "function")
                            putJsonObject("function") {
                                put("name", call.name)
                                put("arguments", call.arguments)
                            }
                        }
                }
        }
    }

    private fun imagePart(mimeType: String, bytes: ByteArray): JsonObject = buildJsonObject {
        put("type", "image_url")
        putJsonObject("image_url") { put("url", RequestContent.dataUrl(mimeType, bytes)) }
    }

    private fun textPart(text: String, cache: Boolean): JsonObject = buildJsonObject {
        put("type", "text")
        put("text", text)
        if (cache)
            putJsonObject("cache_control") { put("type", "ephemeral") }
    }

    /** Puts a cache breakpoint on the last part of the last message, for Claude through OpenRouter. */
    private fun markCache(messages: List<JsonObject>): List<JsonObject> {
        val index = messages.indexOfLast { it["content"] is JsonArray || (it["content"] as? JsonPrimitive)?.isString == true }
        if (index < 0)
            return messages
        val message = messages[index]
        val content = when (val value = message["content"]) {
            is JsonArray -> value
            else -> JsonArray(listOf(textPart((value as JsonPrimitive).content, cache = false)))
        }
        val lastText = content.indexOfLast { (it as? JsonObject)?.string("type") == "text" }
        if (lastText < 0)
            return messages
        val marked = content.mapIndexed { i, part ->
            if (i == lastText)
                JsonObject((part as JsonObject) + ("cache_control" to buildJsonObject { put("type", "ephemeral") }))
            else
                part
        }
        return messages.toMutableList().apply { this[index] = JsonObject(message + ("content" to JsonArray(marked))) }
    }

    private class StreamState(private val sink: StreamSink) {

        private val text = StringBuilder()
        private val thinking = StringBuilder()
        private val calls = sortedMapOf<Int, Call>()
        private var usage = TurnUsage()
        private var finishReason: String? = null
        private var model: String? = null

        private class Call(var id: String = "", var name: String = "") {
            val arguments = StringBuilder()
            /** Fields other than id and function, such as Gemini's thought signature, sent back as they came. */
            val extra = linkedMapOf<String, JsonElement>()
        }

        fun onEvent(event: SseEvent) {
            val data = event.data.trim()
            if (data.isEmpty() || data == "[DONE]")
                return
            val chunk = AgentJson.parseToJsonElement(data).jsonObject
            (chunk["error"] as? JsonObject)?.let { error ->
                val code = error["code"]?.jsonPrimitive?.contentOrNull
                throw StreamErrorException(code, error.string("message") ?: data, isRetryable = code == "429" || code?.startsWith("5") == true)
            }
            chunk.string("model")?.let { model = it }
            (chunk["usage"] as? JsonObject)?.let { readUsage(it) }
            val choice = (chunk["choices"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return
            choice.string("finish_reason")?.let { finishReason = it }
            val delta = choice["delta"] as? JsonObject ?: return
            delta.string("content")?.let {
                text.append(it)
                sink.onText(it)
            }
            (delta.string("reasoning_content") ?: delta.string("reasoning"))?.let {
                thinking.append(it)
                sink.onThinking(it)
            }
            for (element in (delta["tool_calls"] as? JsonArray).orEmpty()) {
                val callDelta = element as? JsonObject ?: continue
                val index = callDelta["index"]?.jsonPrimitive?.intOrNull ?: calls.size
                val call = calls.getOrPut(index) { Call() }
                val isNew = call.id.isEmpty()
                callDelta.string("id")?.takeIf { it.isNotEmpty() }?.let { call.id = it }
                val function = callDelta["function"] as? JsonObject
                function?.string("name")?.takeIf { it.isNotEmpty() }?.let { call.name = it }
                for ((key, value) in callDelta)
                    if (key !in CALL_FIELDS)
                        call.extra[key] = value
                if (isNew && call.id.isNotEmpty())
                    sink.onToolCall(call.id, call.name)
                function?.string("arguments")?.let {
                    call.arguments.append(it)
                    sink.onToolArguments(call.id, it)
                }
            }
        }

        private fun readUsage(json: JsonObject) {
            val prompt = json["prompt_tokens"]?.jsonPrimitive?.longOrNull ?: 0
            val cached = (json["prompt_tokens_details"] as? JsonObject)?.get("cached_tokens")?.jsonPrimitive?.longOrNull ?: 0
            val output = json["completion_tokens"]?.jsonPrimitive?.longOrNull ?: 0
            usage = TurnUsage(input = (prompt - cached).coerceAtLeast(0), cacheRead = cached, output = output)
        }

        fun result(): LlmResult {
            val parts = mutableListOf<Part>()
            if (thinking.isNotBlank())
                parts += Part.Thinking(thinking.toString())
            if (text.isNotEmpty())
                parts += Part.Text(text.toString())
            // Some servers stream calls without ids. Made-up ids must be unique across the conversation, since
            // results, rows and other providers find calls by id.
            val callsWithIds = calls.values.map { call ->
                if (call.id.isEmpty())
                    call.id = "call_" + UUID.randomUUID().toString().replace("-", "").take(24)
                call
            }
            for (call in callsWithIds)
                parts += Part.ToolCall(call.id, call.name, call.arguments.toString().ifBlank { "{}" })
            val message = buildJsonObject {
                put("role", "assistant")
                put("content", if (text.isEmpty()) JsonNull else JsonPrimitive(text.toString()))
                if (callsWithIds.isNotEmpty())
                    putJsonArray("tool_calls") {
                        for (call in callsWithIds)
                            addJsonObject {
                                put("id", call.id)
                                put("type", "function")
                                putJsonObject("function") {
                                    put("name", call.name)
                                    put("arguments", call.arguments.toString().ifBlank { "{}" })
                                }
                                for ((key, value) in call.extra)
                                    if (key != "type")
                                        put(key, value)
                            }
                    }
            }
            val reason = when (finishReason) {
                "stop"                          -> StopReason.END
                "tool_calls", "function_call"   -> StopReason.TOOL_USE
                "length"                        -> StopReason.MAX_TOKENS
                "content_filter"                -> StopReason.REFUSAL
                null                            -> if (callsWithIds.isNotEmpty()) StopReason.TOOL_USE else StopReason.END
                else                            -> if (callsWithIds.isNotEmpty()) StopReason.TOOL_USE else StopReason.OTHER
            }
            return LlmResult(parts, RawTurn(Protocol.CHAT_COMPLETIONS.id, JsonArray(listOf(message))), usage, reason, model)
        }

        companion object {
            private val CALL_FIELDS = setOf("index", "id", "function", "type")
        }
    }
}

/** How many results OpenRouter's web plugin adds to a request. */
private const val OPENROUTER_SEARCH_RESULTS = 5
