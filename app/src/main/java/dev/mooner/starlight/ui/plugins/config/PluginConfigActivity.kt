/*
 * PluginConfigActivity.kt created by Minki Moon(mooner1022) on 22. 1. 8. 오후 7:23
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.plugins.config

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.google.android.material.snackbar.Snackbar
import dev.mooner.starlight.databinding.ActivityPluginConfigBinding
import dev.mooner.starlight.plugincore.Session.pluginManager
import dev.mooner.starlight.plugincore.config.ButtonConfigObject
import dev.mooner.starlight.plugincore.config.TypedString
import dev.mooner.starlight.plugincore.config.config
import dev.mooner.starlight.plugincore.config.data.ConfigImpl
import dev.mooner.starlight.plugincore.plugin.StarlightPlugin
import dev.mooner.starlight.plugincore.utils.Icon
import dev.mooner.starlight.ui.config.ParentAdapter
import dev.mooner.starlight.utils.ViewUtils.Companion.bindFadeImage
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
    private var recyclerAdapter: ParentAdapter? = null

    private lateinit var binding: ActivityPluginConfigBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPluginConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val fabProjectConfig = binding.fabPluginConfig
        val configRecyclerView = binding.configRecyclerView

        val pluginName = intent.getStringExtra(EXTRA_PLUGIN_NAME)!!
        val pluginId = intent.getStringExtra(EXTRA_PLUGIN_ID)!!
        val plugin = pluginManager.getPluginById(pluginId)?: error("Failed to get plugin [$pluginName]")
        recyclerAdapter = ParentAdapter(binding.root.context) { parentId, id, view, data ->
            if (changedData.containsKey(parentId)) {
                changedData[parentId]!![id] = data
            } else {
                changedData[parentId] = hashMapOf(id to data)
            }

            if (savedData.containsKey(parentId)) {
                savedData[parentId]!![id] = TypedString.parse(data)
            } else {
                savedData[parentId] = hashMapOf(id to TypedString.parse(data))
            }

            if (!fabProjectConfig.isShown) {
                fabProjectConfig.show()
            }
            plugin.onConfigChanged(id, view, data)
        }

        val configFile = File((plugin as StarlightPlugin).getDataFolder(), PLUGIN_CONFIG_FILE_NAME)
        savedData = try {
            dev.mooner.starlight.plugincore.Session.json.decodeFromString(configFile.readText())
        } catch (e: Exception) {
            mutableMapOf()
        }

        fabProjectConfig.setOnClickListener { view ->
            if (recyclerAdapter!!.isHavingError) {
                Snackbar.make(view, "올바르지 않은 설정이 있습니다. 확인 후 다시 시도해주세요.", Snackbar.LENGTH_SHORT).show()
                fabProjectConfig.hide()
                return@setOnClickListener
            }
            configFile.writeText(dev.mooner.starlight.plugincore.Session.json.encodeToString(savedData))
            plugin.onConfigUpdated(ConfigImpl(savedData), changedData.keys)
            plugin.onConfigUpdated(changedData)
            Snackbar.make(view, "설정 저장 완료!", Snackbar.LENGTH_SHORT).show()
            fabProjectConfig.hide()
        }

        binding.scroll.bindFadeImage(binding.imageViewLogo)

        binding.leave.setOnClickListener { finish() }

        recyclerAdapter!!.apply {
            data = plugin.configObjects + config {
                category {
                    id = "cautious"
                    title = "위험"
                    textColor = color { "#FF865E" }
                    items = items {
                        button {
                            id = "delete_plugin"
                            title = "플러그인 제거"
                            type = ButtonConfigObject.Type.FLAT
                            onClickListener = { view ->
                                MaterialDialog(binding.root.context, BottomSheet(LayoutMode.WRAP_CONTENT)).show {
                                    cornerRadius(25f)
                                    title(text = "정말 [${pluginName}](을)를 삭제할까요?")
                                    message(text = "주의: 삭제시 되돌릴 수 없습니다.")
                                    positiveButton(text = "확인") { dialog ->
                                        pluginManager.removePlugin(pluginId)
                                        Snackbar.make(view, "플러그인을 삭제했습니다.\n앱을 재시작할까요?", Snackbar.LENGTH_LONG)
                                            .setAction("확인") {
                                                restartApplication(context)
                                            }
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
            saved = savedData
            notifyDataSetChanged()
        }

        val layoutManager = LinearLayoutManager(applicationContext)
        configRecyclerView.layoutManager = layoutManager
        configRecyclerView.adapter = recyclerAdapter

        binding.pluginName.text = pluginName
    }

    override fun onDestroy() {
        super.onDestroy()
        recyclerAdapter?.destroy()
        recyclerAdapter = null
    }
}