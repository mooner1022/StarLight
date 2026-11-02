package dev.mooner.starlight.ui.agent

import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.mooner.starlight.R
import dev.mooner.starlight.agent.AgentHub
import dev.mooner.starlight.agent.ApprovalPolicy
import dev.mooner.starlight.databinding.ItemAgentOptionBinding
import dev.mooner.starlight.databinding.SheetAgentAddBinding
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.ui.common.Sheet
import dev.mooner.starlight.ui.common.SlideBottomSheetDialog
import dev.mooner.starlight.ui.common.showSheet

/**
 * "Add to chat", opened from the composer's plus button: photos and files on top, then what the conversation is
 * about (its project), when the agent asks first, and the model.
 */
internal object AddSheet {

    fun show(activity: AppCompatActivity, composer: Composer, onPhotos: () -> Unit, onFiles: () -> Unit) {
        val hub = AgentHub.get(activity)
        val binding = SheetAgentAddBinding.inflate(LayoutInflater.from(activity))
        val dialog = SlideBottomSheetDialog(activity)
        dialog.setContentView(binding.root)
        // Row ripples stay inside the group's rounded corners.
        binding.group.clipToOutline = true

        fun render() {
            binding.projectValue.text = composer.project ?: activity.getString(R.string.agent_add_none)
            binding.approvalValue.text = composer.approval?.let { approvalLabel(activity, it) }
                ?: activity.getString(R.string.agent_add_approval_default)
            binding.modelValue.text = hub.settings.activeProvider()
                ?.let { kind -> hub.settings.model(kind)?.let { AgentUi.modelName(hub, kind, it) } ?: kind.displayName }
                ?: activity.getString(R.string.agent_subtitle_setup)
        }

        binding.close.setOnClickListener { dialog.dismiss() }
        binding.photos.setOnClickListener {
            dialog.dismiss()
            onPhotos()
        }
        binding.files.setOnClickListener {
            dialog.dismiss()
            onFiles()
        }
        binding.projectRow.setOnClickListener { pickProject(activity, composer, ::render) }
        binding.approvalRow.setOnClickListener { pickApproval(activity, composer, ::render) }
        binding.modelRow.setOnClickListener { ModelSheet.show(activity, onDismiss = ::render) }
        render()
        // Closed with the screen, so it doesn't outlive it on rotation.
        val closer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY)
                dialog.dismiss()
        }
        activity.lifecycle.addObserver(closer)
        dialog.setOnDismissListener { activity.lifecycle.removeObserver(closer) }
        dialog.show()
    }

    /** Lets the user point the conversation at one of their projects, or at none. */
    fun pickProject(activity: AppCompatActivity, composer: Composer, onPicked: () -> Unit = {}) {
        val projects = Session.projectManager.getProjects().sortedBy { it.info.name.lowercase() }
        lateinit var sheet: Sheet
        sheet = activity.showSheet {
            title = activity.getString(R.string.agent_add_project_title)
            icon = R.drawable.ic_round_projects_24
            content { inflater, parent ->
                fun option(title: String, description: String, selected: Boolean, value: String?) {
                    val row = ItemAgentOptionBinding.inflate(inflater, parent, true)
                    row.title.text = title
                    row.description.text = description
                    row.check.isVisible = selected
                    row.root.setOnClickListener {
                        composer.project = value
                        onPicked()
                        sheet.dismiss()
                    }
                }
                option(activity.getString(R.string.agent_add_none), activity.getString(R.string.agent_add_project_none_desc),
                    composer.project == null, null)
                for (project in projects) {
                    val status = activity.getString(when {
                        !project.isCompiled    -> R.string.project_status_not_compiled
                        project.info.isEnabled -> R.string.project_status_running
                        else                   -> R.string.project_status_off
                    })
                    val language = runCatching { project.getLanguage().name }.getOrNull()
                    option(project.info.name, listOfNotNull(language, status).joinToString(" · "),
                        project.info.name == composer.project, project.info.name)
                }
            }
        }
    }

    private fun pickApproval(activity: AppCompatActivity, composer: Composer, onPicked: () -> Unit) {
        val hub = AgentHub.get(activity)
        val options = listOf(
            Triple(null, activity.getString(R.string.agent_add_approval_default),
                activity.getString(R.string.agent_add_approval_default_desc, approvalLabel(activity, hub.settings.approvalPolicy))),
            Triple(ApprovalPolicy.AUTO, activity.getString(R.string.agent_approval_auto), activity.getString(R.string.agent_approval_auto_desc)),
            Triple(ApprovalPolicy.ASK_DESTRUCTIVE, activity.getString(R.string.agent_approval_destructive),
                activity.getString(R.string.agent_approval_destructive_desc)),
            Triple(ApprovalPolicy.ASK_WRITES, activity.getString(R.string.agent_approval_writes), activity.getString(R.string.agent_approval_writes_desc)),
        )
        lateinit var sheet: Sheet
        sheet = activity.showSheet {
            title = activity.getString(R.string.agent_add_approval_title)
            icon = R.drawable.ic_agent_shield
            content { inflater, parent ->
                for ((policy, label, description) in options) {
                    val row = ItemAgentOptionBinding.inflate(inflater, parent, true)
                    row.title.text = label
                    row.description.text = description
                    row.check.isVisible = policy == composer.approval
                    row.root.setOnClickListener {
                        composer.approval = policy
                        onPicked()
                        sheet.dismiss()
                    }
                }
            }
        }
    }

    fun approvalLabel(activity: android.content.Context, policy: ApprovalPolicy): String = activity.getString(when (policy) {
        ApprovalPolicy.AUTO            -> R.string.agent_approval_auto
        ApprovalPolicy.ASK_DESTRUCTIVE -> R.string.agent_approval_destructive
        ApprovalPolicy.ASK_WRITES      -> R.string.agent_approval_writes
    })
}
