package dev.mooner.starlight.plugincore.chat

/**
 * A message that was deleted, as `onMessageDeleted(event)` receives it.
 *
 * StarLight notices a deletion when KakaoTalk removes the notification of the latest message StarLight received, from
 * any room, so deletions of earlier messages aren't seen. Nor is one if a bot has sent to a KakaoTalk room since that
 * message arrived. Unlike [Message], the sender is only a name.
 *
 * @see guide:events#event-functions
 */
data class DeletedMessage(
    /** The deleted message's text, from its notification. */
    val message: String,
    /** The name of the sender, as plain text rather than a [ChatSender]. */
    val sender: String,
    /**
     * The room the message was in, or `null` if StarLight doesn't know the room.
     *
     * StarLight learns rooms from the messages it receives while it runs. A known room can be sent to, as in
     * `onMessage`.
     */
    val room: ChatRoom?,
    /** The package name of the messenger: `com.kakao.talk`, as StarLight only watches KakaoTalk for deletions. */
    val packageName: String,
    /** The deleted message's ID, the same `chatLogId` its `onMessage` event had. */
    val chatLogId: Long
)