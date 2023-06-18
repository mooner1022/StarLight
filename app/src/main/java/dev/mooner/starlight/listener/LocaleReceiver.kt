/*
 * LocaleReceiver.kt created by Minki Moon(mooner1022) on 1/28/23, 8:51 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.listener

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.mooner.starlight.R
import dev.mooner.starlight.plugincore.event.EventHandler
import dev.mooner.starlight.plugincore.event.Events
import dev.mooner.starlight.plugincore.translation.Locale

class LocaleReceiver: BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_LOCALE_CHANGED) return

        val locale = context
            .getString(R.string.locale_name)
            .let(Locale::valueOf)

        EventHandler.fireEventWithScope(Events.Locale.Update(locale))
    }
}