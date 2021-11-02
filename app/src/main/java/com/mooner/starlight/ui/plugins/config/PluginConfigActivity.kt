package com.mooner.starlight.ui.plugins.config

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.mooner.starlight.databinding.ActivityPluginConfigBinding
import com.mooner.starlight.plugincore.core.Session
import com.mooner.starlight.plugincore.core.Session.pluginManager
import com.mooner.starlight.plugincore.models.TypedString
import com.mooner.starlight.plugincore.plugin.StarlightPlugin
import com.mooner.starlight.ui.config.ParentAdapter
import com.mooner.starlight.utils.ViewUtils.Companion.bindFadeImage
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
        recyclerAdapter = ParentAdapter(applicationContext) { parentId, id, view, data ->
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
            Session.json.decodeFromString(configFile.readText())
        } catch (e: Exception) {
            mutableMapOf()
        }

        fabProjectConfig.setOnClickListener { view ->
            if (recyclerAdapter!!.isHavingError) {
                Snackbar.make(view, "올바르지 않은 설정이 있습니다. 확인 후 다시 시도해주세요.", Snackbar.LENGTH_SHORT).show()
                fabProjectConfig.hide()
                return@setOnClickListener
            }
            configFile.writeText(Session.json.encodeToString(savedData))
            plugin.onConfigUpdated(changedData)
            Snackbar.make(view, "설정 저장 완료!", Snackbar.LENGTH_SHORT).show()
            fabProjectConfig.hide()
        }

        binding.scroll.bindFadeImage(binding.imageViewLogo)

        binding.leave.setOnClickListener { finish() }

        recyclerAdapter!!.apply {
            data = plugin.configObjects.toList()
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