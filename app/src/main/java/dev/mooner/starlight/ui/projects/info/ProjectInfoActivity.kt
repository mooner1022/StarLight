package dev.mooner.starlight.ui.projects.info

import android.content.Context
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.utils.startConfigActivity
import kotlinx.coroutines.CoroutineScope

fun Context.startProjectInfoActivity(
    project: Project
) {
    startConfigActivity(
        title = "정보",
        subTitle = project.info.name,
        struct = getProjectInfoItems(project)
    )
}