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
            val runStart = messages.indexOfLast { message ->
                message.role == Role.USER && message.parts.any { it !is Part.Context || it.label !in Part.Context.HIDDEN }
            }
            return plan.takeIf { running && at > runStart }
        }

        /** The latest plan, with the index of the message whose call set it. */
        private fun find(messages: List<ChatMessage>): Pair<Plan, Int>? {
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
                return plan to index
            }
            return null
        }
    }
}
