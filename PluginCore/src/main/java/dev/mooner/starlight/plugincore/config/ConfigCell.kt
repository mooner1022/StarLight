/*
 * ConfigCell.kt created by Minki Moon(mooner1022) on 4/23/23, 11:37 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.plugincore.config

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import dev.mooner.starlight.plugincore.config.data.TypedString

abstract class ConfigCell<Builder: ConfigCellBuilder, T> {

    fun requestUpdate() {

    }

    final fun attach() {

    }

    protected fun notifyValueUpdated(value: T) {

    }

    abstract fun inflateView(parentView: ViewGroup): View

    abstract fun setData(view: View, data: T)

    abstract fun setEnabled(enabled: Boolean)

    abstract fun encodeData(data: T): TypedString<T>

    private fun onEventReceived(event: String) {

    }
}

interface ConfigCellBuilder {

}

internal fun inflateFromXml(parentView: ViewGroup, @LayoutRes layout: Int): View {
    return LayoutInflater.from(parentView.context).inflate(layout, parentView, false)
}