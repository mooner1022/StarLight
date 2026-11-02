package dev.mooner.starlight.agent.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * What ask_user asks: one to [MAX_QUESTIONS] questions, answered one after another, each with up to [MAX_OPTIONS]
 * options and, where [Item.multiple], several of them at once. The user may always write their own answer instead.
 */
data class Questions(val items: List<Item>) {

    data class Item(val question: String, val header: String?, val options: List<Option>, val multiple: Boolean)

    data class Option(val label: String, val description: String?)

    companion object {
        const val MAX_QUESTIONS = 4
        const val MAX_OPTIONS = 4
        private const val MAX_HEADER_CHARS = 16

        /**
         * The questions in ask_user's [args]. Also takes one question given directly, and options given as plain
         * strings. Throws [IllegalArgumentException] saying what to fix.
         */
        fun parse(args: JsonObject): Questions {
            val list = (args["questions"] as? JsonArray)?.map { it as? JsonObject ?: throw IllegalArgumentException(
                "Each question is an object with question and options") }
                ?: listOf(args.takeIf { it["question"] != null }
                    ?: throw IllegalArgumentException("Missing argument: questions (1 to $MAX_QUESTIONS of {question, options})"))
            require(list.size in 1..MAX_QUESTIONS) { "Ask 1 to $MAX_QUESTIONS questions at a time" }
            return Questions(list.mapIndexed { index, item -> item(index + 1, item) })
        }

        private fun item(number: Int, item: JsonObject): Item {
            val question = item.string("question")
            require(!question.isNullOrBlank()) { "Question $number has no question text" }
            val options = (item["options"] as? JsonArray).orEmpty().mapNotNull(::option)
            require(options.size <= MAX_OPTIONS) {
                "Question $number has ${options.size} options; give at most $MAX_OPTIONS, the user can always write their own"
            }
            return Item(
                question = question.trim(),
                header = item.string("header")?.trim()?.take(MAX_HEADER_CHARS)?.takeIf(String::isNotEmpty),
                options = options,
                multiple = (item["multiple"] as? JsonPrimitive)?.booleanOrNull == true,
            )
        }

        private fun option(element: JsonElement): Option? = when (element) {
            is JsonPrimitive -> element.contentOrNull?.trim()?.takeIf(String::isNotEmpty)?.let { Option(it, null) }
            is JsonObject    -> element.string("label")?.trim()?.takeIf(String::isNotEmpty)
                ?.let { Option(it, element.string("description")?.trim()?.takeIf(String::isNotEmpty)) }
            else             -> null
        }

        /** The tool result for [answers], one per question in order, or for a question the user skipped (null). */
        fun result(questions: Questions, answers: List<String>?): String {
            if (answers == null)
                return "The user skipped the question. Decide what fits best yourself, or ask in your reply."
            if (questions.items.size == 1)
                return "The user answered \"${questions.items[0].question}\": ${answers.firstOrNull().orEmpty()}"
            return "The user's answers:\n" + questions.items.mapIndexed { index, item ->
                "${index + 1}. ${item.question}\n   ${answers.getOrNull(index).orEmpty()}"
            }.joinToString("\n")
        }

        private fun JsonObject.string(name: String): String? =
            (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    }
}
