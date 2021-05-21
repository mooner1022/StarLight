package com.mooner.starlight.ui.plugins

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.mooner.starlight.R
import com.mooner.starlight.plugincore.plugin.Plugin
import com.mooner.starlight.plugincore.plugin.StarlightPlugin

class PluginsListAdapter(
    private val context: Context
): RecyclerView.Adapter<PluginsListAdapter.PluginListViewHolder>() {
    var data = listOf<Plugin>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PluginListViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.card_plugins, parent, false)
        return PluginListViewHolder(view)
    }

    override fun getItemCount(): Int = data.size

    override fun onBindViewHolder(holder: PluginListViewHolder, position: Int) {
        val plugin = data[position] as StarlightPlugin
        val config = plugin.config

        with(holder) {
            name.text = config.fullName
            fileSize.text = String.format("%.2f MB", plugin.fileSize)

            buttonConfig.setOnClickListener {
                // ...플러그인 설정
            }

            buttonRemove.setOnClickListener {
                MaterialDialog(context, BottomSheet(LayoutMode.WRAP_CONTENT)).show {
                    cornerRadius(25f)
                    title(text = "정말 [${config.name}](을)를 삭제할까요?")
                    message(text = "주의: 삭제시 되돌릴 수 없습니다.")
                    positiveButton(text = "확인") {
                        // ...삭제 코드
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
        val fileSize: TextView = itemView.findViewById(R.id.pluginSizeText)
        val buttonConfig: Button = itemView.findViewById(R.id.pluginConfigButton)
        val buttonRemove: Button = itemView.findViewById(R.id.pluginRemoveButton)
    }
}