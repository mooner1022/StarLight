package com.mooner.starlight.ui.projects

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.size.Scale
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.google.android.material.snackbar.Snackbar
import com.mooner.starlight.R
import com.mooner.starlight.languages.JSRhino
import com.mooner.starlight.plugincore.language.Language
import com.mooner.starlight.plugincore.project.Project
import com.mooner.starlight.ui.debugroom.DebugRoomActivity
import com.mooner.starlight.ui.editor.EditorActivity
import com.mooner.starlight.ui.presets.ExpandableCardView
import com.mooner.starlight.ui.projects.config.ProjectConfigActivity
import kotlinx.coroutines.*
import java.io.File

class ProjectListAdapter(
    private val context: Context
) : RecyclerView.Adapter<ProjectListAdapter.ProjectListViewHolder>() {
    var data = listOf<Project>()
    private val mainContext = Dispatchers.Main
    private val compileContext = Dispatchers.Default

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
        val config = project.info

        fun updateCardColor() {
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
        }

        updateCardColor()
        holder.expandable.apply {
            setSwitch(config.isEnabled)
            setSwitchEnabled(project.isCompiled)
        }

        holder.expandable.setOnSwitchChangeListener { _, isChecked ->
            if (config.isEnabled != isChecked) {
                config.isEnabled = isChecked
                project.saveConfig()
            }
            updateCardColor()
            holder.expandable.apply {
                setSwitch(config.isEnabled)
                setSwitchEnabled(project.isCompiled)
            }
        }

        holder.expandable.setIcon {
            when(project.getLanguage().id) {
                "JS_RHINO" -> it.load(R.drawable.ic_js)
                "JS_V8" -> it.load(R.drawable.ic_v8)
                else -> it.load((project.getLanguage() as Language).getIconFile()) {
                    scale(Scale.FIT)
                }
            }
        }

        holder.expandable.setTitle(titleText = config.name)

        holder.buttonDebugRoom.setOnClickListener {
            it.context.startActivity(
                Intent(
                    it.context,
                    DebugRoomActivity::class.java
                ).apply {
                    putExtra("imageHash", 0)
                    putExtra("roomName", config.name)
                    putExtra("sender", "debug_sender")
                }
            )
        }

        holder.buttonProjectConfig.setOnClickListener {
            it.context.startActivity(
                Intent(
                    it.context,
                    ProjectConfigActivity::class.java
                ).apply {
                    putExtra("projectName", config.name)
                }
            )
        }

        holder.buttonEditCode.setOnClickListener {
            val intent = Intent(
                it.context,
                EditorActivity::class.java
            ).apply {
                putExtra("fileDir", File(project.directory, config.mainScript).path)
                putExtra("title", config.name)
            }
            it.context.startActivity(intent)
        }

        holder.buttonRecompile.setOnClickListener { view ->
            CoroutineScope(compileContext).launch {
                var isSuccess = false
                try {
                    project.compile(true)
                    isSuccess = true
                } catch (e: Exception) {
                    withContext(context = mainContext) {
                        Snackbar.make(view, "${config.name}의 컴파일에 실패했어요.\n$e", Snackbar.LENGTH_LONG).apply {
                            setAction("자세히 보기") {
                                MaterialDialog(view.context, BottomSheet(LayoutMode.WRAP_CONTENT)).show {
                                    cornerRadius(25f)
                                    cancelOnTouchOutside(false)
                                    noAutoDismiss()
                                    title(text = project.info.name + " 에러 로그")
                                    message(text = e.toString() + "\n" + e.stackTraceToString())
                                    positiveButton(text = "닫기") {
                                        dismiss()
                                    }
                                }
                            }
                        }.show()
                    }
                }
                if (isSuccess) {
                    withContext(context = mainContext) {
                        Snackbar.make(view, "${config.name} 컴파일 완료!", Snackbar.LENGTH_SHORT).show()
                        updateCardColor()
                        if (holder.expandable.isEnabled) {
                            holder.expandable.setSwitchEnabled(true)
                        }
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
    }
}