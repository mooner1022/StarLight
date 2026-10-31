/*
 * PluginConfigActivity.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.plugins.config

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import dev.mooner.configdsl.adapters.ConfigAdapter
import dev.mooner.configdsl.utils.PeekType
import dev.mooner.starlight.databinding.ActivityPluginConfigBinding
import dev.mooner.starlight.logging.bindLogNotifier
import dev.mooner.starlight.plugincore.Session.pluginManager
import dev.mooner.starlight.plugincore.config.data.FileConfig
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.utils.bindFadeImage
import dev.mooner.starlight.utils.peek
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val LOG = LoggerFactory.logger {  }

class PluginConfigActivity: AppCompatActivity() {

    private val updatedIDs    : MutableMap<String, MutableSet<String>> = hashMapOf()
    private var configAdapter : ConfigAdapter? = null

    private lateinit var binding: ActivityPluginConfigBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPluginConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bindLogNotifier()

        val fabProjectConfig = binding.fabPluginConfig

        val pluginName = intent.getStringExtra(EXTRA_PLUGIN_NAME)!!
        val pluginId = intent.getStringExtra(EXTRA_PLUGIN_ID)!!
        val plugin = pluginManager.getPluginById(pluginId) ?: error("Failed to find plugin with id: $pluginId, name: $pluginName")

        val configFile = File(plugin.getExternalDataDirectory(), PLUGIN_CONFIG_FILE_NAME)
        val fileConfig = FileConfig(configFile)

        val pluginStructure = runPluginCall(pluginName) { plugin.getConfigStructure() }
        if (pluginStructure == null) {
            finish()
            return
        }

        configAdapter = ConfigAdapter.Builder(this) {
            bind(binding.configRecyclerView)
            onValueUpdated { parentId, id, value, jsonValue ->
                updatedIDs.computeIfAbsent(parentId) { hashSetOf() } += id
                if (!fabProjectConfig.isShown)
                    withContext(Dispatchers.Main) {
                        fabProjectConfig.show()
                    }
                runPluginCall(pluginName) { plugin.onConfigValueUpdated(id, value) }
                fileConfig.category(parentId).setRaw(id, jsonValue)
            }
            structure { pluginStructure }
            configData(fileConfig.getMutableData())
            lifecycleOwner(this@PluginConfigActivity)
        }.build()

        fabProjectConfig.setOnClickListener {
            if (configAdapter?.hasError == true) {
                peek("올바르지 않은 설정값이 있습니다. 확인 후 다시 시도해주세요.", PeekType.ERROR)
                fabProjectConfig.hide()
                return@setOnClickListener
            }
            fileConfig.push()
            runPluginCall(pluginName) { plugin.onConfigUpdated(fileConfig, updatedIDs) }
            peek("설정 저장 완료!", PeekType.SUCCESS)
            fabProjectConfig.hide()
        }

        binding.scroll.bindFadeImage(binding.imageViewLogo)

        binding.leave.setOnClickListener { finish() }

        binding.pluginName.text = pluginName
    }

    private inline fun <T> runPluginCall(pluginName: String, block: () -> T): T? =
        try {
            block()
        } catch (e: Exception) {
            logPluginError(pluginName, e)
            null
        } catch (e: LinkageError) {
            logPluginError(pluginName, e)
            null
        }

    private fun logPluginError(pluginName: String, error: Throwable) =
        LOG.error {
            translate {
                Locale.ENGLISH { "Plugin $pluginName failed while handling its settings: $error" }
                Locale.KOREAN  { "플러그인 $pluginName 의 설정을 처리하다 오류가 났어요: $error" }
            }
        }

    companion object {
        const val EXTRA_PLUGIN_NAME               = "pluginName"
        const val EXTRA_PLUGIN_ID                 = "pluginId"
        private const val PLUGIN_CONFIG_FILE_NAME = "config-plugin.json"
    }
}