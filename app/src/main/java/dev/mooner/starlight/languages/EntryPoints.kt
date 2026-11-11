/*
 * EntryPoints.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.languages

import dev.mooner.starlight.api.api2.BotManagerApi
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.project.event.AllowedEventEntries
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.ui.projects.events.EventCatalog

/**
 * Says, when a project has compiled, that nothing in it can be called: no function with the name of an event it
 * receives, and no listener added. Such a project compiles "successfully" and then never answers, and nothing
 * told why: a function that isn't there is, by design, not an error when its event comes.
 *
 * The usual cause in NeonJS is a function written and not exported, which a script from another engine never
 * needed; that case is named as what it is.
 */
internal object EntryPoints {

    class Notice(
        val text: String,
        /** A function of an event's name is there but can't be reached: a mistake, not a project that takes no events. */
        val isMistake: Boolean,
    )

    /**
     * What to tell about a compiled script. [expected] are the function names of the events it receives, [has]
     * says whether the script gives a function of a name to the app, [listeners] is how many listeners it added,
     * and [source] is its main script when its language reaches only what is exported, null otherwise.
     */
    fun notice(expected: List<String>, has: (String) -> Boolean, listeners: Int, source: String?): Notice? {
        if (listeners > 0 || expected.isEmpty() || expected.any(has))
            return null
        val unexported = source?.let { text -> expected.filter { declares(text, it) } }.orEmpty()
        if (unexported.isNotEmpty()) {
            val name = unexported.first()
            return Notice(translate {
                Locale.ENGLISH { "'$name' is defined but not exported, so StarLight can't call it and the project won't " +
                    "answer. Write 'export function $name' (or 'export { $name }')." }
                Locale.KOREAN  { "'$name' 함수가 있지만 export되지 않아 StarLight가 호출할 수 없고, 프로젝트가 응답하지 않아요. " +
                    "'export function $name'(또는 'export { $name }')으로 바꿔 주세요." }
            }, isMistake = true)
        }
        val names = expected.take(MAX_NAMED).joinToString() + if (expected.size > MAX_NAMED) ", …" else ""
        return Notice(translate {
            Locale.ENGLISH { "Nothing in this project is called by StarLight: it has none of the functions of the events it " +
                "receives ($names)${if (source != null) " exported" else ""}, and added no listener. That is as it should " +
                "be for a project that other projects call or that only runs timers." }
            Locale.KOREAN  { "이 프로젝트에는 StarLight가 호출할 것이 없어요. 받는 이벤트의 함수($names)가 " +
                "${if (source != null) "export되어 있지 않고" else "없고"} 리스너도 추가되지 않았어요. 다른 프로젝트가 불러 쓰거나 " +
                "타이머만 돌리는 프로젝트라면 정상이에요." }
        }, isMistake = false)
    }

    /** Whether [source] declares a function or a variable named [name] at the start of a line. */
    private fun declares(source: String, name: String): Boolean =
        Regex("(?m)^\\s*(?:async\\s+)?function\\s*\\*?\\s*${Regex.escape(name)}\\b").containsMatchIn(source) ||
            Regex("(?m)^\\s*(?:const|let|var)\\s+${Regex.escape(name)}\\s*=").containsMatchIn(source)

    /**
     * Writes the notice for [project], just compiled from [source], into its log, if there is one to give. A mistake
     * is a warning, which raises an alert; the other case is information for whoever looks.
     * [exportsOnly] says the language reaches only what the script exports.
     */
    fun report(project: Project, source: String, exportsOnly: Boolean, has: (String) -> Boolean) {
        val notice = runCatching {
            val allowed = AllowedEventEntries(project.info.allowedEventIds)
            val expected = EventCatalog.load().flatMap { it.events }
                .filter { allowed.isAllowed(it.id) && EventCatalog.isAvailable(project.info.apiLevels, it) }
                // An event that comes through a listener has no function to look for.
                .filter { it.event.listenerName == null }
                .map { it.event.functionName }
                .distinct()
            val listeners = BotManagerApi.Bot.find(project)?.listenerCount ?: 0
            notice(expected, has, listeners, source.takeIf { exportsOnly })
        }.getOrNull() ?: return
        // Under the project's name, as its other entries are: an alert is titled with it.
        val tag = "Project-${project.info.name}"
        if (notice.isMistake)
            project.logger.w(tag, notice.text)
        else
            project.logger.i(tag, notice.text)
    }

    private const val MAX_NAMED = 4
}
