package com.mooner.starlight.utils

import com.afollestad.materialdialogs.bottomsheets.BasicGridItem
import com.mooner.starlight.models.Align

fun <T> Array<Align<T>>.toGridItems(): List<BasicGridItem> = this.map { item ->
    BasicGridItem(
        iconRes = item.icon,
        title = item.name
    )
}