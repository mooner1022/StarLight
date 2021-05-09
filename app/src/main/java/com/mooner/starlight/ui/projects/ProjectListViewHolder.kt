package com.mooner.starlight.ui.projects

import android.content.Intent
import android.graphics.Typeface
import android.view.View
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.mooner.starlight.MainActivity
import com.mooner.starlight.R
import com.mooner.starlight.plugincore.Session.Companion.getProjectLoader
import com.mooner.starlight.plugincore.project.Project
import com.mooner.starlight.ui.debugroom.DebugRoomActivity
import com.mooner.starlight.ui.editor.EditorActivity
import com.mooner.starlight.ui.projects.config.ProjectConfigActivity
import host.stjin.expandablecardview.ExpandableCardView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class ProjectListViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
    private val expandable = itemView.findViewById<ExpandableCardView>(R.id.card_project)

    fun bind(project: Project) {
        val config = project.config

        val cardViewIsEnabled: CardView = itemView.findViewById(R.id.cardViewIsEnabled)
        val buttonDebugRoom: Button = itemView.findViewById(R.id.buttonDebugRoom)
        val buttonEditCode: Button = itemView.findViewById(R.id.buttonEditCode)
        val buttonRecompile: Button = itemView.findViewById(R.id.buttonRecompile)
        val buttonProjectConfig: Button = itemView.findViewById(R.id.buttonProjectConfig)
        val cardTitle: TextView = itemView.findViewById(R.id.card_title)

        cardTitle.typeface = Typeface.createFromAsset(
            itemView.context.assets,
            "fonts/nanumsquare_round_regular.ttf"
        )
        cardViewIsEnabled.setBackgroundColor(itemView.context.getColor(if (config.isEnabled) R.color.card_enabled else R.color.card_disabled))

        with(expandable) {
            setIcon(
                drawable = project.getLanguage().icon ?: ContextCompat.getDrawable(
                    itemView.context,
                    R.drawable.ic_js
                )
            )
            setTitle(titleText = config.name)
            setSwitch(config.isEnabled)
            setOnSwitchChangeListener { v, isChecked ->
                getProjectLoader().updateProjectConfig(config.name) {
                    isEnabled = isChecked
                }
                cardViewIsEnabled.setBackgroundColor(v!!.context.getColor(if (isChecked) R.color.card_enabled else R.color.card_disabled))
                MainActivity.reloadText()
            }
        }

        buttonEditCode.setOnClickListener {
            val intent = Intent(it.context, EditorActivity::class.java).apply {
                putExtra("fileDir", File(project.folder, config.mainScript).path)
                putExtra("title", config.name)
            }
            it.context.startActivity(intent)
        }

        buttonRecompile.setOnClickListener {
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    getProjectLoader().getProject(config.name)?.compile(true)
                } catch (e: Exception) {
                    CoroutineScope(Dispatchers.Main).launch {
                        MainActivity.showSnackbar("${config.name}의 컴파일에 실패했어요.\n$e")
                    }
                }
                CoroutineScope(Dispatchers.Main).launch {
                    MainActivity.showSnackbar("${config.name}의 컴파일을 완료했어요!")
                }
            }
        }

        buttonDebugRoom.setOnClickListener {
            it.context.startActivity(Intent(it.context, DebugRoomActivity::class.java).apply {
                putExtra(
                    "roomName",
                    config.name
                )
            })
        }

        buttonProjectConfig.setOnClickListener {
            val intent = Intent(it.context, ProjectConfigActivity::class.java).apply {
                putExtra("projectName", config.name)
            }
            it.context.startActivity(intent)
        }
    }
}