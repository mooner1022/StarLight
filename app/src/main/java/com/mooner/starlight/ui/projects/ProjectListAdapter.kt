package com.mooner.starlight.ui.projects

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.mooner.starlight.MainActivity
import com.mooner.starlight.R
import com.mooner.starlight.plugincore.project.Project
import com.mooner.starlight.ui.debugroom.DebugRoomActivity
import com.mooner.starlight.ui.editor.EditorActivity
import com.mooner.starlight.ui.presets.ExpandableCardView
import com.mooner.starlight.ui.projects.config.ProjectConfigActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class ProjectListAdapter(
    private val context: Context
) : RecyclerView.Adapter<ProjectListAdapter.ProjectListViewHolder>() {
    var data = listOf<Project>()
    private val mainScope = CoroutineScope(Dispatchers.Main)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProjectListViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.card_project_list, parent, false)
        return ProjectListViewHolder(view)
    }

    override fun getItemCount(): Int = data.size

    override fun getItemViewType(position: Int): Int {
        return position
    }

    override fun onBindViewHolder(holder: ProjectListViewHolder, position: Int) {
        val project = data[position]
        val config = project.config

        holder.cardViewIsEnabled.setCardBackgroundColor(
            context.getColor(
                if (project.isCompiled) {
                    if (config.isEnabled) {
                        R.color.card_enabled
                    } else {
                        R.color.card_disabled
                    }
                } else {
                    R.color.orange
                }
            )
        )

        with(holder.expandable) {
            if (project.isCompiled) {
                setOnSwitchChangeListener { _, isChecked ->
                    if (isChecked != config.isEnabled) {
                        holder.cardViewIsEnabled.setCardBackgroundColor(
                            context.getColor(
                                if (isChecked) {
                                    R.color.card_enabled
                                } else {
                                    R.color.card_disabled
                                }
                            )
                        )
                        config.isEnabled = isChecked
                        project.flush()
                        MainActivity.reloadText()
                    }
                }
                setSwitch(project.config.isEnabled)
                setSwitchEnabled(true)
            } else {
                setSwitch(false)
                setSwitchEnabled(false)
            }

            setIcon(
                drawable = project.getLanguage().icon
                    ?: ContextCompat.getDrawable(
                        context,
                        R.drawable.ic_round_error_outline_24
                    )
            )
            setTitle(titleText = config.name)
            setOnExpandedListener { _, isExpanded ->
                if (isExpanded) {
                    holder.buttonEditCode.setOnClickListener {
                        val intent = Intent(
                            it.context,
                            EditorActivity::class.java
                        ).apply {
                            putExtra("fileDir", File(project.folder, config.mainScript).path)
                            putExtra("title", config.name)
                        }
                        it.context.startActivity(intent)
                    }

                    holder.buttonRecompile.setOnClickListener {
                        CoroutineScope(Dispatchers.Default).launch {
                            try {
                                project.compile(true)
                            } catch (e: Exception) {
                                mainScope.launch {
                                    MainActivity.showSnackbar("${config.name}의 컴파일에 실패했어요.\n$e")
                                }
                            }
                            mainScope.launch {
                                MainActivity.showSnackbar("${config.name}의 컴파일을 완료했어요!")
                                holder.cardViewIsEnabled.setCardBackgroundColor(
                                    holder.itemView.context.getColor(
                                        R.color.card_disabled
                                    )
                                )
                                with(holder.expandable) {
                                    setSwitchEnabled(true)
                                    setOnSwitchChangeListener { _, isChecked ->
                                        if (isChecked != config.isEnabled) {
                                            holder.cardViewIsEnabled.setCardBackgroundColor(
                                                context.getColor(
                                                    if (isChecked) {
                                                        R.color.card_enabled
                                                    } else {
                                                        R.color.card_disabled
                                                    }
                                                )
                                            )
                                            project.config.isEnabled = true
                                            project.flush()
                                            //Session.getProjectLoader().updateProjectConfig(config.name, false) {
                                            //    isEnabled = isChecked
                                            //}
                                            println("config: ${project.config}")
                                            MainActivity.reloadText()
                                        }
                                    }
                                }
                            }
                        }
                    }

                    holder.buttonDebugRoom.setOnClickListener {
                        it.context.startActivity(
                            Intent(
                                it.context,
                                DebugRoomActivity::class.java
                            ).apply {
                                putExtra(
                                    "roomName",
                                    config.name
                                )
                            })
                    }

                    holder.buttonProjectConfig.setOnClickListener {
                        val intent = Intent(it.context, ProjectConfigActivity::class.java).apply {
                            putExtra("projectName", config.name)
                        }
                        it.context.startActivity(intent)
                    }
                }
            }
        }
    }

    inner class ProjectListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val context: Context = itemView.context
        val expandable: ExpandableCardView = itemView.findViewById(R.id.card_project)
        val cardViewIsEnabled: CardView = itemView.findViewById(R.id.cardViewIsEnabled)
        val buttonDebugRoom: Button = itemView.findViewById(R.id.buttonDebugRoom)
        val buttonEditCode: Button = itemView.findViewById(R.id.buttonEditCode)
        val buttonRecompile: Button = itemView.findViewById(R.id.buttonRecompile)
        val buttonProjectConfig: Button = itemView.findViewById(R.id.buttonProjectConfig)
        val switch: SwitchMaterial = itemView.findViewById(R.id.card_switch)
    }
}