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
import dev.mooner.configdsl.Icon
import dev.mooner.configdsl.adapters.ConfigAdapter
import dev.mooner.configdsl.config
import dev.mooner.configdsl.options.button
import dev.mooner.starlight.databinding.ActivityPluginConfigBinding
import dev.mooner.starlight.logging.bindLogNotifier
import dev.mooner.starlight.plugincore.Session.pluginManager
import dev.mooner.starlight.plugincore.config.data.FileConfig
import dev.mooner.starlight.plugincore.plugin.StarlightPlugin
import dev.mooner.starlight.utils.bindFadeImage
import dev.mooner.starlight.utils.restartApplication
import dev.mooner.starlight.utils.setCommonAttrs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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

        configAdapter = ConfigAdapter.Builder(this) {
            bind(binding.configRecyclerView)
            onValueUpdated { parentId, id, value, jsonValue ->
                updatedIDs.computeIfAbsent(parentId) { hashSetOf() } += id
                if (!fabProjectConfig.isShown)
                    withContext(Dispatchers.Main) {
                        fabProjectConfig.show()
                    }
                plugin.onConfigValueUpdated(id, value)
                fileConfig.category(parentId).setRaw(id, jsonValue)
            }
            structure { getConfig(plugin) }
            configData(fileConfig.getMutableData())
            lifecycleOwner(this@PluginConfigActivity)
        }.build()

        fabProjectConfig.setOnClickListener { view ->
            if (configAdapter?.hasError == true) {
                Snackbar.make(view, "올바르지 않은 설정값이 있습니다. 확인 후 다시 시도해주세요.", Snackbar.LENGTH_SHORT).show()
                fabProjectConfig.hide()
                return@setOnClickListener
            }
            fileConfig.push()
            plugin.onConfigUpdated(fileConfig, updatedIDs)
            Snackbar.make(view, "설정 저장 완료!", Snackbar.LENGTH_SHORT).show()
            fabProjectConfig.hide()
        }

        binding.scroll.bindFadeImage(binding.imageViewLogo)

        binding.leave.setOnClickListener { finish() }

        binding.pluginName.text = pluginName
    }

    private fun getConfig(plugin: StarlightPlugin) = plugin.getConfigStructure() + config {
        category {
            id = "cautious"
            title = "위험"
            textColor = color { "#FF865E" }
            items {
                button {
                    id = "delete_plugin"
                    title = "플러그인 제거"
                    setOnClickListener { view ->
                        MaterialDialog(binding.root.context, BottomSheet(LayoutMode.WRAP_CONTENT)).show {
                            setCommonAttrs()
                            title(text = "정말 [${plugin.info.fullName}](을)를 삭제할까요?")
                            message(text = "주의: 모든 설정과 하위 디렉토리가 함께 삭제되며, 되돌릴 수 없습니다.")
                            positiveButton(text = "확인") { dialog ->
                                pluginManager.removePlugin(plugin.info.id)
                                Snackbar.make(view, "플러그인을 삭제했습니다.\n앱을 재시작할까요?", Snackbar.LENGTH_LONG)
                                    .setAction("확인") {
                                        restartApplication()
                                    }
                                    .setAnimationMode(Snackbar.ANIMATION_MODE_SLIDE)
                                    .show()
                                dialog.dismiss()
                            }
                            negativeButton(text = "취소") { dialog ->
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

    companion object {
        private const val EXTRA_PLUGIN_NAME       = "pluginName"
        private const val EXTRA_PLUGIN_ID         = "pluginId"
        private const val PLUGIN_CONFIG_FILE_NAME = "config-plugin.json"
    }
}