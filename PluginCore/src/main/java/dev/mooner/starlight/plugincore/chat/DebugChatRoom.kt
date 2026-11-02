package dev.mooner.starlight.plugincore.chat

data class DebugChatRoom(
    override val id: String,
    override val name: String,
    override val isGroupChat: Boolean,
    override val isDebugRoom: Boolean = true,
    val onSend: (message: String) -> Unit,
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
