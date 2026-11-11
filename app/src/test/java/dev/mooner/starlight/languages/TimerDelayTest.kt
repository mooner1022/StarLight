package dev.mooner.starlight.languages

import org.junit.Assert.assertEquals
import org.junit.Test

class TimerDelayTest {

    @Test
    fun readsNumbersAndTextThatReadsAsANumber() {
        assertEquals(1500L, TimerDelay.fromScript(1500.0))
        assertEquals(250L, TimerDelay.fromScript(250))
        assertEquals(1000L, TimerDelay.fromScript("1000"))
        assertEquals(30L, TimerDelay.fromScript(" 30.9 "))
    }

    @Test
    fun anythingElseIsZero() {
        assertEquals(0L, TimerDelay.fromScript(null))
        assertEquals(0L, TimerDelay.fromScript(""))
        assertEquals(0L, TimerDelay.fromScript("soon"))
        assertEquals(0L, TimerDelay.fromScript(Double.NaN))
        assertEquals(0L, TimerDelay.fromScript(true))
    }

    @Test
    fun aRepeatingTimerAlwaysWaits() {
        assertEquals(TimerDelay.MIN_REPEAT_MILLIS, TimerDelay.effective(0, repeat = true))
        assertEquals(TimerDelay.MIN_REPEAT_MILLIS, TimerDelay.effective(-5, repeat = true))
        assertEquals(200L, TimerDelay.effective(200, repeat = true))
    }

    @Test
    fun aSingleTimerMayRunAtOnce() {
        assertEquals(0L, TimerDelay.effective(0, repeat = false))
        assertEquals(0L, TimerDelay.effective(-5, repeat = false))
        assertEquals(200L, TimerDelay.effective(200, repeat = false))
    }
}
