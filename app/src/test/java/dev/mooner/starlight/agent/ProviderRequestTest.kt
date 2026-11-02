package dev.mooner.starlight.agent

import dev.mooner.starlight.agent.model.AttachmentKind
import dev.mooner.starlight.agent.model.ChatMessage
import dev.mooner.starlight.agent.model.Part
import dev.mooner.starlight.agent.model.RawTurn
import dev.mooner.starlight.agent.model.Role
import dev.mooner.starlight.agent.provider.AnthropicClient
import dev.mooner.starlight.agent.provider.AttachmentSource
import dev.mooner.starlight.agent.provider.ChatCompletionsClient
import dev.mooner.starlight.agent.provider.LlmRequest
import dev.mooner.starlight.agent.provider.Protocol
import dev.mooner.starlight.agent.provider.ResponsesClient
import dev.mooner.starlight.agent.provider.ToolSpec
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The request bodies each provider gets: caching markers, tools, history replay and attachments. */
class ProviderRequestTest {

    private val attachments = object : AttachmentSource {
        override fun bytes(attachment: Part.Attachment) = byteArrayOf(1, 2, 3)
        override fun pdfPages(attachment: Part.Attachment) = listOf(byteArrayOf(4, 5))
    }

    private val tools = listOf(
        ToolSpec("write_file", "Writes", buildJsonObject { put("type", "object") }),
        ToolSpec("list_projects", "Lists", buildJsonObject { put("type", "object") }),
    )

    private val image = Part.Attachment("att_1", "shot.png", "image/png", 3, AttachmentKind.IMAGE)

    private fun request(messages: List<ChatMessage>, model: String = "claude-opus-5-5", effort: String? = null) = LlmRequest(
        conversationId = "c1",
        model = model,
        system = "SYSTEM",
        tools = tools,
        messages = messages,
        effort = effort,
        maxOutputTokens = 64_000,
        attachments = attachments,
    )

    private fun user(vararg parts: Part) = ChatMessage("u" + parts.size, Role.USER, parts.toList(), 0)

    private val toolTurn = ChatMessage(
        "a1", Role.ASSISTANT,
        listOf(Part.Text("Looking"), Part.ToolCall("call:1", "list_projects", "{}")),
        0,
    )
    private val toolResults = ChatMessage("t1", Role.TOOL, listOf(Part.ToolResult("call:1", "list_projects", "[]")), 0)

