/*
 * PopupUtils.kt created by Minki Moon(mooner1022) on 25. 1. 17. 오후 9:22
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.utils

import android.content.Context
import android.content.res.ColorStateList
import android.view.Menu
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.MenuCompat
import dev.mooner.starlight.R
import dev.mooner.starlight.ui.presets.RoundedRectDrawable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import me.saket.cascade.CascadePopupMenu

fun View.showPopup(builder: PopupBuilder.PopupMenuBuilder.() -> Unit): Flow<String> {
    return callbackFlow {
        val menu = CascadePopupMenu(this@showPopup.context, this@showPopup, styler = getMenuStyler(this@showPopup.context))
        menu.popup.setOnDismissListener(::close)
        MenuCompat.setGroupDividerEnabled(menu.menu, true)

        val built = PopupBuilder.PopupMenuBuilder().apply(builder)
        configureMenu(menu.menu, built)

        menu.setOnMenuItemClickListener { item ->
            trySend(item.title.toString())
            true
        }
        menu.show()

        awaitClose {
            menu.setOnMenuItemClickListener(null)
        }
    }
}

private fun getMenuStyler(context: Context): CascadePopupMenu.Styler {
    return CascadePopupMenu.Styler(
        background = {
            RoundedRectDrawable(context.getColor(R.color.background_log_card), radius = dp(8f))
        },
        menuTitle = {
            it.titleView.typeface = ResourcesCompat.getFont(context, R.font.nanumsquare_neo_regular)
        },
        menuItem = {
            it.titleView.typeface = ResourcesCompat.getFont(context, R.font.nanumsquare_neo_regular)
        }
    )
}

fun View.showPopup(vararg items: Pair<String, Int>): Flow<String> {
    return showPopup {
        for ((title, icon) in items) {
            item(title) { this.icon = icon }
        }
    }
}

private fun configureMenu(menu: Menu, builder: PopupBuilder.PopupMenuBuilder) {
    for ((title, config) in builder.build()) {
        when (config) {
            is PopupBuilder.PopupItemBuilder ->
                menu.add(title).also {
                    if (config.icon != null)
                        it.setIcon(config.icon!!)
                    else
                        it.setIcon(null)
                    it.icon?.setTintList(config.iconTintList)

                    it.isEnabled   = config.enabled
                    it.isCheckable = config.checkable
                    it.isChecked   = config.checked
                }
            is PopupBuilder.PopupMenuBuilder ->
                menu.addSubMenu(title).also {
                    if (config.icon != null)
                        it.setIcon(config.icon!!)
                    else
                        it.setIcon(null)
                    //it.icon?.setTintList(config.iconTintList)

                    configureMenu(it, config)
                }
        }
    }
}

sealed class PopupBuilder {

    @DrawableRes
    var icon: Int? = null
    var iconTintList: ColorStateList? = null

    fun iconColor(@ColorInt colorInt: Int) {
        iconTintList = ColorStateList.valueOf(colorInt)
    }

    fun iconColorRes(context: Context, @ColorRes colorRes: Int) {
        iconTintList = context.getColorStateList(colorRes)
    }

    class PopupItemBuilder: PopupBuilder() {

        var enabled   : Boolean = true
        var checkable : Boolean = false
        var checked   : Boolean = false
    }

    class PopupMenuBuilder: PopupBuilder() {
        private val entries: MutableList<Pair<String, PopupBuilder>> = arrayListOf()

        fun item(title: String, config: (PopupItemBuilder.() -> Unit) = {}) {
            val builder = PopupItemBuilder().apply(config)
            entries += title to builder
        }

        fun submenu(title: String, config: PopupMenuBuilder.() -> Unit) {
            val builder = PopupMenuBuilder().apply(config)
            entries += title to builder
        }

        fun build(): List<Pair<String, PopupBuilder>> {
            return entries
        }
    }
}