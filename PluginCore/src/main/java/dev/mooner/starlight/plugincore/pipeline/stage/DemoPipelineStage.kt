/*
 * DemoPipelineStage.kt created by Minki Moon(mooner1022) on 22. 1. 11. 오후 8:22
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.pipeline.stage

class DemoPipelineStage: PrecompileStage() {

    override val id: String = "demo_stage"

    override val name: String = "데모 스테이지"

    override fun run(value: String): String {
        Thread.sleep(2000L)
        return value
    }
}