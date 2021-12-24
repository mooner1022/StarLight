package com.mooner.starlight.ui.projects

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.google.android.material.snackbar.Snackbar
import com.mooner.starlight.R
import com.mooner.starlight.plugincore.project.Project
import com.mooner.starlight.ui.debugroom.DebugRoomActivity
import com.mooner.starlight.ui.editor.EditorActivity
import com.mooner.starlight.ui.presets.ExpandableCardView
import com.mooner.starlight.ui.projects.config.ProjectConfigActivity
import com.mooner.starlight.ui.projects.info.startProjectInfoActivity
import com.mooner.starlight.utils.ViewUtils.Companion.graceProgress
import com.mooner.starlight.utils.ViewUtils.Companion.loadAnyWithTint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ProjectListAdapter(
    private val context: Context
) : RecyclerView.Adapter<ProjectListAdapter.ProjectListViewHolder>() {
    var data = listOf<Project>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProjectListViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.card_projects, parent, false)
        return ProjectListViewHolder(view)
    }

    override fun getItemCount(): Int = data.size

    override fun getItemViewType(position: Int): Int = 0

    override fun onBindViewHolder(holder: ProjectListViewHolder, position: Int) {
        val project = data[position]
        val info = project.info

        fun getCardColor(): Int = context.getColor(
            if (project.isCompiled) {
                if (info.isEnabled) {
                    R.color.card_enabled
                } else {
                    R.color.card_disabled
                }
            } else {
                R.color.orange
            }
        )

        holder.cardViewIsEnabled.setCardBackgroundColor(getCardColor())

        holder.expandable.setOnSwitchChangeListener { _, isChecked ->
            if (info.isEnabled != isChecked) {
                info.isEnabled = isChecked
                project.saveInfo()
            }
            holder.cardViewIsEnabled.setCardBackgroundColor(getCardColor())
            holder.expandable.apply {
                setSwitch(info.isEnabled)
                setSwitchEnabled(project.isCompiled)
            }
        }

        holder.expandable.apply {
            setSwitch(info.isEnabled)
            setSwitchEnabled(project.isCompiled)
        }

        holder.expandable.setIcon {
            val icon: Any? = when(project.getLanguage().id) {
                "JS_RHINO" -> R.drawable.ic_js
                "JS_V8" -> R.drawable.ic_v8
                else -> project.getLanguage().getIconFileOrNull()
            }
            val tint = if (icon == null) R.color.main_purple else null
            it.loadAnyWithTint(
                data = icon?: R.drawable.ic_round_developer_mode_24,
                tintColor = tint
            )
        }

        holder.expandable.setTitle(titleText = info.name)

        holder.buttonDebugRoom.setOnClickListener {
            if (!project.isCompiled) {
                Snackbar.make(it, "아직 프로젝트가 컴파일되지 않았어요.", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            it.context.startActivity(
                Intent(
                    it.context,
                    DebugRoomActivity::class.java
                ).apply {
                    putExtra("projectName", info.name)
                }
            )
        }

        holder.buttonProjectConfig.setOnClickListener {
            it.context.startActivity(
                Intent(
                    it.context,
                    ProjectConfigActivity::class.java
                ).apply {
                    putExtra("projectName", info.name)
                }
            )
        }

        holder.buttonEditCode.setOnClickListener {
            val intent = Intent(
                it.context,
                EditorActivity::class.java
            ).apply {
                putExtra("fileDir", File(project.directory, info.mainScript).path)
                putExtra("title", info.name)
            }
            it.context.startActivity(intent)
        }

        fun showProgress(show: Boolean) {
            holder.buttonWrapper.visibility = if (show) View.GONE else View.VISIBLE
            holder.progressWrapper.visibility = if (!show) View.GONE else View.VISIBLE
        }

        holder.buttonRecompile.setOnClickListener { view ->
            showProgress(true)
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    project.compile(true)
                    withContext(Dispatchers.Main) {
                        Snackbar.make(view, "${info.name} 컴파일 완료!", Snackbar.LENGTH_SHORT).show()
                        holder.cardViewIsEnabled.setCardBackgroundColor(getCardColor())
                        holder.expandable.setSwitchEnabled(true)
                        holder.progressBar.graceProgress = 100
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Snackbar.make(view, "${info.name}의 컴파일에 실패했어요.\n$e", Snackbar.LENGTH_LONG).apply {
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
                        holder.cardViewIsEnabled.setCardBackgroundColor(getCardColor())
                        holder.expandable.setSwitchEnabled(false)
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        showProgress(false)
                    }
                }
            }
        }

        holder.buttonProjectInfo.setOnClickListener {
            /*
            val intent = Intent(
                it.context,
                ProjectInfoActivity::class.java
            ).apply {
                putExtra("projectName", info.name)
            }
            it.context.startActivity(intent)
            */
            context.startProjectInfoActivity(project)
        }
    }

    inner class ProjectListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val buttonWrapper: LinearLayout      = itemView.findViewById(R.id.buttonWrapper)
        val progressWrapper: LinearLayout    = itemView.findViewById(R.id.progressWrapper)

        val progressBar: ProgressBar         = itemView.findViewById(R.id.progressBar)
        val progressState: TextView          = itemView.findViewById(R.id.progressState)

        val expandable: ExpandableCardView   = itemView.findViewById(R.id.card_project)
        val cardViewIsEnabled: CardView      = itemView.findViewById(R.id.cardViewIsEnabled)
        val buttonDebugRoom: ImageButton     = itemView.findViewById(R.id.buttonDebugRoom)
        val buttonEditCode: ImageButton      = itemView.findViewById(R.id.buttonEditCode)
        val buttonRecompile: ImageButton     = itemView.findViewById(R.id.buttonRecompile)
        val buttonProjectConfig: ImageButton = itemView.findViewById(R.id.buttonProjectConfig)
        val buttonProjectInfo: ImageButton   = itemView.findViewById(R.id.buttonProjectInfo)
    }
}