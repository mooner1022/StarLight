/*
 * ProjectOnStartCompileEvent.kt created by Minki Moon(mooner1022) on 7/26/23, 7:54 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.listener.event

import dev.mooner.starlight.plugincore.language.CodeGenerator
import dev.mooner.starlight.plugincore.project.event.ProjectEvent
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate

class ProjectOnStartCompileEvent: ProjectEvent() {

    override val id: String = "compile"

    override val name: String = "onStartCompile 이벤트"

    override val functionName: String = "onStartCompile"

    override val functionComment: String = translate {
        Locale.ENGLISH { "Called just before scope being destroyed.\nAny job that runs for a long time should not run here." }
        Locale.KOREAN  { "프로젝트 스코프 폐기 직전에 호출됩니다.\n오랜 시간이 걸리는 작업은 여기에서 실행되어선 안됩니다." }
    }

    override val argTypes: Array<CodeGenerator.Argument<*>> = emptyArray()
}