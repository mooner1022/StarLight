package dev.mooner.starlight.agent.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * The steps the agent set out for a task, as its latest update_plan call left them. It isn't stored apart: it is read
 * back from the conversation, so editing an earlier message takes the plan back with it.
 */
data class Plan(val steps: List<Step>) {

    data class Step(val text: String, val status: Status)

    enum class Status(val id: String) {
        PENDING("pending"),
        IN_PROGRESS("in_progress"),
        COMPLETED("completed");

        companion object {
            val ids: List<String> = entries.map { it.id }
        }
    }

    val done: Int
        get() = steps.count { it.status == Status.COMPLETED }

    val current: Step?
        get() = steps.firstOrNull { it.status == Status.IN_PROGRESS }

    val isFinished: Boolean
        get() = steps.all { it.status == Status.COMPLETED }

    /** The chat's one line for an update, such as "2/5 · Write the command module". */
    val summary: String
        get() = listOfNotNull("$done/${steps.size}", current?.text).joinToString(" · ")

    companion object {
        const val TOOL = "update_plan"
        const val MAX_STEPS = 20
        const val MAX_STEP_CHARS = 200
        /** Calls of other tools after which the app reminds the agent of a plan it hasn't updated, and again after as many more. */
        const val REMIND_AFTER_CALLS = 6

        /**
         * The plan in update_plan's [args]; an empty one clears the plan. Throws [IllegalArgumentException] saying what
         * is wrong, for the model to correct.
         */
        fun parse(args: JsonObject): Plan {
            val items = args["plan"] as? JsonArray
                ?: throw IllegalArgumentException("Missing argument: plan (an array of {step, status})")
            require(items.size <= MAX_STEPS) { "A plan has at most $MAX_STEPS steps; group smaller ones together" }
            val steps = items.mapIndexed { index, item ->
                val fields = item as? JsonObject
                    ?: throw IllegalArgumentException("Step ${index + 1} isn't an object with step and status")
                val text = (fields["step"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.trim()
                require(!text.isNullOrEmpty()) { "Step ${index + 1} has no step text" }
                require(text.length <= MAX_STEP_CHARS) { "Step ${index + 1} is longer than $MAX_STEP_CHARS characters" }
                val id = (fields["status"] as? JsonPrimitive)?.contentOrNull
                val status = Status.entries.firstOrNull { it.id == id }
                    ?: throw IllegalArgumentException("Step ${index + 1} has the status $id; use one of ${Status.ids.joinToString()}")
                Step(text, status)
            }
            require(steps.count { it.status == Status.IN_PROGRESS } <= 1) { "Only one step can be in_progress at a time" }
            return Plan(steps)
        }

        /** The plan at the end of [messages]: the last update_plan call that succeeded, or null if none or cleared. */
        fun latest(messages: List<ChatMessage>): Plan? =
            find(messages)?.first

        /**
         * The plan to show for [messages]. One with steps left shows until they are done; a finished one only while
         * the run that finished it is [running], so it doesn't come back with the next, unrelated request.
         */
        fun shown(messages: List<ChatMessage>, running: Boolean): Plan? {
            val (plan, at) = find(messages) ?: return null
            if (!plan.isFinished)
                return plan
            // The run going on began with the user's last message; messages the app sends on its own don't count.
            val runStart = messages.indexOfLast { it.isUserTurn }
            return plan.takeIf { running && at > runStart }
        }

        /**
         * A reminder to go with the results of [turn], the latest: the agent has made [REMIND_AFTER_CALLS] calls of other
         * tools since it last updated a plan that still has steps left, and as many more since the last reminder. Null
         * while the plan is current, or when [turn] updates it.
         */
        fun reminder(messages: List<ChatMessage>, turn: ChatMessage): String? {
            if (turn.toolCalls.any { it.name == TOOL })
                return null
            val found = locate(messages) ?: return null
            if (found.plan.isFinished)
                return null
            val calls = callsSince(messages, found).size
            val reminded = messages.drop(found.index + 1)
                .filter { it.role == Role.TOOL }
                .sumOf { message -> message.parts.count { it is Part.ToolResult && it.reminder != null } }
            if (calls < REMIND_AFTER_CALLS * (reminded + 1))
                return null
            return "Reminder from the app, not the user: your plan hasn't changed in your last $calls tool calls. The user " +
                "sees it above the chat as:\n${checklist(found.plan)}\nIf it is behind, send update_plan along with your " +
                "next tool call: finished steps completed, the one you are on in_progress. Don't mention this reminder."
        }

        /**
         * What to ask of the agent when it has given its final reply while its plan still has steps left, though it
         * worked after it last updated the plan, in the run going on: to put the plan right. Null when the plan is done,
         * or was updated after the work, as when it leaves a step for the user on purpose, or the run did no work, as
         * when the user only says thanks.
         */
        fun leftOpen(messages: List<ChatMessage>): String? {
            val found = locate(messages) ?: return null
            if (found.plan.isFinished)
                return null
            val runStart = messages.indexOfLast { it.isUserTurn }
            if (callsSince(messages, found).none { (index, _) -> index > runStart })
                return null
            return "From the app, not the user: you worked on after your last plan update, and the plan the user sees " +
                "above the chat still shows steps that aren't completed:\n${checklist(found.plan)}\nCall update_plan " +
                "now so it shows where the work stands: steps you finished completed, a step still to do, such as one " +
                "waiting on the user, pending, and steps that no longer apply taken out. Then end your turn without " +
                "writing anything: the user already has your reply."
        }

        /**
         * Whether [messages] end on the results of a turn that only updated the plan, every update taken, as a run ends
         * after the agent puts its plan right following its reply. Nothing is left to continue then; an update that was
         * refused leaves the agent to see why and try again.
         */
        fun endsOnUpdate(messages: List<ChatMessage>): Boolean {
            val turns = messages.filter { message -> message.parts.any { it !is Part.Notice } }
            val last = turns.lastOrNull()?.takeIf { it.role == Role.TOOL } ?: return false
            val turn = turns.lastOrNull { it.role == Role.ASSISTANT } ?: return false
            val answered = last.parts.filterIsInstance<Part.ToolResult>().filter { !it.isError }.mapTo(HashSet()) { it.callId }
            return turn.toolCalls.isNotEmpty() && turn.toolCalls.all { it.name == TOOL && it.id in answered }
        }

        /** The plan as the agent reads it in a reminder: one step a line, with its status. */
        private fun checklist(plan: Plan): String =
            plan.steps.joinToString("\n") { step ->
                when (step.status) {
                    Status.COMPLETED   -> "[x] ${step.text}"
                    Status.IN_PROGRESS -> "[>] ${step.text} (in progress)"
                    Status.PENDING     -> "[ ] ${step.text}"
                }
            }

        /** The calls of other tools the agent made after the update that set [found], with the index of their message. */
        private fun callsSince(messages: List<ChatMessage>, found: Found): List<Pair<Int, Part.ToolCall>> =
            (found.index until messages.size).flatMap { index ->
                val message = messages[index]
                if (message.role != Role.ASSISTANT)
                    return@flatMap emptyList()
                val calls = if (index == found.index) message.toolCalls.dropWhile { it.id != found.callId }.drop(1)
                    else message.toolCalls
                calls.filter { it.name != TOOL }.map { index to it }
            }

        /** The latest plan, with where the call that set it is. */
        private class Found(val plan: Plan, val index: Int, val callId: String)

        private fun find(messages: List<ChatMessage>): Pair<Plan, Int>? =
            locate(messages)?.let { it.plan to it.index }

        private fun locate(messages: List<ChatMessage>): Found? {
            val succeeded = messages.asSequence()
                .filter { it.role == Role.TOOL }
                .flatMap { it.parts.filterIsInstance<Part.ToolResult>() }
                .filter { it.name == TOOL && !it.isError }
                .mapTo(HashSet()) { it.callId }
            if (succeeded.isEmpty())
                return null
            for (index in messages.indices.reversed()) {
                val message = messages[index]
                if (message.role != Role.ASSISTANT)
                    continue
                val call = message.toolCalls.lastOrNull { it.name == TOOL && it.id in succeeded } ?: continue
                val args = runCatching { AgentJson.parseToJsonElement(call.arguments) as? JsonObject }.getOrNull() ?: return null
                val plan = runCatching { parse(args) }.getOrNull()?.takeIf { it.steps.isNotEmpty() } ?: return null
                return Found(plan, index, call.id)
            }
            return null
        }
    }
}
