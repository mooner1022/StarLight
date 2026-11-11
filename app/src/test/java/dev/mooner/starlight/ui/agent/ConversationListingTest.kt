package dev.mooner.starlight.ui.agent

import dev.mooner.starlight.agent.store.ConversationSummary
import dev.mooner.starlight.agent.model.RunState
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationListingTest {

    private fun summaries(count: Int): List<ConversationSummary> =
        (1..count).map { ConversationSummary(id = "c$it", title = "t$it", updatedAt = (count - it).toLong(), state = RunState.IDLE) }

    @Test
    fun theOlderOnesAreCountedNotDropped() {
        val page = ConversationListing.page(summaries(57), emptySet(), ConversationListing.PAGE)

        assertEquals(40, page.others.size)
        assertEquals("c1", page.others.first().id)
        assertEquals(17, page.more)
    }

    @Test
    fun askingForMoreShowsThem() {
        val page = ConversationListing.page(summaries(57), emptySet(), ConversationListing.PAGE * 2)

        assertEquals(57, page.others.size)
        assertEquals(0, page.more)
    }

    @Test
    fun theOnesWorkingAreAllShownAndDontCountAgainstTheLimit() {
        val live = setOf("c50", "c55")
        val page = ConversationListing.page(summaries(57), live, ConversationListing.PAGE)

        assertEquals(listOf("c50", "c55"), page.running.map { it.id })
        assertEquals(40, page.others.size)
        assertEquals(15, page.more)
    }
}
