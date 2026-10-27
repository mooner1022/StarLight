package dev.mooner.starlight.ui.widget

import android.view.View
import dev.mooner.starlight.R
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.project.ProjectID
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.plugincore.widget.widget
import dev.mooner.starlight.plugincore.widget.x
import dev.mooner.starlight.utils.showConfirmDialog
import kotlin.time.Duration.Companion.seconds

const val WIDGET_JOBS = "widget_jobs"

private const val STUCK_MILLIS = 10_000L
private const val SETTING_BUSY_ONLY = "busy_only"

private data class JobState(
    val project: Project,
    val activeJobs: Int,
    val busySeconds: Long,
) {
    val isStuck: Boolean
        get() = busySeconds * 1000 >= STUCK_MILLIS
}

val JobsWidget = widget(WIDGET_JOBS) {
    name {
        Locale.ENGLISH { "Running jobs" }
        Locale.KOREAN  { "실행 중 작업" }
    }
    span(default = 4 x 2, min = 2 x 1, max = 8 x 6)

    settings {
        toggle(
            id = SETTING_BUSY_ONLY,
            title = translate {
                Locale.ENGLISH { "Only show busy projects" }
                Locale.KOREAN  { "작업 중인 프로젝트만 표시" }
            },
        )
    }

    content {
        val binding = panel(R.drawable.ic_round_refresh_24, R.string.widget_jobs)
        val busyOnly = settings.boolean(SETTING_BUSY_ONLY)
        val busySince: MutableMap<ProjectID, Long> = hashMapOf()
        var lastRendered: List<Any>? = null

        fun collect(now: Long): List<JobState> {
            if (!Session.isInitComplete)
                return emptyList()
            return Session.projectManager.getEnabledProjects().map { project ->
                val active = project.activeJobs()
                val id = project.info.id
                if (active > 0)
                    busySince.putIfAbsent(id, now)
                else
                    busySince -= id
                val busySeconds = busySince[id]?.let { (now - it) / 1000 } ?: 0
                JobState(project, active, busySeconds)
            }.sortedWith(compareByDescending<JobState> { it.activeJobs }.thenBy { it.project.info.name })
        }

        fun render() {
            val states = collect(System.currentTimeMillis())
            val visible = states
                .filter { !busyOnly || it.activeJobs > 0 }
                .take(rowCapacity())
            val total = states.sumOf(JobState::activeJobs)
            val stuck = states.count(JobState::isStuck)
            val snapshot = listOf(isCompact, total, stuck) + visible.map { listOf(it.project.info.id, it.activeJobs, if (it.isStuck) it.busySeconds else 0L) }
            if (snapshot == lastRendered)
                return
            lastRendered = snapshot

            when {
                stuck > 0 -> binding.setSummary(translate {
                    Locale.ENGLISH { "$stuck may be stuck" }
                    Locale.KOREAN  { "${stuck}개 멈춤 의심" }
                }, R.color.status_warning)
                total > 0 -> binding.setSummary(translate {
                    Locale.ENGLISH { "$total running" }
                    Locale.KOREAN  { "${total}개 실행 중" }
                }, R.color.main_dark)
                else -> binding.setSummary(translate {
                    Locale.ENGLISH { "Idle" }
                    Locale.KOREAN  { "대기 중" }
                }, R.color.status_ok)
            }
            if (isCompact)
                return

            if (visible.isEmpty()) {
                binding.setContent(listOf(createMessage(
                    if (busyOnly && states.isNotEmpty())
                        translate {
                            Locale.ENGLISH { "No running jobs" }
                            Locale.KOREAN  { "실행 중인 작업이 없어요" }
                        }
                    else
                        translate {
                            Locale.ENGLISH { "No enabled projects" }
                            Locale.KOREAN  { "활성화된 프로젝트가 없어요" }
                        }
                )))
                return
            }
            binding.setContent(visible.map { state ->
                val value = when {
                    state.isStuck -> translate {
                        Locale.ENGLISH { "${state.activeJobs} · ${state.busySeconds}s" }
                        Locale.KOREAN  { "${state.activeJobs}개 · ${state.busySeconds}초째" }
                    }
                    else -> state.activeJobs.toString()
                }
                val colorRes = when {
                    state.isStuck        -> R.color.status_warning
                    state.activeJobs > 0 -> R.color.main_dark
                    else                 -> R.color.text_sub
                }
                createRow(
                    title = state.project.info.name,
                    value = value,
                    valueColorRes = colorRes,
                    icon = R.drawable.ic_round_projects_24,
                    fill = false,
                ).apply {
                    if (state.activeJobs > 0)
                        setClickAction { view -> confirmStop(view, state.project) }
                }
            })
        }

        render()
        ticker(1.seconds) { render() }
    }
}

private fun confirmStop(view: View, project: Project) {
    val name = project.info.name
    showConfirmDialog(
        context = view.context,
        title = translate {
            Locale.ENGLISH { "Release jobs" }
            Locale.KOREAN  { "작업 해제" }
        },
        message = translate {
            Locale.ENGLISH { "Release the stuck jobs of '$name' and start a new worker pool? Script code that is already running may keep running until it finishes." }
            Locale.KOREAN  { "'$name'의 멈춘 작업을 해제하고 작업 스레드를 새로 만들까요? 이미 실행 중인 스크립트 코드는 끝날 때까지 계속될 수 있어요." }
        },
    ) { confirmed ->
        if (confirmed)
            project.stopAllJobs()
    }
}
