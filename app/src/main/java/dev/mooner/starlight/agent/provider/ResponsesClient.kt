package dev.mooner.starlight.agent.provider

import dev.mooner.starlight.agent.model.AgentJson
import dev.mooner.starlight.agent.model.AttachmentKind
import dev.mooner.starlight.agent.model.ChatMessage
import dev.mooner.starlight.agent.model.Part
import dev.mooner.starlight.agent.model.RawTurn
import dev.mooner.starlight.agent.model.Role
import dev.mooner.starlight.agent.model.TurnUsage
import dev.mooner.starlight.agent.net.Http
import dev.mooner.starlight.agent.net.HttpStatusException
import dev.mooner.starlight.agent.net.SseEvent
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The OpenAI Responses API, statelessly (`store: false`): every request carries the whole history, and reasoning
 * comes back encrypted so that it can be sent again. OpenAI caches the prompt prefix on its own;
 * `prompt_cache_key` keeps the requests of one conversation on the same cache.
 *
 * Used with an API key, and with ChatGPT sign-in ([chatGpt]), where OpenAI's rules for apps that use a
 * ChatGPT plan apply: no output limit, and function tools grouped in a namespace.
 */
class ResponsesClient(
    private val token: suspend (forceRefresh: Boolean) -> String,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val chatGpt: Boolean = false,
) : LlmClient {

    override suspend fun complete(request: LlmRequest, sink: StreamSink): LlmResult {
        val body = buildBody(request).toString()
        return try {
            send(request, body, sink, forceRefresh = false)
        } catch (e: HttpStatusException) {
            // A ChatGPT access token can expire mid-conversation; refresh it once.
            if (e.code != 401 || !chatGpt)
                throw e
            send(request, body, sink, forceRefresh = true)
        }
    }

    private suspend fun send(request: LlmRequest, body: String, sink: StreamSink, forceRefresh: Boolean): LlmResult {
        val headers = buildMap {
            put("Authorization", "Bearer ${token(forceRefresh)}")
            if (chatGpt) {
                // ChatGPT keeps the prompt cache of a session together by these.
                put("session-id", request.conversationId)
                put("thread-id", request.conversationId)
            }
        }
        val stream = StreamState(sink)
        Http.postSse("$baseUrl/responses", headers, body) { stream.onEvent(it) }
        return stream.result()
    }

    override suspend fun listModels(): List<ModelInfo> {
        val response = Http.get("$baseUrl/models", mapOf("Authorization" to "Bearer ${token(false)}"))
        val root = AgentJson.parseToJsonElement(response).jsonObject
        // ChatGPT sign-in lists `models` with slugs; API keys list `data` with ids.
        root["models"]?.jsonArray?.let { models ->
            return models.mapNotNull { element ->
                val model = element.jsonObject
                if (model.string("visibility") != "list" || model["supported_in_api"]?.jsonPrimitive?.booleanOrNull == false)
                    return@mapNotNull null
                val slug = model.string("slug") ?: return@mapNotNull null
                ModelInfo(slug, model.string("display_name") ?: slug, model["context_window"]?.jsonPrimitive?.intOrNull)
            }
        }
        return root["data"]?.jsonArray.orEmpty()
            .mapNotNull { it.jsonObject.string("id") }
            .filter(::isChatModel)
            .sortedDescending()
            .map { ModelInfo(it) }
    }

    /** Folds recorded stream events into a turn, as [complete] does with a live stream. */
    internal fun parse(events: List<SseEvent>, sink: StreamSink = object : StreamSink {}): LlmResult {
        val stream = StreamState(sink)
        events.forEach(stream::onEvent)
        return stream.result()
    }

    internal fun buildBody(request: LlmRequest): JsonObject = buildJsonObject {
        put("model", request.model)
        put("instructions", request.system)
        put("input", input(request))
        if (request.tools.isNotEmpty()) {
            val functions = request.tools.sortedBy(ToolSpec::name).map { tool ->
                buildJsonObject {
                    put("type", "function")
                    put("name", tool.name)
                    put("description", tool.description)
                    put("parameters", tool.parameters)
                    put("strict", false)
                }
            }
            put("tools", buildJsonArray {
                if (chatGpt)
                    addJsonObject {
                        put("type", "namespace")
                        put("name", TOOL_NAMESPACE)
                        put("description", "Tools that read and change the user's StarLight projects on this device.")
                        put("tools", JsonArray(functions))
                    }
                else
                    functions.forEach { add(it) }
            })
            put("tool_choice", "auto")
            put("parallel_tool_calls", true)
        }
        if (chatGpt || isReasoningModel(request.model)) {
            putJsonObject("reasoning") {
                request.effort?.let { put("effort", it) }
                put("summary", "auto")
            }
            put("include", buildJsonArray { add(JsonPrimitive("reasoning.encrypted_content")) })
        }
        put("prompt_cache_key", request.conversationId)
        put("store", false)
        put("stream", true)
    }

    private fun input(request: LlmRequest): JsonArray = buildJsonArray {
        for (message in RequestContent.visibleMessages(request.messages)) {
            when (message.role) {
                Role.USER -> addJsonObject {
                    put("type", "message")
                    put("role", "user")
                    put("content", userContent(message, request.attachments))
                }
                Role.ASSISTANT -> assistantItems(message).forEach { add(it) }
                Role.TOOL -> {
                    val results = message.parts.filterIsInstance<Part.ToolResult>()
                    for (result in results)
                        addJsonObject {
                            put("type", "function_call_output")
                            put("call_id", result.callId)
                            put("output", result.output)
                        }
                    toolImages(results, request.attachments)?.let { add(it) }
                }
            }
        }
    }

    /** The images tools showed, as a user message after their outputs, which not every server lets carry images. */
    private fun toolImages(results: List<Part.ToolResult>, source: AttachmentSource): JsonObject? {
        val shown = results.filter { it.images.isNotEmpty() }
        if (shown.isEmpty())
            return null
        return buildJsonObject {
            put("type", "message")
            put("role", "user")
            putJsonArray("content") {
                for (result in shown) {
                    for (image in result.images)
                        addJsonObject {
                            put("type", "input_image")
                            put("image_url", RequestContent.dataUrl(image.mimeType, source.bytes(image)))
                        }
                    addJsonObject {
                        put("type", "input_text")
                        put("text", RequestContent.toolImagesLabel(result))
                    }
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
                        addJsonObject {
                            put("type", "input_image")
                            put("image_url", RequestContent.dataUrl(part.mimeType, source.bytes(part)))
                        }
                    }
                    AttachmentKind.PDF -> {
                        texts += RequestContent.attachmentLabel(part)
                        addJsonObject {
                            put("type", "input_file")
                            put("filename", part.name)
                            put("file_data", RequestContent.dataUrl("application/pdf", source.bytes(part)))
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
        for (text in texts.filter(String::isNotBlank))
            addJsonObject {
                put("type", "input_text")
                put("text", text)
            }
    }

    private fun assistantItems(message: ChatMessage): List<JsonElement> {
        val raw = message.raw
        if (raw != null && raw.protocol == Protocol.RESPONSES.id)
            return raw.items
        val items = mutableListOf<JsonElement>()
        message.text.takeIf(String::isNotEmpty)?.let { text ->
            items += buildJsonObject {
                put("type", "message")
                put("role", "assistant")
                putJsonArray("content") {
                    addJsonObject {
                        put("type", "output_text")
                        put("text", text)
                    }
                }
            }
        }
        for (call in message.toolCalls)
            items += buildJsonObject {
                put("type", "function_call")
                put("call_id", call.id)
                put("name", call.name)
                put("arguments", call.arguments)
            }
        return items
    }

    private class StreamState(private val sink: StreamSink) {

        private val items = sortedMapOf<Int, JsonObject>()
        private val text = StringBuilder()
        private val thinking = StringBuilder()
        private var usage = TurnUsage()
        private var status: String? = null
        private var incompleteReason: String? = null
        private var model: String? = null
        /** Argument deltas name the output item; the chat knows calls by their call id. */
        private val callIds = hashMapOf<String, String>()

        fun onEvent(event: SseEvent) {
            if (event.data.isBlank() || event.data == "[DONE]")
                return
            val data = AgentJson.parseToJsonElement(event.data).jsonObject
            when (data.string("type") ?: event.event) {
                "response.output_text.delta" -> data.string("delta")?.let {
                    text.append(it)
                    sink.onText(it)
                }
                "response.reasoning_summary_text.delta" -> data.string("delta")?.let {
                    thinking.append(it)
                    sink.onThinking(it)
                }
                "response.reasoning_summary_part.added" -> if (thinking.isNotEmpty()) {
                    thinking.append("\n\n")
                    sink.onThinking("\n\n")
                }
                "response.output_item.added" -> {
                    val item = data["item"]?.jsonObject ?: return
                    if (item.string("type") == "function_call") {
                        val callId = item.string("call_id").orEmpty()
                        item.string("id")?.let { callIds[it] = callId }
                        sink.onToolCall(callId, toolName(item))
                    }
                }
                "response.function_call_arguments.delta" -> data.string("delta")?.let {
                    val itemId = data.string("item_id").orEmpty()
                    sink.onToolArguments(callIds[itemId] ?: itemId, it)
                }
                "response.output_item.done" -> {
                    val index = data["output_index"]?.jsonPrimitive?.intOrNull ?: items.size
                    data["item"]?.jsonObject?.let { items[index] = it }
                }
                "response.completed", "response.incomplete" -> finish(data["response"]?.jsonObject)
                "response.failed" -> {
                    val error = data["response"]?.jsonObject?.get("error") as? JsonObject
                    throw failure(error, event.data)
                }
                "error" -> throw failure(data["error"] as? JsonObject ?: data, event.data)
            }
        }

        private fun finish(response: JsonObject?) {
            response ?: return
            status = response.string("status")
            model = response.string("model")
            incompleteReason = (response["incomplete_details"] as? JsonObject)?.string("reason")
            // The final output is complete even when an output_item.done event was missed.
            (response["output"] as? JsonArray)?.forEachIndexed { index, item ->
                (item as? JsonObject)?.let { items[index] = it }
            }
            (response["usage"] as? JsonObject)?.let { json ->
                val input = json["input_tokens"]?.jsonPrimitive?.longOrNull ?: 0
                val details = json["input_tokens_details"] as? JsonObject
                val cached = details?.get("cached_tokens")?.jsonPrimitive?.longOrNull ?: 0
                val written = details?.get("cache_write_tokens")?.jsonPrimitive?.longOrNull ?: 0
                usage = TurnUsage(
                    input = (input - cached - written).coerceAtLeast(0),
                    cacheRead = cached,
                    cacheWrite = written,
                    output = json["output_tokens"]?.jsonPrimitive?.longOrNull ?: 0,
                )
            }
        }

        private fun failure(error: JsonObject?, fallback: String): StreamErrorException {
            val code = error?.string("code") ?: error?.string("type")
            return StreamErrorException(
                code,
                error?.string("message") ?: fallback,
                isRetryable = code == "server_error" || code == "rate_limit_exceeded" || code == "server_is_overloaded",
            )
        }

        fun result(): LlmResult {
            val parts = mutableListOf<Part>()
            val raw = mutableListOf<JsonElement>()
            var sawText = false
            for (item in items.values) {
                when (item.string("type")) {
                    "reasoning" -> raw += replayable(item)
                    "message" -> {
                        raw += replayable(item)
                        val itemText = (item["content"] as? JsonArray).orEmpty()
                            .mapNotNull { (it as? JsonObject)?.takeIf { c -> c.string("type") == "output_text" }?.string("text") }
                            .joinToString("")
                        if (itemText.isNotEmpty()) {
                            parts += Part.Text(itemText)
                            sawText = true
                        }
                    }
                    "function_call" -> {
                        raw += replayable(item)
                        parts += Part.ToolCall(item.string("call_id").orEmpty(), toolName(item), item.string("arguments") ?: "{}")
                    }
                }
            }
            if (!sawText && text.isNotEmpty())
                parts.add(0, Part.Text(text.toString()))
            if (thinking.isNotBlank())
                parts.add(0, Part.Thinking(thinking.toString()))
            val hasCalls = parts.any { it is Part.ToolCall }
            val reason = when {
                status == "incomplete" && incompleteReason == "content_filter" -> StopReason.REFUSAL
                status == "incomplete"                                         -> StopReason.MAX_TOKENS
                hasCalls                                                       -> StopReason.TOOL_USE
                status == "completed"                                          -> StopReason.END
                else                                                           -> StopReason.OTHER
            }
            return LlmResult(parts, RawTurn(Protocol.RESPONSES.id, JsonArray(raw)), usage, reason, model,
                refusal = incompleteReason.takeIf { reason == StopReason.REFUSAL })
        }

        /** An output item as input for the next request: ids point at stored items, which `store: false` has none of. */
        private fun replayable(item: JsonObject): JsonObject =
            JsonObject(item.filterKeys { it != "id" && it != "status" })
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
        const val TOOL_NAMESPACE = "starlight"

        /** Shown before the account's own list arrives. */
        val CHATGPT_MODELS = listOf(
            ModelInfo("gpt-6.1-sol", "GPT-6.1 Sol"),
            ModelInfo("gpt-6.1-terra", "GPT-6.1 Terra"),
            ModelInfo("gpt-6-sol", "GPT-6 Sol"),
            ModelInfo("gpt-6-luna", "GPT-6 Luna"),
            ModelInfo("gpt-5.6-sol", "GPT-5.6 Sol"),
        )

        /** A function call's tool name without the namespace some APIs prefix it with. */
        fun toolName(item: JsonObject): String =
            item.string("name").orEmpty().removePrefix("$TOOL_NAMESPACE.")

        /** Older chat models reject reasoning options. */
        private fun isReasoningModel(id: String): Boolean =
            id.startsWith("o") || Regex("^gpt-([5-9]|\\d{2})").containsMatchIn(id)

        private fun isChatModel(id: String): Boolean =
            (id.startsWith("gpt-") || id.startsWith("o")) &&
                listOf("audio", "realtime", "tts", "transcribe", "image", "embedding", "search", "moderation").none { it in id }
    }
}
