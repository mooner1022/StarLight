package com.mooner.starlight.plugincore.widget

abstract class Widget: IWidget {

    override val size: WidgetSize = WidgetSize.Medium

    override fun equals(other: Any?): Boolean = when(other) {
        null -> false
        is IWidget -> other.id == this.id
        else -> false
    }

    override fun hashCode(): Int = this.id.hashCode()
}