/*
 * LanguageStage.kt created by Minki Moon(mooner1022) on 22. 1. 11. 오후 7:43
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.pipeline.stage

import dev.mooner.starlight.plugincore.project.Project

data class LanguageStage(
    override val id: String,
    override val name: String,
    val run: (project: Project, code: String) -> Any
): PipelineStage<Pair<Project, String>, Any> {
    override fun run(value: Pair<Project, String>): Any = run(value.first, value.second)
}