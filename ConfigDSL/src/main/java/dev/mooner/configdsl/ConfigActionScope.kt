package dev.mooner.configdsl

import android.content.Context
import android.view.View
import dev.mooner.configdsl.utils.PeekType
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

typealias ConfigAction = suspend ConfigActionScope.() -> Unit

interface ConfigActionScope {

    val context: Context

    fun peek(message: String, type: PeekType = PeekType.INFO)

    fun setDescription(description: String?)

    fun resetDescription()

    suspend fun confirm(title: String, message: String): Boolean

    suspend fun input(title: String, hint: String = "", initial: String = ""): String?

    suspend fun select(title: String, items: List<String>, selected: Int = -1): Int?
}

internal class ConfigActionScopeImpl(
    private val view  : View,
    private val option: ConfigOption<*, *>,
): ConfigActionScope {

    private val provider
        get() = ConfigDSL.dialogProvider

    override val context: Context
        get() = view.context

    override fun peek(message: String, type: PeekType) =
        provider.peek(view, message, type)

    override fun setDescription(description: String?) {
        option.updateDescription(description)
    }

    override fun resetDescription() {
        option.resetText()
    }

    override suspend fun confirm(title: String, message: String): Boolean =
        suspendCancellableCoroutine { cont ->
            provider.confirm(context, title, message) { if (cont.isActive) cont.resume(it) }
        }

    override suspend fun input(title: String, hint: String, initial: String): String? =
        suspendCancellableCoroutine { cont ->
            provider.input(context, title, hint, initial) { if (cont.isActive) cont.resume(it) }
        }

    override suspend fun select(title: String, items: List<String>, selected: Int): Int? =
        suspendCancellableCoroutine { cont ->
            provider.select(context, title, items, selected) { if (cont.isActive) cont.resume(it) }
        }
}
