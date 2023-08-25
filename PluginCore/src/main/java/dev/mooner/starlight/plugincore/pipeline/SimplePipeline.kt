/*
 * SimplePipeline.kt created by Minki Moon(mooner1022) on 6/27/23, 12:18 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.pipeline

import dev.mooner.starlight.plugincore.pipeline.stage.PipelineStage
import dev.mooner.starlight.plugincore.pipeline.stage.PipelineStageImpl

private typealias StageCallback = (stage: AnyStage, index: Int) -> Unit

class SimplePipeline<S, E>(
    initialStages: List<PipelineStage<*, *>>? = null
): Pipeline<S, E> {

    private var listener: StageCallback? = null

    private val mStages: MutableList<PipelineStage<*, *>> =
        initialStages?.toMutableList() ?: arrayListOf()
    override val stages: List<PipelineStage<*, *>>
        get() = mStages

    fun <T, R> addStage(stage: PipelineStage<T, R>) {
        mStages += stage
    }

    fun setOnStageUpdateListener(listener: StageCallback) {
        this.listener = listener
    }

    override fun run(value: S): E {
        if (stages.isEmpty())
            error("Pipeline is empty")
        return recursiveRun(0, value)
    }

    override fun toString(): String {
        return "Pipeline(${stages.joinToString(" => ") { it.name }})"
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T, V> recursiveRun(index: Int, prevResult: T): V {
        val stage = stages[index] as PipelineStage<T, V>
        listener?.invoke(stage, index)

        return if (index == stages.size - 1)
            stage.run(prevResult)
        else
            recursiveRun(index + 1, stage.run(prevResult))
    }

    class Builder<S, E> {
        private val pipeline = SimplePipeline<S, E>()

        fun <T, V> initial(id: String, name: String, run: (value: T) -> V): PipelineStage<T, V> {
            return PipelineStageImpl(id, name, run)
                .also(pipeline::addStage)
        }

        fun <T, V> PipelineStage<*, T>.pipe(id: String, name: String, run: (value: T) -> V): PipelineStage<T, V> {
            return PipelineStageImpl(id, name, run)
                .also(pipeline::addStage)
        }

        fun build(): Pipeline<S, E> = pipeline
    }
}