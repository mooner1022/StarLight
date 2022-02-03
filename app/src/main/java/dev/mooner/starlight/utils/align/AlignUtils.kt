package dev.mooner.starlight.utils.align

import com.afollestad.materialdialogs.bottomsheets.BasicGridItem

fun <T> Array<Align<T>>.toGridItems(): List<BasicGridItem> = this.map { item ->
    BasicGridItem(
        iconRes = item.icon,
        title = item.name
    )
}