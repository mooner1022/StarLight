/*
 * CompilePipeline.kt created by Minki Moon(mooner1022) on 22. 1. 4. 오후 5:46
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.pipeline

import dev.mooner.starlight.plugincore.pipeline.stage.PipelineStage
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.utils.pairOf

/*
 * prepare() ->
 */

typealias AnyStage = PipelineStage<Any, Any>

@Suppress("UNCHECKED_CAST")
class CompilePipeline(
    private val project: Project,
    private val info: PipelineInfo,
    private val stageChangeCallback: (stage: AnyStage, percentage: Int) -> Unit
) {

    val stageSize = info.stages.size + 1

    fun run(code: String): Any {
        val lang = project.getLanguage()

        val precompiledCode = preCompile(code)

        val langStage = lang.toPipelineStage()
        stageChangeCallback(langStage as AnyStage, calcPercent(stageSize - 1))
        return langStage.run(pairOf(project, precompiledCode))
    }

    fun preCompile(code: String): String {
        var prevStageResult: String? = null

        for ((index, stage) in info.stages.withIndex()) {
            stageChangeCallback(stage as AnyStage, calcPercent(index))
            prevStageResult = stage.run(prevStageResult ?: code)
        }
        return prevStageResult ?: code
    }

    private fun calcPercent(index: Int): Int = ((index.toFloat() / stageSize.toFloat()) * 100f).toInt()
}