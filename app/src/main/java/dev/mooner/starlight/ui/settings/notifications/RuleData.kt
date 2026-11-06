/*
 * RuleData.kt created by Minki Moon(mooner1022) on 6/27/23, 12:18 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.settings.notifications

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RuleData(
    @SerialName("package_name")
    val packageName: String,
    @SerialName("user_id")
    val userId: Int = 0,
    @SerialName("parser_spec_id")
    val parserSpecId: String = "default"
)