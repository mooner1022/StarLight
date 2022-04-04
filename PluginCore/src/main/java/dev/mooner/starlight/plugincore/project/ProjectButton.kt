/*
 * ProjectButton.kt created by Minki Moon(mooner1022) on 22. 2. 7. 오후 11:09
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.project

import dev.mooner.starlight.plugincore.utils.Icon
import kotlinx.serialization.Serializable

@Serializable
data class ProjectButton(
    val id: String,
    val icon: Icon
)