package dev.mooner.starlight.listener.legacy

/**
 * Sends replies for the legacy `response` event: MessengerBot's `replier`.
 *
 * A `Replier` stays tied to its room, so a script can keep it and reply later. In the debug room every reply appears
 * in the debug room, whatever room it names, and counts as sent.
 *
 * MessengerBot's `replyDelayed`, `replyWithMarkdown` and `markAsRead` are missing.
 *
 * @see guide:testing#how-debug-messages-differ
 */
data class Replier(
    /**
     * The function StarLight sends replies through; scripts call [reply] instead.
     *
     * @suppress
     */
    val onSend: (room: String?, message: String, hideToast: Boolean) -> Boolean
) {

    /**
     * Sends [message] to the room named [room] and says whether it was sent.
     *
     * The room must have sent a message since StarLight started. If no known room has that name, the message goes to
     * this replier's own room instead; MessengerBot returns `false` there. A toast shows when no room can be reached at
     * all, unless [hideToast] is `true`.
     *
     * @param room The room's name.
     * @param message The text to send.
     * @param hideToast Whether to skip the toast shown when no room can be reached.
     * @return `false` if no room could be reached, or if the messenger's reply action failed.
     */
    @JvmOverloads
    fun reply(room: String, message: String, hideToast: Boolean = false): Boolean =
        onSend(room, message, hideToast)

    /**
     * Sends [message] to the room the message came from and says whether it was sent.
     *
     * A toast shows when StarLight can't reach the room.
     *
     * @param message The text to send.
     * @return `false` if the room couldn't be reached, or if the messenger's reply action failed.
     */
    fun reply(message: String) =
        onSend(null, message, false)
}