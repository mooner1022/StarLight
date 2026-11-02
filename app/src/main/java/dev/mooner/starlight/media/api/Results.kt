/*
 * Based on the MediaSender module by Hehee (CC BY-NC-SA 4.0), included in StarLight with the author's permission.
 */

package dev.mooner.starlight.media.api

/** A KakaoTalk chat room a message came from. */
class RoomInfo internal constructor(
    /** The channel ID. */
    val id: String,
    val name: String,
    val isGroupChat: Boolean,
    /** When a message from it last came, in epoch milliseconds. */
    val lastSeen: Long,
    /** The Android user whose KakaoTalk has it: 0 for the owner, another for Dual Messenger; null if unknown. */
    val userId: Int?,
) {

    override fun toString(): String =
        "RoomInfo($name, id=$id${userId?.let { ", user=$it" } ?: ""})"
}
