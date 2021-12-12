package com.mooner.starlight.core

import android.app.Application
import com.mooner.starlight.core.session.ApplicationSession
import com.mooner.starlight.plugincore.logger.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GlobalApplication: Application() {

    companion object {
        private val T = GlobalApplication::class.simpleName
    }

    override fun onCreate() {
        super.onCreate()
        Logger.v(T, "Application onCreate() called")

        CoroutineScope(Dispatchers.Default).launch {
            ApplicationSession.init(applicationContext)
        }
    }
}