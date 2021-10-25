package com.mooner.starlight.ui.plugins

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.size.Scale
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.google.android.material.snackbar.Snackbar
import com.mooner.starlight.R
import com.mooner.starlight.plugincore.core.Session
import com.mooner.starlight.plugincore.plugin.Plugin
import com.mooner.starlight.plugincore.plugin.StarlightPlugin
import com.mooner.starlight.ui.plugins.config.PluginConfigActivity
import com.mooner.starlight.utils.Utils
import com.mooner.starlight.utils.Utils.Companion.trimLength
import java.io.File

class PluginsListAdapter(
    private val context: Context
): RecyclerView.Adapter<PluginsListAdapter.PluginListViewHolder>() {
    var data = listOf<Plugin>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PluginListViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.card_plugins, parent, false)
        return PluginListViewHolder(view)
    }

    override fun getItemCount(): Int = data.size

    override fun getItemViewType(position: Int): Int = 0

    override fun onBindViewHolder(holder: PluginListViewHolder, position: Int) {
        val plugin = data[position] as StarlightPlugin
        val config = plugin.info

        with(holder) {
            name.text = config.name.trimLength(17)
            version.text = config.version
            fileSize.text = String.format("%.2f MB", plugin.fileSize)

            val iconFile = File(plugin.getDataFolder().resolve("assets"), "icon.png")
            if (iconFile.exists() && iconFile.isFile) {
                icon.load(iconFile) {
                    scale(Scale.FIT)
                }
            }

            buttonConfig.setOnClickListener {
                // ...플러그인 설정
                val intent = Intent(it.context, PluginConfigActivity::class.java).apply {
                    putExtra("pluginName", config.name)
                    putExtra("pluginId", config.id)
                }
                it.context.startActivity(intent)
            }

            buttonRemove.setOnClickListener { view ->
                MaterialDialog(context, BottomSheet(LayoutMode.WRAP_CONTENT)).show {
                    cornerRadius(25f)
                    title(text = "정말 [${config.name}](을)를 삭제할까요?")
                    message(text = "주의: 삭제시 되돌릴 수 없습니다.")
                    positiveButton(text = "확인") {
                        Session.pluginLoader.removePlugin(config.id)
                        Snackbar.make(view, "플러그인을 삭제했습니다.\n앱을 재시작할까요?", Snackbar.LENGTH_LONG)
                            .setAction("확인") {
                                Utils.restartApplication(context)
                            }
                            .show()
                        it.dismiss()
                    }
                    negativeButton(text = "취소") {
                        it.dismiss()
                    }
                }
            }
        }
    }

    inner class PluginListViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        val context: Context = itemView.context

        val icon: ImageView = itemView.findViewById(R.id.pluginIconImageView)
        val name: TextView = itemView.findViewById(R.id.pluginNameText)
        val version: TextView = itemView.findViewById(R.id.pluginVersionText)
        val fileSize: TextView = itemView.findViewById(R.id.pluginSizeText)
        val buttonConfig: Button = itemView.findViewById(R.id.pluginConfigButton)
        val buttonRemove: Button = itemView.findViewById(R.id.pluginRemoveButton)
    }
}