    @Test
    fun anthropicCachesSystemAndConversation() {
        val body = AnthropicClient("key").buildBody(request(listOf(user(Part.Text("hi")))))
        val system = body["system"]!!.jsonArray.single().jsonObject
        assertEquals("ephemeral", system["cache_control"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("ephemeral", body["cache_control"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        // Sorted, so that the cached prefix stays the same.
        assertEquals(listOf("list_projects", "write_file"), body["tools"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content })
        assertTrue(body["tools"]!!.jsonArray.all { it.jsonObject["eager_input_streaming"]!!.jsonPrimitive.content == "true" })
        assertEquals("adaptive", body["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("default", body["fallbacks"]!!.jsonPrimitive.content)
        assertNull(body["temperature"])
    }

    @Test
    fun anthropicLeavesUnknownModelsWithoutThinking() {
        val body = AnthropicClient("key").buildBody(request(listOf(user(Part.Text("hi"))), model = "claude-3-haiku", effort = "high"))
        assertNull(body["thinking"])
        assertNull(body["output_config"])
        assertNull(body["fallbacks"])
    }

    @Test
    fun anthropicReplaysRawTurnsAndMergesResultsWithNextMessage() {
        val raw = buildJsonArray {
            add(buildJsonObject {
                put("type", "thinking")
                put("thinking", "hmm")
                put("signature", "sig")
            })
            add(buildJsonObject {
                put("type", "tool_use")
                put("id", "toolu_1")
                put("name", "list_projects")
                put("input", buildJsonObject {})
            })
        }
        val assistant = toolTurn.copy(raw = RawTurn(Protocol.ANTHROPIC.id, raw))
        val results = ChatMessage("t1", Role.TOOL, listOf(Part.ToolResult("toolu_1", "list_projects", "[]")), 0)
        val messages = AnthropicClient("key").buildBody(request(listOf(user(Part.Text("hi")), assistant, results, user(Part.Text("next")))))["messages"]!!.jsonArray
        assertEquals(listOf("user", "assistant", "user"), messages.map { it.jsonObject["role"]!!.jsonPrimitive.content })
        val replayed = messages[1].jsonObject["content"]!!.jsonArray
        assertEquals("sig", replayed[0].jsonObject["signature"]!!.jsonPrimitive.content)
        val last = messages[2].jsonObject["content"]!!.jsonArray
        assertEquals(listOf("tool_result", "text"), last.map { it.jsonObject["type"]!!.jsonPrimitive.content })
    }

    @Test
    fun anthropicConvertsOtherProvidersTurnsAndSanitizesIds() {
        val messages = AnthropicClient("key").buildBody(request(listOf(user(Part.Text("hi")), toolTurn, toolResults)))["messages"]!!.jsonArray
        val toolUse = messages[1].jsonObject["content"]!!.jsonArray.last().jsonObject
        assertEquals("call_1", toolUse["id"]!!.jsonPrimitive.content)
        val result = messages[2].jsonObject["content"]!!.jsonArray.single().jsonObject
        assertEquals("call_1", result["tool_use_id"]!!.jsonPrimitive.content)
    }

    @Test
    fun anthropicPutsImagesBeforeText() {
        val content = AnthropicClient("key").buildBody(request(listOf(user(image, Part.Text("what is this?")))))["messages"]!!
            .jsonArray.single().jsonObject["content"]!!.jsonArray
        assertEquals("image", content.first().jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("AQID", content.first().jsonObject["source"]!!.jsonObject["data"]!!.jsonPrimitive.content)
        assertTrue(content.any { it.jsonObject["text"]?.jsonPrimitive?.content?.contains("att_1") == true })
    }

    @Test
    fun chatGptFollowsThePlanUsageRules() {
        val body = ResponsesClient({ "token" }, chatGpt = true).buildBody(request(listOf(user(Part.Text("hi"))), model = "gpt-6.1-sol", effort = "high"))
        assertEquals("false", body["store"]!!.jsonPrimitive.content)
        assertEquals("true", body["stream"]!!.jsonPrimitive.content)
        assertNull(body["max_output_tokens"])
        assertNull(body["temperature"])
        assertEquals("c1", body["prompt_cache_key"]!!.jsonPrimitive.content)
        assertEquals("SYSTEM", body["instructions"]!!.jsonPrimitive.content)
        val namespace = body["tools"]!!.jsonArray.single().jsonObject
        assertEquals("namespace", namespace["type"]!!.jsonPrimitive.content)
        assertEquals(2, namespace["tools"]!!.jsonArray.size)
        assertEquals("reasoning.encrypted_content", body["include"]!!.jsonArray.single().jsonPrimitive.content)
        assertTrue(body["input"]!!.jsonArray.none { it.jsonObject["role"]?.jsonPrimitive?.content == "system" })
    }

    @Test
    fun openAiKeepsPlainFunctionTools() {
        val body = ResponsesClient({ "key" }).buildBody(request(listOf(user(Part.Text("hi"))), model = "gpt-6.1-sol"))
        assertEquals(listOf("function", "function"), body["tools"]!!.jsonArray.map { it.jsonObject["type"]!!.jsonPrimitive.content })
    }

    @Test
    fun responsesSendsToolResultsAsCallOutputs() {
        val input = ResponsesClient({ "key" }).buildBody(request(listOf(user(Part.Text("hi")), toolTurn, toolResults)))["input"]!!.jsonArray
        val types = input.map { it.jsonObject["type"]!!.jsonPrimitive.content }
        assertEquals(listOf("message", "message", "function_call", "function_call_output"), types)
        assertEquals("call:1", input.last().jsonObject["call_id"]!!.jsonPrimitive.content)
    }

    @Test
    fun openRouterMarksClaudeCacheBreakpoints() {
        val body = ChatCompletionsClient("key", "https://openrouter.ai/api/v1", ChatCompletionsClient.Flavor.OPENROUTER)
            .buildBody(request(listOf(user(Part.Text("hi"))), model = "anthropic/claude-opus-5.5"))
        val messages = body["messages"]!!.jsonArray
        val system = messages.first().jsonObject["content"]!!.jsonArray.single().jsonObject
        assertTrue(system.containsKey("cache_control"))
        val last = messages.last().jsonObject["content"]!!.jsonArray.last().jsonObject
        assertTrue(last.containsKey("cache_control"))
    }

    @Test
    fun genericChatCompletionsSendPdfPagesAsImagesAndToolMessages() {
        val pdf = Part.Attachment("att_2", "doc.pdf", "application/pdf", 3, AttachmentKind.PDF)
        val body = ChatCompletionsClient(null, "http://localhost:1234/v1")
            .buildBody(request(listOf(user(pdf, Part.Text("read")), toolTurn, toolResults), model = "local"))
        val messages = body["messages"]!!.jsonArray
        assertEquals("system", messages.first().jsonObject["role"]!!.jsonPrimitive.content)
        assertFalse(messages.first().jsonObject["content"] is JsonArray)
        val userContent = messages[1].jsonObject["content"]!!.jsonArray
        assertEquals("image_url", userContent.first().jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("tool", messages.last().jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals(JsonObject::class, messages[2].jsonObject::class)
    }
}
