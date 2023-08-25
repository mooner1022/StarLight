/*
 * LegacyEvent.kt created by Minki Moon(mooner1022) on 6/27/23, 12:54 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.listener.event

import dev.mooner.starlight.listener.legacy.ImageDB
import dev.mooner.starlight.listener.legacy.Replier
import dev.mooner.starlight.plugincore.project.event.ProjectEvent
import kotlin.reflect.KClass

class LegacyEvent: ProjectEvent() {

    override val id: String = "legacy"

    override val name: String = "레거시 호환 모드"

    override val functionName: String = "response"

    override val argTypes: Array<KClass<*>> =
        arrayOf(String::class, String::class, String::class, Boolean::class, Replier::class, ImageDB::class)
}