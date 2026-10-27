/*
 * ConfigBuilderBase.kt created by Minki Moon(mooner1022) on 2/17/24, 3:03 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.configdsl

import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import java.io.File

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

    fun setIcon(icon: Icon? = null, iconFile: File? = null, @DrawableRes iconResId: Int? = null) {
        when {
            icon != null -> this.icon = icon
            iconFile != null -> this.iconFile = iconFile
            iconResId != null -> this.iconResId = iconResId
        }
    }

    abstract fun build(): ConfigOption<T, V>

    protected fun requiredField(fieldName: String, value: Any?) {
        if (value == null) {
            throw IllegalArgumentException("Required field '$fieldName' of config option '${_id ?: "(no id)"}' is null")
        }
    }
}