/*
 * LegacyEvent.kt created by Minki Moon(mooner1022) on 6/27/23, 12:54 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.listener.event

import dev.mooner.starlight.listener.legacy.ImageDB
import dev.mooner.starlight.listener.legacy.Replier
import dev.mooner.starlight.plugincore.language.CodeGenerator
import dev.mooner.starlight.plugincore.project.event.ProjectEvent
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.script.chat.ChatWaits
import java.math.BigInteger

/**
 * A chat message arrived, in a room of an app the notification rules cover; MessengerBot's legacy `response` form.
 *
 * `response` is called once per message, on the project's workers, in each project that is on, has the MessengerBot
 * legacy API level and allows the event. The debug room screen calls it too, but `send_debug_message` doesn't.
 * It always gets all eleven arguments, as MessengerBot's extended form passes them.
 *
 * @param room The room's name.
 * @param msg The message's text.
 * @param sender The sender's name.
 * @param isGroupChat Whether the room is a group chat.
 * @param replier Sends replies to the room.
 * @param imageDB The sender's profile image.
 * @param packageName The package name of the messenger the message came from, such as `com.kakao.talk`; in the
 * debug room, the one its settings name.
 * @param isMention Whether the message mentions someone. In the debug room, `true` for a message typed with `<@name>`.
 * @param logId The messenger's ID of this message, as a `BigInt`; `0` when the notification has none, and in the
 * debug room.
 * @param channelId The room's ID, as a `BigInt`; `0` when it is not a number, and in the debug room.
 * @param userHash The sender's ID as the messenger gives it, such as KakaoTalk's user key; an empty string when there
 * is none, and `"DEBUG ROOM"` in the debug room.
 * @see guide:events#event-functions
 * @see guide:projects#allowed-events
 * @see guide:testing#how-debug-messages-differ
 */
class LegacyEvent: ProjectEvent() {

    override val id: String = "legacy"

    override val name: String = "레거시 호환 모드"

    override val description: String = translate {
        Locale.ENGLISH { "When a message arrives, in MessengerBot's legacy form" }
        Locale.KOREAN  { "메시지를 받았을 때, 메신저봇 레거시 방식으로" }
    }

    override val functionName: String = "response"

    // Its eleven parameters don't fit a row; the first two say what it is.
    override val usage: String
        get() = "$functionName(room, msg, …)"

    override val argTypes: Array<CodeGenerator.Argument<*>> =
        arrayOf(
            "room"        typedAs String::class,
            "msg"         typedAs String::class,
            "sender"      typedAs String::class,
            "isGroupChat" typedAs Boolean::class,
            "replier"     typedAs Replier::class,
            "imageDB"     typedAs ImageDB::class,
            "packageName" typedAs String::class,
            "isMention"   typedAs Boolean::class,
            "logId"       typedAs BigInteger::class,
            "channelId"   typedAs BigInteger::class,
            "userHash"    typedAs String::class,
        )

    // Its arguments don't carry the message, which a script waiting for it as an answer needs to hold it back.
    override fun dispatch(project: Project, args: Array<out Any>, onFailure: (Throwable) -> Unit) {
        // The replier is shared by the projects; each gets it with its own project, for its debug room and state.
        val own = arrayOf<Any>(*args)
        (own.getOrNull(REPLIER_INDEX) as? Replier)?.let { own[REPLIER_INDEX] = it.forProject(project) }
        // ChatWaits finds the call by this very array, so it notes the one the project is called with.
        ChatWaits.noteLegacyCall(project.info.id, own)
        super.dispatch(project, own, onFailure)
    }

    private companion object {
        /** The place of `replier` among the arguments. */
        const val REPLIER_INDEX = 4
    }
}
