package dev.mooner.starlight.api.msgbot

import dev.mooner.starlight.api.api2.appDelayOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

class AppBroadcastApiTest {

    @Test
    fun delayIsCutToAWholeNumber() {
        assertEquals(1500L, appDelayOf(1500))
        assertEquals(1L, appDelayOf(1.9))
        assertEquals(0L, appDelayOf(-0.5))
        assertEquals(0L, appDelayOf(Double.NaN))
        assertEquals(Long.MAX_VALUE, appDelayOf(Double.POSITIVE_INFINITY))
    }

    @Test
    fun delayDefaultsToZero() {
        assertEquals(0L, appDelayOf(null))
        assertEquals(0L, appDelayOf("soon"))
        assertEquals(0L, appDelayOf("1.5"))
        assertEquals(0L, appDelayOf(true))
        assertEquals(250L, appDelayOf("250"))
    }

    @Test
    fun negativeDelayThrows() {
        val error = assertThrows(IllegalArgumentException::class.java) { appDelayOf(-1) }
        assertEquals("delayMillis must be non-negative", error.message)
    }

    interface Callback {
        fun call()
    }

    /** The handler class needs a public `getTarget()` method, as NeonJS's has. */
    class TargetHandler(private val target: Any) : InvocationHandler {
        fun getTarget(): Any = target
        override fun invoke(proxy: Any, method: java.lang.reflect.Method, args: Array<out Any?>?): Any? = null
    }

    private fun proxyOf(target: Any): Callback =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(Callback::class.java), TargetHandler(target)) as Callback

    @Test
    fun proxiesOfOneFunctionShareAnIdentity() {
        val function = Any()
        val first = proxyOf(function)
        val second = proxyOf(function)
        assertNotSame(first, second)
        assertSame(function, scriptFunctionIdentity(first))
        assertEquals(scriptFunctionIdentity(first), scriptFunctionIdentity(second))
        assertNotEquals(scriptFunctionIdentity(first), scriptFunctionIdentity(proxyOf(Any())))
    }

    @Test
    fun plainObjectIsItsOwnIdentity() {
        val function: () -> Unit = {}
        assertSame(function, scriptFunctionIdentity(function))
    }
}
