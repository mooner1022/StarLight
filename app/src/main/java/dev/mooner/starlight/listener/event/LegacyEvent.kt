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

/**
 * A chat message arrived, in a room of an app the notification rules cover; MessengerBot's legacy `response` form.
 *
 * `response` is called once per message, on the project's workers, in each project that is on, has the MessengerBot
 * legacy API level and allows the event. The debug room screen calls it too, but `send_debug_message` doesn't.
 * MessengerBot's later arguments, `isMention`,
 * `logId`, `channelId` and `userHash`, aren't passed, so they are `undefined`.
 *
 * @param room The room's name.
 * @param msg The message's text.
 * @param sender The sender's name.
 * @param isGroupChat Whether the room is a group chat.
 * @param replier Sends replies to the room.
 * @param imageDB The sender's profile image.
 * @param packageName The package name of the messenger the message came from, such as `com.kakao.talk`; in the
 * debug room, the one its settings name.
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

    // Its seven parameters don't fit a row; the first two say what it is.
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
        )

    // Its arguments don't carry the message, which a script waiting for it as an answer needs to hold it back.
    override fun dispatch(project: Project, args: Array<out Any>, onFailure: (Throwable) -> Unit) {
        ChatWaits.noteLegacyCall(project.info.id, args)
        super.dispatch(project, args, onFailure)
    }
}