/*
 * Based on the MediaSender module by Hehee (CC BY-NC-SA 4.0), included in StarLight with the author's permission.
 */

package dev.mooner.starlight.media.api

/** A KakaoTalk chat room a message came from. */
class RoomInfo internal constructor(
    /** The channel ID. */
    val id: String,
    /** The room's name, as its latest message showed it; in a one-to-one chat, the other person's name. */
    val name: String,
    /** Whether the room is a group chat, as StarLight read it from the room's latest message. */
    val isGroupChat: Boolean,
    /** When a message from it last came, in epoch milliseconds. */
    val lastSeen: Long,
    /** The Android user whose KakaoTalk has it: 0 for the owner, another for Dual Messenger; null if unknown. */
    val userId: Int?,
) {

    override fun toString(): String =
        "RoomInfo($name, id=$id${userId?.let { ", user=$it" } ?: ""})"
}
