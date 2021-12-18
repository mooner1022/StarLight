package com.mooner.starlight.ui.plugins

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.size.Scale
import com.mooner.starlight.R
import com.mooner.starlight.plugincore.plugin.Plugin
import com.mooner.starlight.plugincore.plugin.StarlightPlugin
import com.mooner.starlight.ui.plugins.config.PluginConfigActivity
import com.mooner.starlight.ui.plugins.info.startPluginInfoActivity
import com.mooner.starlight.ui.presets.ExpandableCardView
import com.mooner.starlight.utils.trimLength
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

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: PluginListViewHolder, position: Int) {
        val plugin = data[position] as StarlightPlugin
        val config = plugin.info

        with(holder) {
            card.setTitle(titleText = config.name.trimLength(17))
            version.text = "v${config.version}"
            fileSize.text = String.format("%.2f MB", plugin.fileSize)

            val iconFile = File(plugin.getDataFolder().resolve("assets"), "icon.png")
            if (iconFile.exists() && iconFile.isFile) {
                card.setIcon {
                    it.load(iconFile) {
                        scale(Scale.FIT)
                    }
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

            buttonInfo.setOnClickListener {
                /*
                val intent = Intent(it.context, PluginInfoActivity::class.java).apply {
                    putExtra("pluginName", config.name)
                    putExtra("pluginId", config.id)
                }
                it.context.startActivity(intent)
                */
                context.startPluginInfoActivity(plugin)
            }

            /*
            buttonRemove.setOnClickListener { view ->
                MaterialDialog(context, BottomSheet(LayoutMode.WRAP_CONTENT)).show {
                    cornerRadius(25f)
                    title(text = "정말 [${config.name}](을)를 삭제할까요?")
                    message(text = "주의: 삭제시 되돌릴 수 없습니다.")
                    positiveButton(text = "확인") {
                        pluginManager.removePlugin(config.id)
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
             */
        }
    }

    inner class PluginListViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        val card: ExpandableCardView  = itemView.findViewById(R.id.card_plugin)
        val fileSize: TextView        = itemView.findViewById(R.id.pluginSizeText)
        val version: TextView         = itemView.findViewById(R.id.textViewVersion)
        val buttonConfig: Button      = itemView.findViewById(R.id.buttonConfig)
        val buttonInfo: Button        = itemView.findViewById(R.id.buttonInfo)
    }
}