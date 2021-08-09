package com.mooner.starlight.ui.plugins.config

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.mooner.starlight.R
import com.mooner.starlight.databinding.ActivityPluginConfigBinding
import com.mooner.starlight.plugincore.TypedString
import com.mooner.starlight.plugincore.core.Session
import com.mooner.starlight.plugincore.core.Session.Companion.pluginLoader
import com.mooner.starlight.plugincore.plugin.StarlightPlugin
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File

class PluginConfigActivity : AppCompatActivity() {

    private val changedData: MutableMap<String, Any> = mutableMapOf()
    private lateinit var savedData: MutableMap<String, TypedString>
    private lateinit var binding: ActivityPluginConfigBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPluginConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val fabProjectConfig = binding.fabPluginConfig
        val configRecyclerView = binding.configRecyclerView

        val pluginName = intent.getStringExtra("pluginName")!!
        val pluginId = intent.getStringExtra("pluginId")!!
        val plugin = pluginLoader.getPluginById(pluginId)?: error("Failed to get plugin [$pluginName]")
        val recyclerAdapter = PluginConfigAdapter(applicationContext) { id, view, data ->
            changedData[id] = data
            savedData[id] = TypedString.parse(data)
            if (!fabProjectConfig.isShown) {
                fabProjectConfig.show()
            }
            plugin.onConfigChanged(id, view, data)
        }

        val configFile = File((plugin as StarlightPlugin).getDataFolder(), "config-plugin.json")
        savedData = try {
            Session.json.decodeFromString(configFile.readText())
        } catch (e: Exception) {
            mutableMapOf()
        }

        fabProjectConfig.setOnClickListener {
            configFile.writeText(Session.json.encodeToString(savedData))
            plugin.onConfigUpdated(changedData)
            Snackbar.make(it, "설정 저장 완료!", Snackbar.LENGTH_SHORT).show()
            fabProjectConfig.hide()
        }

        binding.scroll.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            if (scrollY in 0..200) {
                binding.imageViewLogo.alpha = 1f - (scrollY / 200.0f)
            } else {
                binding.imageViewLogo.alpha = 0f
            }
        }

        recyclerAdapter.data = plugin.configObjects.toList()
        recyclerAdapter.saved = savedData
        recyclerAdapter.notifyDataSetChanged()

        val layoutManager = LinearLayoutManager(applicationContext)
        configRecyclerView.layoutManager = layoutManager
        configRecyclerView.adapter = recyclerAdapter

        val textViewConfigProjectName: TextView = findViewById(R.id.textViewConfigProjectName)
        textViewConfigProjectName.text = pluginName
    }
}