package dev.mooner.starlight.ui.plugins

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
import coil.transform.RoundedCornersTransformation
import dev.mooner.starlight.R
import dev.mooner.starlight.plugincore.plugin.StarlightPlugin
import dev.mooner.starlight.ui.plugins.config.PluginConfigActivity
import dev.mooner.starlight.ui.plugins.info.startPluginInfoActivity
import dev.mooner.starlight.ui.presets.ExpandableCard
import java.io.File
import kotlin.properties.Delegates.notNull

class PluginsListAdapter(
    private val context: Context,
    var data: List<StarlightPlugin> = listOf()
): RecyclerView.Adapter<PluginsListAdapter.PluginListViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PluginListViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.card_plugins, parent, false)
        return PluginListViewHolder(view)
    }

    override fun getItemCount(): Int = data.size

    override fun getItemViewType(position: Int): Int = 0

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: PluginListViewHolder, position: Int) {
        val plugin = data[position]
        val info = plugin.info

        holder.card.apply {
            title = info.name

            val iconFile = File(plugin.getInternalDataDirectory().resolve("assets"), "icon.png")
            if (iconFile.exists() && iconFile.isFile) {
                setIcon {
                    it.load(iconFile) {
                        scale(Scale.FIT)
                        transformations(RoundedCornersTransformation(context.resources.getDimension(R.dimen.lang_icon_corner_radius)))
                    }
                }
            }

            setOnInnerViewInflateListener {

                with(holder) {
                    inflateInnerView(this@apply)

                    buttonInfo.isEnabled = true
                    buttonConfig.isEnabled = true
                    version.text = "v${info.version}"

                    buttonConfig.setOnClickListener {
                        val intent = Intent(it.context, PluginConfigActivity::class.java).apply {
                            putExtra("pluginName", info.name)
                            putExtra("pluginId", info.id)
                        }
                        it.context.startActivity(intent)
                    }

                    buttonInfo.setOnClickListener {
                        context.startPluginInfoActivity(plugin)
                    }
                }
            }
        }
    }

    inner class PluginListViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        val card: ExpandableCard = itemView.findViewById(R.id.card_plugin)

        var version: TextView by notNull()
        var buttonConfig: Button by notNull()
        var buttonInfo: Button by notNull()

        fun inflateInnerView(innerView: View) {
            version      = innerView.findViewById(R.id.textViewPluginVersion)
            buttonConfig = innerView.findViewById(R.id.buttonConfig)
            buttonInfo   = innerView.findViewById(R.id.buttonInfo)
        }
    }
}