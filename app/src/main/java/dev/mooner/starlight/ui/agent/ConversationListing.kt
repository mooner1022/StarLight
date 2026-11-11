package dev.mooner.starlight.ui.agent

import dev.mooner.starlight.agent.store.ConversationSummary

/** Which conversations the AI tab lists. */
internal object ConversationListing {

    /** How many more a tap on the list's last row shows, and how many it starts with. */
    const val PAGE = 40

    class Page(
        /** The ones working now, all of them: they are what the user is waiting for. */
        val running: List<ConversationSummary>,
        /** The others, the most recent first, as far as [limit] goes. */
        val others: List<ConversationSummary>,
        /** How many others there are past the ones shown. */
        val more: Int,
    )

    /** [summaries], most recent first, split into the ones in [liveIds] and at most [limit] of the rest. */
    fun page(summaries: List<ConversationSummary>, liveIds: Set<String>, limit: Int): Page {
        val (running, others) = summaries.partition { it.id in liveIds }
        val shown = others.take(limit.coerceAtLeast(0))
        return Page(running, shown, others.size - shown.size)
    }
}
