/*
 * Api2Events.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.listener.event

import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification
import dev.mooner.starlight.api.api2.Api2Command
import dev.mooner.starlight.api.api2.Api2Message
import dev.mooner.starlight.api.api2.BotManagerApi.Bot
import dev.mooner.starlight.listener.chat.SessionManager
import dev.mooner.starlight.plugincore.chat.Message
import dev.mooner.starlight.plugincore.language.CodeGenerator
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.project.event.ProjectEvent
import dev.mooner.starlight.plugincore.project.event.ProjectEventManager
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import kotlin.reflect.KClass
import dev.mooner.starlight.script.chat.ChatWaits

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

    override val usage: String
        get() = "bot.on(Event.$constant)"

    /** Calls [deliver] with the project's bot, on its workers, if the script has a bot. */
    override fun dispatch(project: Project, args: Array<out Any>, onFailure: (Throwable) -> Unit) {
        val bot = Bot.find(project) ?: return
        project.runEvent({
            // Decided as it is about to run, as the call gate decides for onMessage.
            if (!isHeldBack(project, args))
                deliver(bot, args)
        }, onFailure)
    }

    protected abstract fun deliver(bot: Bot, args: Array<out Any>)

    /** Whether the event is kept from the listeners: a message a script waits for as an answer (`Chat.waitFor`). */
    protected open fun isHeldBack(project: Project, args: Array<out Any>): Boolean = false

    // onApi2Message beside StarLight's onMessage: in Rhino, a top-level function of an event's name is called as that
    // event, so the listener would run twice.
    override val alternateFunctionName: String
        get() = "onApi2" + functionName.removePrefix("on")

    override fun generateCode(generator: CodeGenerator): CodeGenerator.GeneratedFunction =
        generateCode(generator, functionName)

    // Written out rather than through the language's generator, which in NeonJS would export the function and so
    // let StarLight's own event of the same name call it too.
    override fun generateCode(generator: CodeGenerator, name: String): CodeGenerator.GeneratedFunction =
        CodeGenerator.GeneratedFunction(
            imports = listOf(BOT_DECLARATION),
            function = buildString {
                append("function $name($parameters) {\n}\n")
                setup?.let { append(it).append('\n') }
                append("bot.addListener(Event.$constant, $name);")
            },
        )

    private companion object {
        const val BOT_DECLARATION = "const bot = BotManager.getCurrentBot();"
    }
}

/**
 * A chat message arrived; the bot's `message` listeners, added with `bot.on(Event.MESSAGE, …)`, receive it.
 *
 * Called once per message from the apps the notification rules cover, and for messages sent in the debug room. The
 * listeners run on the project's workers. Nothing is called until the script has its bot from
 * `BotManager.getCurrentBot()`.
 *
 * @param msg The message: its text, sender and room, and `reply` to answer in that room.
 * @see guide:events#api2-listeners
 */
class Api2MessageEvent: Api2Event() {
    override val id = "message"
    override val name = "API2 메시지 이벤트"
    override val description = translate {
        Locale.ENGLISH { "When a message arrives" }
        Locale.KOREAN  { "메시지를 받았을 때" }
    }
    override val functionName = "onMessage"
    override val constant = "MESSAGE"
    override val listenerName = Api2Events.MESSAGE
    override val parameters = "msg"
    override val argTypes: Array<CodeGenerator.Argument<*>> = arrayOf("message" typedAs Message::class)
    override val scriptArguments: Array<CodeGenerator.Argument<*>> = arrayOf("msg" typedAs Api2Message::class)

    override fun deliver(bot: Bot, args: Array<out Any>) =
        bot.deliverMessage(args[0] as Message)

    override fun isHeldBack(project: Project, args: Array<out Any>): Boolean =
        ChatWaits.claim(project, args[0] as Message)
}

/**
 * A chat message starting with the bot's command prefix arrived; the bot's `command` listeners receive it.
 *
 * A message is a command when its first word, up to the first space, starts with the prefix set with
 * `bot.setCommandPrefix(…)`: with the prefix `/`, the message `/help me` is the command `help` with the argument `me`.
 * The prefix is `""` until the script sets one, which makes every message a command, as in MessengerBot R. The message
 * reaches `message` listeners as well.
 *
 * @param msg The command: the message's members, plus its command name and arguments.
 * @see guide:events#api2-listeners
 */
class Api2CommandEvent: Api2Event() {
    override val id = "command"
    override val name = "API2 명령어 이벤트"
    override val description = translate {
        Locale.ENGLISH { "When a message starts with the bot's command prefix" }
        Locale.KOREAN  { "봇의 명령어 접두사로 시작하는 메시지를 받았을 때" }
    }
    override val functionName = "onCommand"
    override val constant = "COMMAND"
    override val listenerName = Api2Events.COMMAND
    override val parameters = "msg"
    override val setup = "bot.setCommandPrefix(\"@\");"
    override val argTypes: Array<CodeGenerator.Argument<*>> = arrayOf("message" typedAs Message::class)
    override val scriptArguments: Array<CodeGenerator.Argument<*>> = arrayOf("msg" typedAs Api2Command::class)

