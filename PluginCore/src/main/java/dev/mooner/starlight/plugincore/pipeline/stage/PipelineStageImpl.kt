/*
 * PipelineStageImpl.kt created by Minki Moon(mooner1022) on 3/22/23, 1:57 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.pipeline.stage

import dev.mooner.starlight.plugincore.pipeline.Pipeline
import dev.mooner.starlight.plugincore.pipeline.SimplePipeline

class PipelineStageImpl<T, V>(
    override val id: String,
    override val name: String,
    val block: (value: T) -> V,
) : PipelineStage<T, V> {

    override fun run(value: T): V {
        return block(value)
    }
}

fun <S, E> plumber(block: SimplePipeline.Builder<S, E>.() -> Unit): Pipeline<S, E> {
    val builder = SimplePipeline.Builder<S, E>().apply(block)
    return builder.build()
}

fun <T, V> initialStage(id: String, name: String, run: (value: T) -> V): PipelineStage<T, V> {
    return PipelineStageImpl(id, name, run)
}