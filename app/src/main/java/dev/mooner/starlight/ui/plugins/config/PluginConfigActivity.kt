/*
 * PluginConfigActivity.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.plugins.config

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.google.android.material.snackbar.Snackbar
import dev.mooner.starlight.databinding.ActivityPluginConfigBinding
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.Session.pluginManager
import dev.mooner.starlight.plugincore.config.ButtonConfigObject
import dev.mooner.starlight.plugincore.config.ConfigImpl
import dev.mooner.starlight.plugincore.config.TypedString
import dev.mooner.starlight.plugincore.config.config
import dev.mooner.starlight.plugincore.plugin.Plugin
import dev.mooner.starlight.plugincore.utils.Icon
import dev.mooner.starlight.ui.config.ConfigAdapter
import dev.mooner.starlight.utils.bindFadeImage
import dev.mooner.starlight.utils.restartApplication
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File

class PluginConfigActivity: AppCompatActivity() {

    companion object {
        private const val EXTRA_PLUGIN_NAME = "pluginName"
        private const val EXTRA_PLUGIN_ID = "pluginId"
        private const val PLUGIN_CONFIG_FILE_NAME = "config-plugin.json"
    }

    private val changedData: MutableMap<String, MutableMap<String, Any>> = hashMapOf()
    private lateinit var savedData: MutableMap<String, MutableMap<String, TypedString>>
    private var configAdapter: ConfigAdapter? = null

    private lateinit var binding: ActivityPluginConfigBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPluginConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val fabProjectConfig = binding.fabPluginConfig

        val pluginName = intent.getStringExtra(EXTRA_PLUGIN_NAME)!!
        val pluginId = intent.getStringExtra(EXTRA_PLUGIN_ID)!!
        val plugin = pluginManager.getPluginById(pluginId)?: error("Failed to find plugin with id: $pluginId, name: $pluginName")

        val configFile = File(plugin.getDataFolder(), PLUGIN_CONFIG_FILE_NAME)
        savedData = try {
            Session.json.decodeFromString(configFile.readText())
        } catch (e: Exception) {
            mutableMapOf()
        }

        configAdapter = ConfigAdapter.Builder(this) {
            bind(binding.configRecyclerView)
            onConfigChanged { parentId, id, view, data ->
                if (parentId in changedData)
                    changedData[parentId]!![id] = data
                else
                    changedData[parentId] = hashMapOf(id to data)

                if (parentId in savedData)
                    savedData[parentId]!![id] = TypedString.parse(data)
                else
                    savedData[parentId] = hashMapOf(id to TypedString.parse(data))

                if (!fabProjectConfig.isShown) {
                    fabProjectConfig.show()
                }
                plugin.onConfigChanged(id, view, data)
            }
            structure { getConfig(plugin) }
            savedData(savedData)
            lifecycleOwner(this@PluginConfigActivity)
        }.build()

        fabProjectConfig.setOnClickListener { view ->
            if (configAdapter?.hasError == true) {
                Snackbar.make(view, "???????????? ?????? ????????? ????????????. ?????? ??? ?????? ??????????????????.", Snackbar.LENGTH_SHORT).show()
                fabProjectConfig.hide()
                return@setOnClickListener
            }
            configFile.writeText(Session.json.encodeToString(savedData))
            plugin.onConfigUpdated(ConfigImpl(savedData), changedData.mapValues { it.value.keys.toSet() })
            plugin.onConfigUpdated(changedData)
            Snackbar.make(view, "?????? ?????? ??????!", Snackbar.LENGTH_SHORT).show()
            fabProjectConfig.hide()
        }

        binding.scroll.bindFadeImage(binding.imageViewLogo)

        binding.leave.setOnClickListener { finish() }

        binding.pluginName.text = pluginName
    }

    private fun getConfig(plugin: Plugin) = plugin.getConfigStructure() + config {
        category {
            id = "cautious"
            title = "??????"
            textColor = color { "#FF865E" }
            items {
                button {
                    id = "delete_plugin"
                    title = "???????????? ??????"
                    type = ButtonConfigObject.Type.FLAT
                    setOnClickListener { view ->
                        MaterialDialog(binding.root.context, BottomSheet(LayoutMode.WRAP_CONTENT)).show {
                            cornerRadius(25f)
                            title(text = "?????? [${plugin.info.fullName}](???)??? ????????????????")
                            message(text = "??????: ????????? ????????? ??? ????????????.")
                            positiveButton(text = "??????") { dialog ->
                                pluginManager.removePlugin(plugin.info.id)
                                Snackbar.make(view, "??????????????? ??????????????????.\n?????? ???????????????????", Snackbar.LENGTH_LONG)
                                    .setAction("??????") {
                                        restartApplication()
                                    }
                                    .show()
                                dialog.dismiss()
                            }
                            negativeButton(text = "??????") { dialog ->
                                dialog.dismiss()
                            }
                        }
                    }
                    icon = Icon.DELETE_SWEEP
                    //backgroundColor = Color.parseColor("#B8DFD8")
                    iconTintColor = color { "#FF5C58" }
                }
            }
        }
    }
}