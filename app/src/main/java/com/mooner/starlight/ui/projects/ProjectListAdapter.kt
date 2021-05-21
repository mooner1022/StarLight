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
import com.mooner.starlight.MainActivity
import com.mooner.starlight.R
import com.mooner.starlight.plugincore.Session
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
): RecyclerView.Adapter<ProjectListAdapter.ProjectListViewHolder>() {
    var data = listOf<Project>()
    private val mainScope = CoroutineScope(Dispatchers.Main)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProjectListViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.card_project_list, parent, false)
        return ProjectListViewHolder(view)
    }

    override fun getItemCount(): Int = data.size

    override fun onBindViewHolder(holder: ProjectListViewHolder, position: Int) {
        val project = data[position]
        val config = project.config

        with(holder) {
            cardViewIsEnabled.setCardBackgroundColor(
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

            with(expandable) {
                println("isEnabled: ${project.config.isEnabled}")
                setSwitch(project.config.isEnabled)
                setIcon(
                    drawable = project.getLanguage().icon ?: ContextCompat.getDrawable(
                        context,
                        R.drawable.ic_question_mark
                    )
                )
                setTitle(titleText = config.name)
                setOnSwitchChangeListener { v, isChecked ->
                    if (project.isCompiled) {
                        cardViewIsEnabled.setCardBackgroundColor(
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
                    } else {
                        if (config.isEnabled) {
                            this.setSwitch(true)
                            return@setOnSwitchChangeListener
                        }
                        this.setSwitch(false)
                        MainActivity.showSnackbar("먼저 컴파일이 완료되어야 해요.")
                    }
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
                        project.compile(true)
                    } catch (e: Exception) {
                        mainScope.launch {
                            MainActivity.showSnackbar("${config.name}의 컴파일에 실패했어요.\n$e")
                        }
                    }
                    mainScope.launch {
                        MainActivity.showSnackbar("${config.name}의 컴파일을 완료했어요!")
                        cardViewIsEnabled.setCardBackgroundColor(
                            itemView.context.getColor(
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

    inner class ProjectListViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        val context: Context = itemView.context
        val expandable: ExpandableCardView = itemView.findViewById(R.id.card_project)
        val cardViewIsEnabled: CardView = itemView.findViewById(R.id.cardViewIsEnabled)
        val buttonDebugRoom: Button = itemView.findViewById(R.id.buttonDebugRoom)
        val buttonEditCode: Button = itemView.findViewById(R.id.buttonEditCode)
        val buttonRecompile: Button = itemView.findViewById(R.id.buttonRecompile)
        val buttonProjectConfig: Button = itemView.findViewById(R.id.buttonProjectConfig)
    }
}