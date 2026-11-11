package dev.mooner.starlight.listener

import dev.mooner.starlight.listener.NotificationDrops.Key
import dev.mooner.starlight.listener.NotificationDrops.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationDropsTest {

    @Test
    fun onlyTheFirstOfAKindAsksForALog() {
        val drops = NotificationDrops()
        val key = Key(Reason.NO_RULE, "org.telegram.messenger", 0)

        assertTrue(drops.note(key, 1))
        assertFalse(drops.note(key, 2))
        assertFalse(drops.note(key, 3))

        val (_, count) = drops.snapshot().single()
        assertEquals(3, count.times)
        assertEquals(3L, count.lastAt)
    }

    @Test
    fun anotherAppUserOrReasonIsAnotherKind() {
        val drops = NotificationDrops()

        assertTrue(drops.note(Key(Reason.NO_RULE, "a", 0), 1))
        assertTrue(drops.note(Key(Reason.NO_RULE, "b", 0), 1))
        assertTrue(drops.note(Key(Reason.OTHER_USER, "a", 95), 1))
        assertTrue(drops.note(Key(Reason.FAILED, "a", 0, "spec", "X@a.b:1"), 1))
        assertTrue(drops.note(Key(Reason.FAILED, "a", 0, "spec", "Y@a.b:2"), 1))
        assertFalse(drops.note(Key(Reason.FAILED, "a", 0, "spec", "X@a.b:1"), 2))
    }

    @Test
    fun theMostFrequentComesFirst() {
        val drops = NotificationDrops()
        drops.note(Key(Reason.NO_RULE, "rare", 0), 1)
        repeat(5) { drops.note(Key(Reason.NO_RULE, "often", 0), it.toLong()) }

        assertEquals(listOf("often", "rare"), drops.snapshot().map { it.first.packageName })
    }

    @Test
    fun aResetStartsOver() {
        val drops = NotificationDrops()
        val key = Key(Reason.UNKNOWN_SPEC, "a", 0, "gone")
        drops.note(key, 1)

        drops.reset()

        assertTrue(drops.snapshot().isEmpty())
        assertTrue(drops.note(key, 2))
    }

    @Test
    fun itHoldsOnlySoManyKinds() {
        val drops = NotificationDrops()
        repeat(500) { drops.note(Key(Reason.FAILED, "a", 0, "spec", "E@$it"), it.toLong()) }

        assertTrue(drops.snapshot().size <= 128)
    }
}
