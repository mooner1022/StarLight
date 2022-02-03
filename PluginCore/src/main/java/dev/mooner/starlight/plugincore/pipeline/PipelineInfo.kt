/*
 * PipelineInfo.kt created by Minki Moon(mooner1022) on 22. 1. 7. 오후 6:26
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.pipeline

import dev.mooner.starlight.plugincore.pipeline.stage.PrecompileStage
import kotlinx.serialization.Serializable

@Serializable
data class PipelineInfo(
    val stages: MutableSet<PrecompileStage>
)
