/*
 * ProjectEvent.kt created by Minki Moon(mooner1022) on 22. 2. 2. 오후 5:53
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.project.event

import dev.mooner.starlight.plugincore.language.CodeGenerator
import dev.mooner.starlight.plugincore.project.Project
import kotlin.reflect.KClass

abstract class ProjectEvent {

    abstract val id: String

    abstract val name: String

    abstract val functionName: String

    abstract val argTypes: Array<CodeGenerator.Argument<*>>

    /**
     * Generate comment above the event function
     */
    open val functionComment: String? = null

    /** When the event happens, in a few words, for lists of events; they show [name] without it. */
    open val description: String? = null

    /**
     * The arguments a script's function or listener receives: the ones the event is fired with, unless [dispatch]
     * changes them, as API2 events do when they hand their listeners wrappers. Agent docs describe these.
     */
    open val scriptArguments: Array<CodeGenerator.Argument<*>>
        get() = argTypes

    /**
     * For an event a script receives through a listener rather than a function: the name it passes to add one, such
     * as API2's `"message"` in `bot.on("message", …)`. Agent docs pair it with the listener's arguments.
     */
    open val listenerName: String?
        get() = null

    /** How a script receives the event, as lists of events show it: by default, the function it calls. */
    open val usage: String
        get() = argTypes.joinToString(prefix = "$functionName(", postfix = ")") { it.name }

    /**
     * Delivers the event to [project]'s script, which is allowed to receive it. Calls the function named
     * [functionName] unless the event reaches the script another way, such as through listeners it registered.
     */
    open fun dispatch(project: Project, args: Array<out Any>, onFailure: (Throwable) -> Unit) =
        project.callFunction(functionName, args, onFailure)

    /** The code a new project starts with for this event; a function named [functionName] unless it needs more. */
    open fun generateCode(generator: CodeGenerator): CodeGenerator.GeneratedFunction =
        generator.generateFunction(functionName, argTypes, null)

    /**
     * The name the starting code gives this event's function instead of [functionName] when another event of the new
     * project already has a function of that name, as API2's message listener and StarLight's `onMessage` do; null
     * to leave this one commented out then.
     */
    open val alternateFunctionName: String?
        get() = null

    /** [generateCode], with the function named [name]: [functionName], or [alternateFunctionName] on a clash. */
    open fun generateCode(generator: CodeGenerator, name: String): CodeGenerator.GeneratedFunction =
        if (name == functionName) generateCode(generator)
        else generator.generateFunction(name, argTypes, null)

    override fun equals(other: Any?): Boolean = other is ProjectEvent && other.id == id

    override fun hashCode(): Int = name.hashCode() + id.hashCode()

    protected inline fun <reified T: Any> typedAs(name: String) =
        name typedAs T::class

    protected infix fun <T: Any> String.typedAs(type: KClass<T>): CodeGenerator.Argument<T> =
        CodeGenerator.Argument(this, type)
}