package dev.mooner.starlight.plugincore.chat

/**
 * A chat room that hands what a script sends to functions instead of a messenger, as the debug room does.
 *
 * Scripts see it as a [ChatRoom] whose `isDebugRoom` is `true`. `send` and `markAsRead` call [onSend] and
 * [onMarkAsRead] and return `true`. A plugin can also make one, with `isDebugRoom = false`, to stand in for a room
 * that takes files its own way.
 */
data class DebugChatRoom(
    override val id: String,
    override val name: String,
    override val isGroupChat: Boolean,
    override val isDebugRoom: Boolean = true,
    /** Receives each text message the project sends to the room. */
    val onSend: (message: String) -> Unit,
    /** Called when the project marks the room as read. */
    val onMarkAsRead: () -> Unit,
    /** Shows files the project sent; the files are only kept until the call returns, so copy what should stay. */
    val onSendMedia: ((media: List<MediaFile>) -> Unit)? = null,
): ChatRoom {
    override fun send(message: String): Boolean {
        onSend(message)
        return true
    }

    override fun markAsRead(): Boolean {
        onMarkAsRead()
        return true
    }
}
