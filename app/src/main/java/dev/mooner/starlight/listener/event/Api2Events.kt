/*
 * Api2Events.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.listener.event

import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification
import dev.mooner.starlight.api.api2.BotManagerApi.Bot
import dev.mooner.starlight.plugincore.chat.Message
import dev.mooner.starlight.plugincore.language.CodeGenerator
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.project.event.ProjectEvent
import dev.mooner.starlight.plugincore.project.event.ProjectEventManager
import kotlin.reflect.KClass

/** The names of MessengerBot R's API2 events, which scripts listen for with `bot.on(…)`. */
object Api2Events {
    const val MESSAGE = "message"
    const val COMMAND = "command"
    const val NOTIFICATION_POSTED = "notificationPosted"
    const val NOTIFICATION_REMOVED = "notificationRemoved"
    const val START_COMPILE = "startCompile"

    /** Whether [project] may receive the event [eventClass]: its allowed events and API levels include it. */
    fun isAllowed(project: Project, eventClass: KClass<out ProjectEvent>): Boolean =
        ProjectEventManager.validateAndGetEventID(eventClass)?.let(project::isEventCallAllowed) == true
}

/**
 * An API2 event: it reaches the listeners the script added to its bot, not a function of a given name, and a new
 * project starts with a listener for it, as MessengerBot R's starting code has.
 */
abstract class Api2Event: ProjectEvent() {

    /** The `Event` constant that names it, as in `Event.MESSAGE`. */
    protected abstract val constant: String

    /** The parameters of the listener a new project starts with. */
    protected abstract val parameters: String

    /** Code that goes before the listener is added. */
    protected open val setup: String? = null

    /** Calls [deliver] with the project's bot, on its workers, if the script has a bot. */
    override fun dispatch(project: Project, args: Array<out Any>, onFailure: (Throwable) -> Unit) {
        val bot = Bot.find(project) ?: return
        project.runEvent({ deliver(bot, args) }, onFailure)
    }

    protected abstract fun deliver(bot: Bot, args: Array<out Any>)

    // Written out rather than through the language's generator, which in NeonJS would export the function and so
    // let StarLight's own event of the same name call it too.
    override fun generateCode(generator: CodeGenerator): CodeGenerator.GeneratedFunction =
        CodeGenerator.GeneratedFunction(
            imports = listOf(BOT_DECLARATION),
            function = buildString {
                append("function $functionName($parameters) {\n}\n")
                setup?.let { append(it).append('\n') }
                append("bot.addListener(Event.$constant, $functionName);")
            },
        )

    private companion object {
        const val BOT_DECLARATION = "const bot = BotManager.getCurrentBot();"
    }
}

class Api2MessageEvent: Api2Event() {
    override val id = "message"
    override val name = "API2 메시지 이벤트"
    override val functionName = "onMessage"
    override val constant = "MESSAGE"
    override val parameters = "msg"
    override val argTypes: Array<CodeGenerator.Argument<*>> = arrayOf("message" typedAs Message::class)

    override fun deliver(bot: Bot, args: Array<out Any>) =
        bot.deliverMessage(args[0] as Message)
}

class Api2CommandEvent: Api2Event() {
    override val id = "command"
    override val name = "API2 명령어 이벤트"
    override val functionName = "onCommand"
    override val constant = "COMMAND"
    override val parameters = "msg"
    override val setup = "bot.setCommandPrefix(\"@\");"
    override val argTypes: Array<CodeGenerator.Argument<*>> = arrayOf("message" typedAs Message::class)

    override fun deliver(bot: Bot, args: Array<out Any>) =
        bot.deliverCommand(args[0] as Message)
}

/** Given `null` for the session manager MessengerBot R passes second, which StarLight doesn't have. */
class Api2NotificationPostedEvent: Api2Event() {
    override val id = "notification_posted"
    override val name = "API2 알림 게시 이벤트"
    override val functionName = "onNotificationPosted"
    override val constant = "NOTIFICATION_POSTED"
    override val parameters = "sbn, sessionManager"
    override val argTypes: Array<CodeGenerator.Argument<*>> = arrayOf("sbn" typedAs StatusBarNotification::class)

    override fun deliver(bot: Bot, args: Array<out Any>) =
        bot.deliver(Api2Events.NOTIFICATION_POSTED, args[0], null)
}

class Api2NotificationRemovedEvent: Api2Event() {
    override val id = "notification_removed"
    override val name = "API2 알림 삭제 이벤트"
    override val functionName = "onNotificationRemoved"
    override val constant = "NOTIFICATION_REMOVED"
    override val parameters = "sbn, rankingMap, reason"
    override val argTypes: Array<CodeGenerator.Argument<*>> = arrayOf(
        "sbn" typedAs StatusBarNotification::class,
        "rankingMap" typedAs RankingMap::class,
        "reason" typedAs Int::class,
    )

    override fun deliver(bot: Bot, args: Array<out Any>) =
        bot.deliver(Api2Events.NOTIFICATION_REMOVED, args[0], args[1], args[2])
}

/** Delivered by the bot itself when its project starts compiling, before the new code runs. */
class Api2StartCompileEvent: Api2Event() {
    override val id = "start_compile"
    override val name = "API2 컴파일 시작 이벤트"
    override val functionName = "onStartCompile"
    override val constant = "START_COMPILE"
    override val parameters = ""
    override val argTypes: Array<CodeGenerator.Argument<*>> = emptyArray()

    override fun deliver(bot: Bot, args: Array<out Any>) =
        bot.deliver(Api2Events.START_COMPILE)
}
