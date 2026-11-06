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

    private fun user(text: String) = ChatMessage("u${++nextId}", Role.USER, listOf(Part.Text(text)), 0)
    private fun reply(text: String) = ChatMessage("r${++nextId}", Role.ASSISTANT, listOf(Part.Text(text)), 0)

    /** A turn calling read_file [calls] times, and the results, the last with [reminder]. */
    private fun work(calls: Int, reminder: String? = null): List<ChatMessage> {
        val ids = List(calls) { "call_${++nextId}" }
        return listOf(
            ChatMessage("a${ids.first()}", Role.ASSISTANT, ids.map { Part.ToolCall(it, "read_file", "{}") }, 0),
            ChatMessage("t${ids.first()}", Role.TOOL, ids.mapIndexed { index, id ->
                Part.ToolResult(id, "read_file", "ok", reminder = reminder.takeIf { index == ids.lastIndex })
            }, 0),
        )
    }

    /** The reminder for the results of the last turn of [messages]. */
    private fun reminder(messages: List<ChatMessage>): String? =
        Plan.reminder(messages, messages.last { it.role == Role.ASSISTANT })

    private val open = args("Read the bot" to "completed", "Add !weather" to "in_progress", "Test it" to "pending")

    @Test
    fun aPlanLeftBehindIsRemindedOfEverySoManyCalls() {
        val start = listOf(user("add !weather")) + update(open)
        assertNull(reminder(start + work(Plan.REMIND_AFTER_CALLS - 1)))
        val due = reminder(start + work(Plan.REMIND_AFTER_CALLS))!!
        assertTrue("[x] Read the bot" in due && "[>] Add !weather (in progress)" in due && "[ ] Test it" in due)
        // Once reminded, the next one waits for as many calls again.
        val reminded = start + work(Plan.REMIND_AFTER_CALLS, reminder = due)
        assertNull(reminder(reminded + work(Plan.REMIND_AFTER_CALLS - 1)))
        assertTrue(reminder(reminded + work(Plan.REMIND_AFTER_CALLS)) != null)
        // An update starts over, and a turn that updates the plan isn't reminded of it.
        assertNull(reminder(reminded + update(open) + work(2)))
        val updating = ChatMessage("aboth", Role.ASSISTANT, listOf(Part.ToolCall("c1", "read_file", "{}"), Part.ToolCall("c2", Plan.TOOL, open)), 0)
        assertNull(Plan.reminder(start + work(Plan.REMIND_AFTER_CALLS) + updating, updating))
        // A finished plan needs no reminder.
        assertNull(reminder(listOf(user("go")) + update(args("A" to "completed")) + work(10)))
    }

    @Test
    fun aReplyAfterWorkWithStepsLeftAsksToPutThePlanRight() {
        val worked = listOf(user("add !weather")) + update(open) + work(3) + reply("Done!")
        val ask = Plan.leftOpen(worked)!!
        assertTrue("[>] Add !weather (in progress)" in ask)
        // Updated after the work: what is left is left on purpose.
        assertNull(Plan.leftOpen(listOf(user("add !weather")) + update(open) + work(3) + update(open) + reply("Your turn")))
        // A later run that does no work, such as thanks, isn't asked again.
        assertNull(Plan.leftOpen(worked + update(open) + user("thanks") + reply("You're welcome")))
        assertNull(Plan.leftOpen(listOf(user("hi")) + update(open) + work(3) + reply("ok") + user("thanks") + reply("welcome")))
        // A later run that works on is.
        assertTrue(Plan.leftOpen(worked + user("go on") + work(1) + reply("More done")) != null)
        assertNull(Plan.leftOpen(listOf(user("go")) + update(args("A" to "completed")) + work(2) + reply("Done")))
    }

    @Test
    fun aRunCanEndOnAPlanUpdate() {
        val base = listOf(user("go")) + update(open) + work(2) + reply("Done")
        assertTrue(Plan.endsOnUpdate(base + user("put it right") + update(args("A" to "completed"))))
        assertFalse(Plan.endsOnUpdate(base))
        assertFalse(Plan.endsOnUpdate(base + work(1)))
        // A refused update is for the agent to correct.
        assertFalse(Plan.endsOnUpdate(base + update(args("A" to "done"), error = true)))
        // A notice after it changes nothing.
        val notice = ChatMessage("n1", Role.ASSISTANT, listOf(Part.Notice(dev.mooner.starlight.agent.model.NoticeKind.INFO, "x")), 0)
        assertTrue(Plan.endsOnUpdate(base + update(args("A" to "completed")) + notice))
        // A message the user sent after the app asked, such as one queued while the reply came, is still to be answered.
        val ask = ChatMessage("ask", Role.USER, listOf(Part.Context(Part.Context.INSTRUCTIONS, "put it right")), 0)
        assertTrue(Plan.endsOnUpdate(base + ask + update(args("A" to "completed")), request = ask.id))
        assertFalse(Plan.endsOnUpdate(base + ask + user("also add !news") + update(args("A" to "completed")), request = ask.id))
        // A request taken out again leaves nothing to end on.
        assertFalse(Plan.endsOnUpdate(base + user("also add !news") + update(args("A" to "completed")), request = ask.id))
    }

    @Test
    fun aReminderGoesToTheModelButNotIntoTheOutput() {
        val result = Part.ToolResult("c1", "read_file", "file text", reminder = "update your plan")
        assertEquals("file text\n\nupdate your plan", dev.mooner.starlight.agent.provider.RequestContent.toolOutput(result))
        assertEquals("file text", dev.mooner.starlight.agent.provider.RequestContent.toolOutput(result.copy(reminder = null)))
        assertFalse("reminder" in AgentJson.encodeToString(Part.ToolResult.serializer(), result.copy(reminder = null)))
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
