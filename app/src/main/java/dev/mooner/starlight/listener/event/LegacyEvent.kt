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
}