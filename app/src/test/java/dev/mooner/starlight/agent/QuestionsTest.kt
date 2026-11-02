package dev.mooner.starlight.agent

import dev.mooner.starlight.agent.model.AgentJson
import dev.mooner.starlight.agent.model.Questions
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** What ask_user accepts, and what the model gets back. */
class QuestionsTest {

    private fun parse(json: String) = Questions.parse(AgentJson.parseToJsonElement(json) as JsonObject)

    @Test
    fun takesSeveralQuestionsWithOptionsAndDescriptions() {
        val questions = parse("""{"questions":[
            {"question":"Prefix?","header":"Prefix","options":[{"label":"!","description":"Most common"},{"label":"/"}]},
            {"question":"Which commands?","options":[{"label":"help"},{"label":"weather"},{"label":"dice"}],"multiple":true}
        ]}""")
        assertEquals(2, questions.items.size)
        assertEquals("Prefix", questions.items[0].header)
        assertEquals("Most common", questions.items[0].options[0].description)
        assertNull(questions.items[0].options[1].description)
        assertTrue(questions.items[1].multiple)
    }

    @Test
    fun alsoTakesOneQuestionGivenDirectlyWithPlainOptions() {
        val questions = parse("""{"question":"Prefix?","options":["!","/"]}""")
        assertEquals(listOf("!", "/"), questions.items.single().options.map { it.label })
    }

    @Test
    fun refusesWhatTheCardsCantShow() {
        val five = assertThrows(IllegalArgumentException::class.java) {
            parse("""{"question":"Pick","options":["a","b","c","d","e"]}""")
        }
        assertTrue(five.message!!.contains("at most ${Questions.MAX_OPTIONS}"))
        assertThrows(IllegalArgumentException::class.java) {
            parse("""{"questions":[${(1..5).joinToString(",") { """{"question":"Q$it","options":["a","b"]}""" }}]}""")
        }
        assertThrows(IllegalArgumentException::class.java) { parse("""{"questions":[{"options":["a"]}]}""") }
        assertThrows(IllegalArgumentException::class.java) { parse("{}") }
    }

    @Test
    fun answersComeBackInOrder() {
        val one = parse("""{"question":"Prefix?","options":["!","/"]}""")
        assertEquals("The user answered \"Prefix?\": /", Questions.result(one, listOf("/")))
        val two = parse("""{"questions":[{"question":"A?","options":["x","y"]},{"question":"B?","options":["z","w"]}]}""")
        assertEquals("The user's answers:\n1. A?\n   x\n2. B?\n   z, my own", Questions.result(two, listOf("x", "z, my own")))
        assertTrue(Questions.result(two, null).startsWith("The user skipped"))
    }
}
