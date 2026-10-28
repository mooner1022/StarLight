package dev.mooner.starlight.utils.align

import dev.mooner.starlight.ui.common.SheetGridItem

fun <T> Array<Align<T>>.toGridItems(): List<SheetGridItem> = this.map { item ->
    SheetGridItem(
        icon = item.icon,
        title = item.name
    )
}