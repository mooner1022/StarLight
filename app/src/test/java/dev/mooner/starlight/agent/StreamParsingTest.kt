package dev.mooner.starlight.agent

import dev.mooner.starlight.agent.model.Part
import dev.mooner.starlight.agent.net.Http
import dev.mooner.starlight.agent.net.SseEvent
import dev.mooner.starlight.agent.provider.AnthropicClient
import dev.mooner.starlight.agent.provider.ChatCompletionsClient
import dev.mooner.starlight.agent.provider.ResponsesClient
import dev.mooner.starlight.agent.provider.StopReason
import dev.mooner.starlight.agent.provider.StreamErrorException
import dev.mooner.starlight.agent.provider.StreamSink
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Streams recorded in each provider's documented event format, folded into turns. */
class StreamParsingTest {

    private fun events(vararg data: String) = data.map { SseEvent(null, it) }

    @Test
    fun readsServerSentEvents() = runBlocking {
        val text = "event: a\ndata: one\ndata: two\n\n: ping\n\ndata:three\n\n"
        val events = mutableListOf<SseEvent>()
        Http.readSse(text.reader().buffered()) { events += it }
        assertEquals(listOf("one\ntwo", "three"), events.map { it.data })
        assertEquals("a", events.first().event)
    }

    @Test
    fun anthropicStreamWithThinkingAndToolUse() {
        val texts = StringBuilder()
        val sink = object : StreamSink {
            override fun onText(delta: String) { texts.append(delta) }
        }
        val result = AnthropicClient("key").parse(events(
            """{"type":"message_start","message":{"model":"claude-opus-5-5","usage":{"input_tokens":12,"cache_read_input_tokens":3000,"cache_creation_input_tokens":40,"output_tokens":1}}}""",
            """{"type":"content_block_start","index":0,"content_block":{"type":"thinking","thinking":"","signature":""}}""",
            """{"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"Let me check."}}""",
            """{"type":"content_block_delta","index":0,"delta":{"type":"signature_delta","signature":"abc"}}""",
            """{"type":"content_block_stop","index":0}""",
            """{"type":"content_block_start","index":1,"content_block":{"type":"text","text":""}}""",
            """{"type":"content_block_delta","index":1,"delta":{"type":"text_delta","text":"On it"}}""",
            """{"type":"content_block_start","index":2,"content_block":{"type":"tool_use","id":"toolu_1","name":"read_file","input":{}}}""",
            """{"type":"content_block_delta","index":2,"delta":{"type":"input_json_delta","partial_json":"{\"project\":"}}""",
            """{"type":"content_block_delta","index":2,"delta":{"type":"input_json_delta","partial_json":"\"bot\"}"}}""",
            """{"type":"message_delta","delta":{"stop_reason":"tool_use"},"usage":{"output_tokens":55}}""",
            """{"type":"message_stop"}""",
        ), sink)
        assertEquals("On it", texts.toString())
        assertEquals(StopReason.TOOL_USE, result.stopReason)
        assertEquals(3000, result.usage.cacheRead)
        assertEquals(40, result.usage.cacheWrite)
        assertEquals(55, result.usage.output)
        val call = result.parts.filterIsInstance<Part.ToolCall>().single()
        assertEquals("toolu_1", call.id)
        assertEquals("{\"project\":\"bot\"}", call.arguments)
        val raw = result.raw!!.items
        assertEquals("abc", raw[0].jsonObject["signature"]!!.jsonPrimitive.content)
        assertEquals("bot", raw[2].jsonObject["input"]!!.jsonObject["project"]!!.jsonPrimitive.content)
    }

    @Test
    fun anthropicOverloadErrorIsRetryable() {
        try {
            AnthropicClient("key").parse(events("""{"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}"""))
            fail("Expected an error")
        } catch (e: StreamErrorException) {
            assertTrue(e.isRetryable)
        }
    }

    @Test
    fun anthropicRefusal() {
        val result = AnthropicClient("key").parse(events(
            """{"type":"message_start","message":{"model":"claude-opus-5-5","usage":{"input_tokens":5}}}""",
            """{"type":"message_delta","delta":{"stop_reason":"refusal","stop_details":{"type":"refusal","category":"cyber"}},"usage":{"output_tokens":0}}""",
        ))
        assertEquals(StopReason.REFUSAL, result.stopReason)
        assertEquals("cyber", result.refusal)
    }

    @Test
    fun responsesStreamWithReasoningAndFunctionCall() {
        val result = ResponsesClient({ "key" }).parse(events(
            """{"type":"response.reasoning_summary_text.delta","delta":"Checking"}""",
            """{"type":"response.output_item.added","output_index":1,"item":{"type":"function_call","id":"fc_1","call_id":"call_1","name":"starlight.list_projects","arguments":""}}""",
            """{"type":"response.function_call_arguments.delta","item_id":"fc_1","delta":"{}"}""",
            """{"type":"response.completed","response":{"status":"completed","model":"gpt-6.1-sol","output":[
                {"type":"reasoning","id":"rs_1","summary":[],"encrypted_content":"ENC"},
                {"type":"function_call","id":"fc_1","call_id":"call_1","name":"list_projects","arguments":"{}","status":"completed"}],
                "usage":{"input_tokens":5000,"input_tokens_details":{"cached_tokens":4500},"output_tokens":20}}}""".replace("\n", ""),
        ))
        assertEquals(StopReason.TOOL_USE, result.stopReason)
        assertEquals(4500, result.usage.cacheRead)
        assertEquals(500, result.usage.input)
        assertEquals("list_projects", result.parts.filterIsInstance<Part.ToolCall>().single().name)
        val raw = result.raw!!.items.map { it.jsonObject }
        assertEquals("ENC", raw[0]["encrypted_content"]!!.jsonPrimitive.content)
        // Without store, ids point at nothing; replayed items leave them out.
        assertTrue(raw.none { "id" in it || "status" in it })
        assertTrue(result.parts.first() is Part.Thinking)
    }

    @Test
    fun responsesFailureCarriesTheCode() {
        try {
            ResponsesClient({ "key" }).parse(events("""{"type":"response.failed","response":{"error":{"code":"server_error","message":"boom"}}}"""))
            fail("Expected an error")
        } catch (e: StreamErrorException) {
            assertEquals("server_error", e.type)
            assertTrue(e.isRetryable)
        }
    }

    @Test
    fun chatCompletionsAssembleToolCallsAndKeepExtraFields() {
        val result = ChatCompletionsClient("key", "https://example.com/v1").parse(events(
            """{"model":"gemini-x","choices":[{"delta":{"content":"Hi "}}]}""",
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_a","type":"function","function":{"name":"read_file","arguments":"{\"pro"}, "extra_content":{"google":{"thought_signature":"SIG"}}}]}}]}""",
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"ject\":\"x\"}"}}]},"finish_reason":"tool_calls"}]}""",
            """{"choices":[],"usage":{"prompt_tokens":100,"completion_tokens":7,"prompt_tokens_details":{"cached_tokens":64}}}""",
            "[DONE]",
        ))
        assertEquals(StopReason.TOOL_USE, result.stopReason)
        val call = result.parts.filterIsInstance<Part.ToolCall>().single()
        assertEquals("{\"project\":\"x\"}", call.arguments)
        assertEquals(64, result.usage.cacheRead)
        assertEquals(36, result.usage.input)
        val rawCall = result.raw!!.items.single().jsonObject["tool_calls"]!!.toString()
        assertTrue(rawCall.contains("thought_signature"))
    }
}
