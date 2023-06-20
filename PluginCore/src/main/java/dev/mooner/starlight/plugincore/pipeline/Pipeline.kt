/*
 * Pipeline.kt created by Minki Moon(mooner1022) on 3/22/23, 2:00 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.pipeline

import dev.mooner.starlight.plugincore.pipeline.stage.PipelineStage

interface Pipeline<S, E> {

    val stages: List<PipelineStage<*, *>>

    fun run(value: S): E
}