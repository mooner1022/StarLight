package dev.mooner.starlight.agent

import dev.mooner.starlight.agent.model.AgentJson
import dev.mooner.starlight.agent.model.ChatMessage
import dev.mooner.starlight.agent.model.Part
import dev.mooner.starlight.agent.model.Plan
import dev.mooner.starlight.agent.model.Role
import dev.mooner.starlight.agent.tools.ParamsBuilder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The plan the agent shows as a board: what update_plan accepts, and which call the board follows. */
class PlanTest {

    private fun args(vararg steps: Pair<String, String>): String =
        """{"plan":[${steps.joinToString(",") { (step, status) -> """{"step":"$step","status":"$status"}""" }}]}"""

    private fun parse(json: String): Plan =
        Plan.parse(AgentJson.parseToJsonElement(json) as JsonObject)

    private var nextId = 0

    /** An assistant turn calling update_plan with [arguments], and the tool message answering it. */
    private fun update(arguments: String, error: Boolean = false): List<ChatMessage> {
        val id = "call_${++nextId}"
        return listOf(
            ChatMessage("a$id", Role.ASSISTANT, listOf(Part.ToolCall(id, Plan.TOOL, arguments)), 0),
            ChatMessage("t$id", Role.TOOL, listOf(Part.ToolResult(id, Plan.TOOL, if (error) "bad" else "ok", isError = error)), 0),
        )
    }

    @Test
    fun parsesStepsAndCountsProgress() {
        val plan = parse(args("Read the bot" to "completed", "Add !weather" to "in_progress", "Test it" to "pending"))
        assertEquals(3, plan.steps.size)
        assertEquals(1, plan.done)
        assertEquals("Add !weather", plan.current?.text)
        assertEquals("1/3 · Add !weather", plan.summary)
        assertFalse(plan.isFinished)
        assertTrue(parse(args("Done" to "completed")).isFinished)
    }

    @Test
    fun refusesPlansTheBoardCouldNotShow() {
        assertThrows(IllegalArgumentException::class.java) { parse(args("A" to "in_progress", "B" to "in_progress")) }
        val wrongStatus = assertThrows(IllegalArgumentException::class.java) { parse(args("A" to "done")) }
        assertTrue(wrongStatus.message!!.contains("in_progress"))
        assertThrows(IllegalArgumentException::class.java) { parse(args(" " to "pending")) }
        assertThrows(IllegalArgumentException::class.java) { parse("{}") }
        assertThrows(IllegalArgumentException::class.java) {
            parse(args(*Array(Plan.MAX_STEPS + 1) { "Step $it" to "pending" }))
        }
        assertTrue(parse("""{"plan":[]}""").steps.isEmpty())
    }

    @Test
    fun followsTheLastUpdateThatSucceeded() {
        val first = update(args("A" to "in_progress", "B" to "pending"))
        val second = update(args("A" to "completed", "B" to "in_progress"))
        val rejected = update(args("A" to "in_progress", "B" to "in_progress"), error = true)
        assertNull(Plan.latest(emptyList()))
        assertEquals("1/2 · B", Plan.latest(first + second + rejected)?.summary)
        // Editing a message drops the later turns, and the board goes back with them.
        assertEquals("0/2 · A", Plan.latest(first)?.summary)
        // An empty plan clears the board.
        assertNull(Plan.latest(first + update("""{"plan":[]}""")))
        // A call still waiting for its result doesn't count yet.
        assertEquals("0/2 · A", Plan.latest(first + second.first())?.summary)
    }

    @Test
    fun aFinishedPlanShowsOnlyWhileItsRunGoesOn() {
        val ask = ChatMessage("u1", Role.USER, listOf(Part.Text("make a bot")), 0)
        val finished = listOf(ask) + update(args("A" to "completed", "B" to "completed"))
        assertEquals("2/2", Plan.shown(finished, running = true)?.summary)
        assertNull(Plan.shown(finished, running = false))
        // The next request starts a run of its own; the old plan stays away.
        val next = finished + ChatMessage("u2", Role.USER, listOf(Part.Text("thanks")), 0)
        assertNull(Plan.shown(next, running = true))
        // A plan with steps left stays, so the user sees what is still to do.
        val unfinished = listOf(ask) + update(args("A" to "completed", "B" to "pending")) +
            ChatMessage("u2", Role.USER, listOf(Part.Text("go on")), 0)
        assertEquals("1/2", Plan.shown(unfinished, running = false)?.summary)
        assertEquals("1/2", Plan.shown(unfinished, running = true)?.summary)
        // A message the app sends on its own, such as asking for the missing reply, is part of the same run.
        val nudged = finished + ChatMessage("u3", Role.USER, listOf(Part.Context(Part.Context.NUDGE, "reply")), 0)
        assertEquals("2/2", Plan.shown(nudged, running = true)?.summary)
    }

    @Test
    fun schemaDescribesStepObjectsWithAllowedStatuses() {
        val schema = ParamsBuilder().apply {
            objectArray("plan", "The steps.") {
                string("step", "What the step does.")
                string("status", "Its status.", enum = Plan.Status.ids)
            }
        }.build()
        val plan = schema["properties"]!!.jsonObject["plan"]!!.jsonObject
        assertEquals("array", plan["type"]!!.jsonPrimitive.content)
        val item = plan["items"]!!.jsonObject
        assertEquals(listOf("step", "status"), item["required"]!!.jsonArray.map { it.jsonPrimitive.content })
        val status = item["properties"]!!.jsonObject["status"]!!.jsonObject
        assertEquals(Plan.Status.ids, status["enum"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf("plan"), schema["required"]!!.jsonArray.map { it.jsonPrimitive.content })
    }
}
