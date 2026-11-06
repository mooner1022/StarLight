package dev.mooner.starlight.plugincore.language

import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.project.event.ProjectEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultCodeTest {

    private val language = object : Language() {
        override val id = "test"
        override val name = "Test"
        override val fileExtension = "js"
        override val requireRelease = false
        override fun compile(code: String, apis: List<Api<*>>, project: Project?, classLoader: ClassLoader?): Any = Unit
        override fun callFunction(scope: Any, functionName: String, args: Array<out Any>): Any? = null
        override fun eval(code: String, options: Map<String, String>): Any = Unit
    }

    private fun event(id: String, functionName: String, vararg args: String) = object : ProjectEvent() {
        override val id = id
        override val name = id
        override val functionName = functionName
        override val argTypes: Array<CodeGenerator.Argument<*>> = args.map { CodeGenerator.Argument(it, String::class) }.toTypedArray()
    }

    @Test
    fun keepsFirstDuplicateAndCommentsOutTheRest() {
        val code = language.formatDefaultCode("index.js", listOf(
            event("a.message", "onMessage", "event"),
            event("b.message", "onMessage", "msg"),
            event("a.start", "onStart"),
        ))

        assertTrue(code.contains("\nfunction onMessage(event) {"))
        assertTrue(code.contains("\nfunction onStart() {"))
        assertTrue(code.contains("동일한 함수명의 이벤트가 두 개 이상 발견되어 주석 처리 되었습니다."))
        assertTrue(code.contains("function onMessage(msg) {"))
        assertEquals(1, Regex("(?m)^function onMessage").findAll(code).count())
    }

    @Test
    fun generatesEveryFunctionWithoutDuplicates() {
        val code = language.formatDefaultCode("index.js", listOf(
            event("a.message", "onMessage", "event"),
            event("a.start", "onStart"),
        ))

        assertTrue(code.contains("\nfunction onMessage(event) {"))
        assertTrue(code.contains("\nfunction onStart() {"))
        assertTrue(!code.contains("주석 처리"))
    }
}
