/*
 * PipelineUtils.kt created by Minki Moon(mooner1022) on 23. 3. 23. 오후 6:18
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.utils

import dev.mooner.starlight.plugincore.pipeline.Pipeline
import dev.mooner.starlight.plugincore.pipeline.SimplePipeline
import dev.mooner.starlight.plugincore.pipeline.stage.PipelineStage

inline fun <reified E, reified NE> Pipeline<*, E>.canConnect(to: Pipeline<E, NE>): Boolean {
    return E::class.isInstance(NE::class)
}

inline fun <reified S, reified E, reified NE> Pipeline<S, E>.connectTo(pipeline: Pipeline<E, NE>): Pipeline<S, NE> {
    if (!canConnect(pipeline))
        error("Un-connectable pipe: $this -> $pipeline")

    val mStages: MutableList<PipelineStage<*, *>> = arrayListOf()
    stages.forEach(mStages::add)
    pipeline.stages.forEach(mStages::add)

    return SimplePipeline(mStages)
}