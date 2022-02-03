/*
 * PrecompileStage.kt created by Minki Moon(mooner1022) on 22. 1. 7. 오후 4:43
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.pipeline.stage

import kotlinx.serialization.Serializable

@Serializable
abstract class PrecompileStage: PipelineStage<String, String>