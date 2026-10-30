/*
 * ConfigBuilderBase.kt created by Minki Moon(mooner1022) on 2/17/24, 3:03 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.configdsl

import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.io.File
import kotlin.time.Duration

abstract class ConfigBuilderBase<T: BaseViewHolder, V: Any> {

    private var _id: String? = null
    var id: String
        get() = _id ?: error("Config option '${title ?: "(untitled)"}' is missing an id")
        set(value) {
            _id = value
        }
    var title: String? = null
    var description: String? = null

    var icon: Icon? = null
    var iconFile: File? = null
    @DrawableRes
    var iconResId: Int? = null

    @ColorInt
    var iconTintColor: Int? = null

    var dependency: String? = null

    private val bindings: MutableList<OptionBinding> = arrayListOf()
    private var visibleRule: ValueRule? = null

    fun setIcon(icon: Icon? = null, iconFile: File? = null, @DrawableRes iconResId: Int? = null) {
        when {
            icon != null -> this.icon = icon
            iconFile != null -> this.iconFile = iconFile
            iconResId != null -> this.iconResId = iconResId
        }
    }

    fun bindTitle(flow: Flow<String>) =
        addBinding { flow.collect { updateTitle(it) } }

    fun bindDescription(flow: Flow<String?>) =
        addBinding { flow.collect { updateDescription(it) } }

    fun bindDescription(every: Duration, block: suspend () -> String?) =
        bindDescription(tickerFlow(every, block))

    fun visibleWhen(id: String, predicate: (JsonElement?) -> Boolean) {
        visibleRule = ValueRule(id, predicate)
    }

    fun visibleWhen(id: String, vararg values: String) =
        visibleWhen(id) { (it as? JsonPrimitive)?.contentOrNull in values }

    fun visibleWhen(id: String, value: Boolean) =
        visibleWhen(id) { (it as? JsonPrimitive)?.booleanOrNull == value }

    protected fun addBinding(binding: OptionBinding) {
        bindings += binding
    }

    internal fun applyExtras(option: ConfigOption<*, *>) {
        option.bindings += bindings
        option.visibleRule = visibleRule
    }

    abstract fun build(): ConfigOption<T, V>

    protected fun requiredField(fieldName: String, value: Any?) {
        if (value == null) {
            throw IllegalArgumentException("Required field '$fieldName' of config option '${_id ?: "(no id)"}' is null")
        }
    }
}

internal fun <T> tickerFlow(every: Duration, block: suspend () -> T): Flow<T> = flow {
    while (true) {
        emit(block())
        delay(every)
    }
}
