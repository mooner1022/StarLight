/*
 * PipelineStage.kt created by Minki Moon(mooner1022) on 22. 1. 4. 오후 10:44
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.pipeline.stage

interface PipelineStage<T, R> {

    val id: String

    val name: String

    fun run(value: T): R
}