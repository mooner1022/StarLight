/*
 * SchemeHandlerActivity.kt created by Minki Moon(mooner1022) on 23. 2. 12. 오전 2:06
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.scheme

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dev.mooner.starlight.databinding.ActivitySchemeHandlerBinding
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.event.EventHandler
import dev.mooner.starlight.plugincore.event.Events
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.plugincore.plugin.StarlightPlugin
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.utils.warnTranslated
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val logger = LoggerFactory.logger {  }

class SchemeHandlerActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySchemeHandlerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySchemeHandlerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val action = intent.action
        val data = intent.data
        if (action != Intent.ACTION_VIEW || data == null) {
            finish()
            return
        }

        val pluginId = intent.data!!.path!!.split("/").getOrNull(1) ?: let {
            logger.warnTranslated {
                Locale.ENGLISH { "Failed to parse path data: ${intent.data?.path}" }
                Locale.KOREAN  { "path 데이터를 처리하지 못함: ${intent.data?.path}" }
            }
            finish()
            return
        }
        if (pluginId.isBlank()) {
            logger.warnTranslated { 
                Locale.ENGLISH { "Failed to handle scheme event: pluginId is blank" }
                Locale.KOREAN  { "scheme 이벤트를 처리하지 못함: pluginId가 비어있음" }
            }
            finish()
            return
        }

        val isGlobal = pluginId == "global"

        val params: MutableMap<String, String> = hashMapOf()
        for (name in data.queryParameterNames)
            params[name] = data.getQueryParameter(name) ?: continue

        logger.verbose { "Firing scheme event, pluginId: $pluginId, isGlobal: $isGlobal, params: $params" }

        val listeners = if (isGlobal)
            Session.pluginManager
                .plugins
                .map(StarlightPlugin::getListeners)
                .flatten()
        else
            Session.pluginManager
                .getPluginById(pluginId)
                ?.getListeners()
                ?: let {
                    logger.warnTranslated {
                        Locale.ENGLISH { "Failed to handle scheme event: no such plugin: $pluginId" }
                        Locale.KOREAN  { "scheme 이벤트를 처리하지 못함: 존재하지 않는 플러그인: $pluginId" }
                    }
                    finish()
                    return
                }

        lifecycleScope.launch {
            listeners.asFlow()
                .onEach {
                    it.onSchemeAction(this@SchemeHandlerActivity, false, params) }
                .collect()
            EventHandler.fireEvent(
                Events.Scheme.SchemeOpenEvent(params)
            )
            withContext(Dispatchers.Main) {
                finish()
            }
        }
    }
}