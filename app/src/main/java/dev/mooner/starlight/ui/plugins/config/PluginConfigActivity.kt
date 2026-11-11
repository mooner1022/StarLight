/*
 * PluginConfigActivity.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.plugins.config

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import dev.mooner.configdsl.MutableDataMap
import dev.mooner.configdsl.adapters.ConfigAdapter
import dev.mooner.configdsl.utils.PeekType
import dev.mooner.starlight.databinding.ActivityPluginConfigBinding
import dev.mooner.starlight.logging.bindLogNotifier
import dev.mooner.starlight.plugincore.Session.pluginManager
import dev.mooner.starlight.plugincore.config.data.FileConfig
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.R
import dev.mooner.starlight.ui.common.confirmLeavingUnsaved
import dev.mooner.starlight.utils.ConfigDrafts
import dev.mooner.starlight.utils.bindPageHeader
import dev.mooner.starlight.utils.fitSkyPage
import dev.mooner.starlight.utils.peek
import dev.mooner.starlight.utils.restartIfNotReady
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val LOG = LoggerFactory.logger {  }

class PluginConfigActivity: AppCompatActivity() {

    private val updatedIDs    : MutableMap<String, MutableSet<String>> = hashMapOf()
    /** The values changed and not saved yet, kept through the screen's own end as [updatedIDs] can't be. */
    private val changedData   : MutableDataMap = hashMapOf()
    private var configAdapter : ConfigAdapter? = null

    private lateinit var binding: ActivityPluginConfigBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        // Brought back before the runtime started: start over from the splash, without restoring what needs it.
        if (restartIfNotReady()) {
            super.onCreate(null)
            finish()
            return
        }
        super.onCreate(savedInstanceState)
        binding = ActivityPluginConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)
        fitSkyPage(binding.root, binding.skyHeader, binding.scroll, binding.leave)

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

        // What was changed and not saved when the screen was last taken down, as on a rotation: put back into the
        // settings read from the file, which is where the screen keeps what isn't saved yet.
        changedData.putAll(ConfigDrafts.decode(savedInstanceState?.getString(STATE_CHANGED)))
        for ((category, values) in changedData) {
            updatedIDs.computeIfAbsent(category) { hashSetOf() } += values.keys
            values.forEach(fileConfig.category(category)::setRaw)
        }

        configAdapter = ConfigAdapter.Builder(this) {
            bind(binding.configRecyclerView)
            onValueUpdated { parentId, id, value, jsonValue ->
                updatedIDs.computeIfAbsent(parentId) { hashSetOf() } += id
                changedData.getOrPut(parentId) { hashMapOf() }[id] = jsonValue
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

        val save: () -> Boolean = save@{
            // The button stays: hidden, there would be no way to save once the setting is put right.
            if (configAdapter?.hasError == true) {
                peek(getString(R.string.config_invalid), PeekType.ERROR)
                return@save false
            }
            fileConfig.push()
            runPluginCall(pluginName) { plugin.onConfigUpdated(fileConfig, updatedIDs) }
            changedData.clear()
            peek(getString(R.string.config_saved), PeekType.SUCCESS)
            fabProjectConfig.hide()
            true
        }
        fabProjectConfig.setOnClickListener { save() }

        bindPageHeader(binding.scroll, binding.skyHeader)

        confirmLeavingUnsaved(binding.leave, hasChanges = { fabProjectConfig.isOrWillBeShown }, save = save)

        binding.pluginName.text = pluginName
        if (changedData.isNotEmpty())
            fabProjectConfig.show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (changedData.isNotEmpty())
            outState.putString(STATE_CHANGED, ConfigDrafts.encode(changedData))
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
        private const val STATE_CHANGED           = "changed"
    }
}