    override fun deliver(bot: Bot, args: Array<out Any>) =
        bot.deliverCommand(args[0] as Message)

    override fun isHeldBack(project: Project, args: Array<out Any>): Boolean =
        ChatWaits.claim(project, args[0] as Message)
}

/**
 * An app posted a notification; the bot's `notificationPosted` listeners receive it while the app
 * setting **onNotificationPosted 이벤트 사용** is on.
 *
 * Every app's notifications count, except StarLight's own. The setting is off by default.
 *
 * @param sbn The notification, as Android's `StatusBarNotification`.
 * @param sessionManager The notification's session: its reply and mark-as-read actions, and `bindSession` to make a
 * room of it replyable.
 * @see guide:events#notification-events
 */
class Api2NotificationPostedEvent: Api2Event() {
    override val id = "notification_posted"
    override val name = "API2 알림 게시 이벤트"
    override val description = translate {
        Locale.ENGLISH { "When any app posts a notification, with the onNotificationPosted setting on" }
        Locale.KOREAN  { "onNotificationPosted 이벤트 사용을 켰을 때, 아무 앱이나 알림을 올리면" }
    }
    override val functionName = "onNotificationPosted"
    override val constant = "NOTIFICATION_POSTED"
    override val listenerName = Api2Events.NOTIFICATION_POSTED
    override val parameters = "sbn, sessionManager"
    override val argTypes: Array<CodeGenerator.Argument<*>> = arrayOf(
        "sbn" typedAs StatusBarNotification::class,
        "sessionManager" typedAs SessionManager::class,
    )

    override fun deliver(bot: Bot, args: Array<out Any>) =
        bot.deliver(Api2Events.NOTIFICATION_POSTED, args[0], args[1])
}

/**
 * A notification was removed; the bot's `notificationRemoved` listeners receive it while the app
 * setting **onNotificationPosted 이벤트 사용** is on.
 *
 * Every app's notifications count, except StarLight's own. The setting is off by default.
 *
 * @param sbn The removed notification, as Android's `StatusBarNotification`.
 * @param rankingMap Android's `NotificationListenerService.RankingMap` of the notifications still showing.
 * @param reason Why it was removed: one of Android's `NotificationListenerService.REASON_*` constants, such as
 * `REASON_CANCEL` (2).
 * @see guide:events#notification-events
 */
class Api2NotificationRemovedEvent: Api2Event() {
    override val id = "notification_removed"
    override val name = "API2 알림 삭제 이벤트"
    override val description = translate {
        Locale.ENGLISH { "When a notification is removed, with the onNotificationPosted setting on" }
        Locale.KOREAN  { "onNotificationPosted 이벤트 사용을 켰을 때, 알림이 지워지면" }
    }
    override val functionName = "onNotificationRemoved"
    override val constant = "NOTIFICATION_REMOVED"
    override val listenerName = Api2Events.NOTIFICATION_REMOVED
    override val parameters = "sbn, rankingMap, reason"
    override val argTypes: Array<CodeGenerator.Argument<*>> = arrayOf(
        "sbn" typedAs StatusBarNotification::class,
        "rankingMap" typedAs RankingMap::class,
        "reason" typedAs Int::class,
    )

    override fun deliver(bot: Bot, args: Array<out Any>) =
        bot.deliver(Api2Events.NOTIFICATION_REMOVED, args[0], args[1], args[2])
}

/**
 * A compiled project is about to compile again; the bot's `startCompile` listeners receive it before the new code runs,
 * and before `onStartCompile` is called.
 *
 * The listeners run on the thread doing the compile, not on the project's workers. An error in one is logged and
 * doesn't stop the compile. Right after, every listener of the bot is removed; the new code adds its own.
 */
class Api2StartCompileEvent: Api2Event() {
    override val id = "start_compile"
    override val name = "API2 컴파일 시작 이벤트"
    override val description = translate {
        Locale.ENGLISH { "Right before the project is compiled again" }
        Locale.KOREAN  { "프로젝트를 다시 컴파일하기 직전" }
    }
    override val functionName = "onStartCompile"
    override val constant = "START_COMPILE"
    override val listenerName = Api2Events.START_COMPILE
    override val parameters = ""
    override val argTypes: Array<CodeGenerator.Argument<*>> = emptyArray()

    override fun deliver(bot: Bot, args: Array<out Any>) =
        bot.deliver(Api2Events.START_COMPILE)
}
