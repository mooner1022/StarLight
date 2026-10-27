package dev.mooner.starlight.diagnostics

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import dev.mooner.starlight.BuildConfig
import dev.mooner.starlight.CA_GENERAL
import dev.mooner.starlight.R
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.ui.settings.notifications.NotificationRulesActivity
import dev.mooner.starlight.ui.settings.solver.readNotificationRuleFile
import dev.mooner.starlight.ui.settings.solver.unwrapData

private const val CF_GLOBAL_POWER = "global_power"

object Diagnostics {

    val globalPower = diagnostic("global_power") {
        title {
            Locale.ENGLISH { "Power" }
            Locale.KOREAN  { "전원" }
        }
        icon = R.drawable.ic_round_power_settings_new_24
        check {
            if (GlobalConfig.category(CA_GENERAL).getBoolean(CF_GLOBAL_POWER, true))
                ok {
                    Locale.ENGLISH { "On" }
                    Locale.KOREAN  { "켜짐" }
                }
            else
                warning {
                    Locale.ENGLISH { "Off" }
                    Locale.KOREAN  { "꺼짐" }
                }
        }
        fix {
            GlobalConfig.edit {
                category(CA_GENERAL)[CF_GLOBAL_POWER] = true
            }
        }
    }

    val notificationAccess = diagnostic("notification_access") {
        title {
            Locale.ENGLISH { "Notifications" }
            Locale.KOREAN  { "알림 접근" }
        }
        icon = R.drawable.ic_round_notifications_24
        check { context ->
            if (context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context))
                ok {
                    Locale.ENGLISH { "Granted" }
                    Locale.KOREAN  { "허용됨" }
                }
            else
                error {
                    Locale.ENGLISH { "Not granted" }
                    Locale.KOREAN  { "권한 없음" }
                }
        }
        fix { context ->
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .let(context::startActivity)
        }
    }

    val packageRules = diagnostic("package_rules") {
        title {
            Locale.ENGLISH { "Package rules" }
            Locale.KOREAN  { "패키지 규칙" }
        }
        icon = R.drawable.ic_round_construction_24
        check {
            val count = readNotificationRuleFile()
                ?.let(::unwrapData)
                ?.size
                ?: 0
            if (count > 0)
                ok {
                    Locale.ENGLISH { "$count rules" }
                    Locale.KOREAN  { "${count}개" }
                }
            else
                error {
                    Locale.ENGLISH { "No rules" }
                    Locale.KOREAN  { "규칙 없음" }
                }
        }
        fix { context ->
            Intent(context, NotificationRulesActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .let(context::startActivity)
        }
    }

    val projects = diagnostic("projects") {
        title {
            Locale.ENGLISH { "Projects" }
            Locale.KOREAN  { "프로젝트" }
        }
        icon = R.drawable.ic_round_projects_24
        check {
            val total = Session.projectManager.getProjects().size
            val enabled = Session.projectManager.getEnabledProjects().size
            if (total == 0)
                info {
                    Locale.ENGLISH { "None" }
                    Locale.KOREAN  { "없음" }
                }
            else
                ok {
                    Locale.ENGLISH { "$enabled/$total active" }
                    Locale.KOREAN  { "${total}개 중 ${enabled}개 활성" }
                }
        }
    }

    val all: List<Diagnostic> = listOf(globalPower, notificationAccess, packageRules, projects)

    fun checkAll(context: Context): List<Pair<Diagnostic, DiagnosticResult>> =
        all.map { it to it.check(context) }

    fun summarize(context: Context): String {
        val problems = checkAll(context).filter { (_, result) -> result.level.isProblem }
        return when (problems.size) {
            0 -> translate {
                Locale.ENGLISH { "All systems normal · v${BuildConfig.VERSION_NAME}" }
                Locale.KOREAN  { "모두 정상 · v${BuildConfig.VERSION_NAME}" }
            }
            1 -> problems.first().let { (diagnostic, result) -> "⚠ ${diagnostic.title}: ${result.summary}" }
            else -> translate {
                Locale.ENGLISH { "⚠ ${problems.size} items need attention" }
                Locale.KOREAN  { "⚠ ${problems.size}개 항목을 확인해 주세요" }
            }
        }
    }
}
