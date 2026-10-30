/*
 * ProjectListItem.kt created by Minki Moon(mooner1022) on 1/19/23, 1:54 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.projects

import android.content.Context
import android.content.res.ColorStateList
import android.os.Build
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import coil.load
import com.mikepenz.fastadapter.binding.AbstractBindingItem
import dev.mooner.configdsl.Icon
import dev.mooner.configdsl.utils.PrimitiveTypedString
import dev.mooner.peekalert.PeekAlert
import dev.mooner.starlight.ID_VIEW_ITEM_PROJECT
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.ItemProjectActionsBinding
import dev.mooner.starlight.databinding.ItemProjectBinding
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.editor.CodeEditorActivity
import dev.mooner.starlight.plugincore.logger.LogData
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.ui.debugroom.DebugRoomActivity
import dev.mooner.starlight.ui.editor.DefaultEditorActivity
import dev.mooner.starlight.ui.projects.config.ProjectButtonData
import dev.mooner.starlight.ui.projects.config.ProjectConfigActivity
import dev.mooner.starlight.utils.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.decodeFromJsonElement
import java.lang.ref.WeakReference
import dev.mooner.starlight.ui.projects.info.startProjectInfoActivity as mStartProjectInfoActivity

private val LOG = LoggerFactory.logger {  }

class ProjectListItem(
    private val parent: WeakReference<Fragment>
): AbstractBindingItem<ItemProjectBinding>() {

    override var identifier: Long
        get() = project!!.info.id.getLongHash()
        set(_) {}
    var project: Project? = null

    private var binding: ItemProjectBinding? = null
    private var isExpanded: Boolean = false

    private var isCompiling: Boolean = false
    private var compileStage: String? = null
    private var compileProgress: Int = 0

    override val type: Int
        get() = ID_VIEW_ITEM_PROJECT

    fun withProject(project: Project): ProjectListItem {
        this.project = project
        return this
    }

    override fun bindView(binding: ItemProjectBinding, payloads: List<Any>) {
        this.binding = binding
        binding.root.tag = this
        val project = project ?: return

        binding.icon.loadLanguageIcon(project)
        binding.header.setOnClickListener {
            isExpanded = !isExpanded
            binding.setExpanded(project, isExpanded, animate = true)
        }
        binding.updateState(project)
        binding.setExpanded(project, isExpanded, animate = false)
    }

    override fun unbindView(binding: ItemProjectBinding) {
        if (binding.root.tag === this)
            binding.root.tag = null
        if (this.binding === binding)
            this.binding = null
    }

    fun tryUpdateView() {
        val project = project ?: return
        boundBinding()?.updateState(project)
    }

    private fun boundBinding(): ItemProjectBinding? =
        binding?.takeIf { it.root.tag === this }

    private fun ItemProjectBinding.findActions(inflate: Boolean): ItemProjectActionsBinding? {
        root.findViewById<View>(R.id.actions)?.let { return ItemProjectActionsBinding.bind(it) }
        if (!inflate)
            return null
        return ItemProjectActionsBinding.bind(actionsStub.inflate())
    }

    private fun ItemProjectBinding.setExpanded(project: Project, expanded: Boolean, animate: Boolean) {
        if (animate)
            (root.parent?.parent as? ViewGroup)?.let {
                TransitionManager.beginDelayedTransition(it, AutoTransition().apply {
                    ordering = TransitionSet.ORDERING_TOGETHER
                    duration = ANIM_DURATION
                })
            }
        val actions = findActions(inflate = expanded)
        if (actions != null) {
            actions.root.visibility = if (expanded) View.VISIBLE else View.GONE
            if (expanded)
                actions.bind(project)
        }
        val rotation = if (expanded) 180f else 0f
        if (animate)
            arrow.animate().rotation(rotation).setDuration(ANIM_DURATION).start()
        else {
            arrow.animate().cancel()
            arrow.rotation = rotation
        }
    }

    private fun ItemProjectActionsBinding.bind(project: Project) {
        for (button in arrayOf(buttonEditCode, buttonRecompile, buttonDebugRoom, buttonProjectConfig, buttonProjectInfo, buttonProjectPin))
            button.setOnClickListener(onClickListener)
        updatePinButton(project)

        customButtons.removeAllViews()
        val buttons = project.getCustomButtons()
        for ((id, icon) in buttons)
            customButtons.addView(project.createCustomButton(root.context, id, icon))
        customButtons.visibility = if (buttons.isEmpty()) View.GONE else View.VISIBLE

        updateProgress()
    }

    private fun ItemProjectActionsBinding.updatePinButton(project: Project) {
        val tintColor = if (project.info.isPinned) R.color.code_yellow else R.color.text
        buttonProjectPin.setDrawableTint(tintColor)
    }

    private fun ItemProjectActionsBinding.updateProgress() {
        actionRow.visibility = if (isCompiling) View.INVISIBLE else View.VISIBLE
        progressWrapper.visibility = if (isCompiling) View.VISIBLE else View.GONE
        if (isCompiling) {
            progressState.text = compileStage
            progressBar.graceProgress = compileProgress
        }
    }

    private val onClickListener = OnClickListener { view ->
        val context = view.context
        val project = project ?: return@OnClickListener

        when(view.id) {
            R.id.buttonEditCode ->
                context.startEditorActivity(project)
            R.id.buttonRecompile ->
                compileProject(view, project)
            R.id.buttonDebugRoom ->
                context.startDebugRoomActivity(view, project)
            R.id.buttonProjectConfig ->
                context.startProjectConfigActivity(project)
            R.id.buttonProjectInfo ->
                context.startProjectInfoActivity(project)
            R.id.buttonProjectPin -> {
                project.info.isPinned = !project.info.isPinned
                project.saveInfo()
                boundBinding()?.let { binding ->
                    binding.updateState(project)
                    binding.findActions(inflate = false)?.updatePinButton(project)
                }
            }
        }
    }

    private fun compileProject(view: View, project: Project) {
        if (isCompiling)
            return
        isCompiling = true
        compileStage = null
        compileProgress = 0
        boundBinding()?.findActions(inflate = false)?.updateProgress()

        val startMillis = System.currentTimeMillis()
        project.compileAsync()
            .buffer(3)
            .onEach { (stage, percent) ->
                withContext(Dispatchers.Main) {
                    compileStage = stage.name
                    compileProgress = percent
                    boundBinding()?.findActions(inflate = false)?.updateProgress()
                }
            }
            .onCompletion { e ->
                withContext(Dispatchers.Main) {
                    compileProgress = 100
                    boundBinding()?.findActions(inflate = false)?.updateProgress()
                }
                if (e == null) {
                    val compileTime = System.currentTimeMillis() - startMillis
                    withContext(Dispatchers.Main) {
                        (parent.get() ?: return@withContext).createSuccessPeek(translate {
                            Locale.ENGLISH { "Successfully compiled ${project.info.name} (${compileTime}ms)" }
                            Locale.KOREAN  { "${project.info.name} 컴파일 완료! (${compileTime}ms)" }
                        }, PeekAlert.Position.Bottom).also {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                                view.rootWindowInsets?.let { insets ->
                                    it.setMargin(vertical = insets.getInsets(WindowInsets.Type.systemBars()).bottom)
                                }
                        }.peek()
                        tryUpdateView()
                    }
                }

                val awaitAnimation = GlobalConfig.category("project").getBoolean("compile_animation", true)
                if (awaitAnimation)
                    delay(PROGRESS_WAIT_TIME)

                withContext(Dispatchers.Main) {
                    isCompiling = false
                    boundBinding()?.findActions(inflate = false)?.updateProgress()
                }
            }
            .catch { e ->
                val title = translate {
                    Locale.ENGLISH { "Failed to compile ${project.info.name}" }
                    Locale.KOREAN  { "${project.info.name} 컴파일 실패" }
                }
                LOG.error(LogData.FLAG_COMPILE_RESULT) { title + "\n" + e.toString() }
                withContext(Dispatchers.Main) {
                    (parent.get() ?: return@withContext).createSimplePeek(
                        title = title,
                        text = e.toString()
                    ) {
                        paddingDp = 12
                        position = PeekAlert.Position.Bottom
                        iconRes = R.drawable.ic_round_error_outline_24
                        backgroundColor(res = R.color.code_error)
                        action("자세히") {
                            textColor(res = R.color.black)
                            setOnActionListener {
                                view.context.showErrorLogDialog(
                                    translate {
                                        Locale.ENGLISH { "Error log of ${project.info.name}\n$e" }
                                        Locale.KOREAN  { "${project.info.name} 에러 로그\n$e" }
                                    }, e
                                )
                            }
                        }
                    }.apply {
                        setTitleColor(res = R.color.white)
                        setTextColor(res = R.color.white)
                    }.peek()
                    tryUpdateView()
                }
            }
            .launchIn(CoroutineScope(Dispatchers.Default))
    }

    private fun Context.startEditorActivity(project: Project) {
        startActivityWithExtra(
            DefaultEditorActivity::class.java,
            mapOf(
                CodeEditorActivity.KEY_BASE_DIRECTORY to project.directory.path,
                CodeEditorActivity.KEY_PROJECT_NAME   to project.info.name,
                CodeEditorActivity.KEY_MAIN_FILE      to project.info.mainScript,
                "title"   to project.info.name
            )
        )
    }

    private fun Context.startDebugRoomActivity(view: View, project: Project) {
        if (!project.isCompiled) {
            view.context.peek(translate {
                Locale.ENGLISH { "Project isn't compiled yet." }
                Locale.KOREAN  { "프로젝트 컴파일이 완료된 후 사용할 수 있어요." }
            })
            return
        }
        startActivityWithExtra(
            DebugRoomActivity::class.java,
            mapOf(DebugRoomActivity.EXTRA_PROJECT_NAME to project.info.name)
        )
    }

    private fun Context.startProjectConfigActivity(project: Project) {
        startActivityWithExtra(
            ProjectConfigActivity::class.java,
            mapOf("projectName" to project.info.name)
        )
    }

    private fun Context.startProjectInfoActivity(project: Project) {
        mStartProjectInfoActivity(project)
    }

    private fun Project.createCustomButton(context: Context, id: String, icon: Icon): ImageButton {
        return ImageButton(context).apply {
            layoutParams = ViewGroup.LayoutParams(dp(48), dp(48))
            TypedValue()
                .also { context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, it, true) }
                .let { setBackgroundResource(it.resourceId) }
            loadWithTint(data = icon.drawableRes, tintColor = R.color.text)
            setOnClickListener {
                callFunction("onProjectButtonClicked", arrayOf(id)) { e ->
                    LOG.warn {
                        translate {
                            Locale.ENGLISH { "Failed to call button event: $e" }
                            Locale.KOREAN  { "버튼 이벤트 실행 실패: $e" }
                        }
                    }
                    e.printStackTrace()
                }
            }
        }
    }

    private fun Project.getCustomButtons(): List<Pair<String, Icon>> {
        return try {
            runCatching {
                config // Support legacy config format
                    .category("beta_features")
                    .getString("custom_buttons")
                    ?.let<_, List<Map<String, PrimitiveTypedString>>>(Session.json::decodeFromString)
                    ?.map { it["button_id"]!!.castAs<String>() to Icon.entries.toTypedArray()[it["button_icon"]!!.castAs()] }
                    ?: listOf()
            }.getOrElse {
                config
                    .category("beta_features")
                    .getList("custom_buttons")
                    ?.map<_, ProjectButtonData>(Session.json::decodeFromJsonElement)
                    ?.map { it.id to (it.icon?.let(Icon.entries::get) ?: Icon.LAYERS) }
                    ?: listOf()
            }
        } catch (e: Exception) {
            LOG.warn {
                translate {
                    Locale.ENGLISH { "Failed to parse custom buttons: $e" }
                    Locale.KOREAN  { "커스텀 버튼 목록을 불러오지 못함: $e" }
                }
            }
            listOf()
        }
    }

    private fun ImageView.loadLanguageIcon(project: Project) {
        val icon: Any? = when(project.getLanguage().id) {
            "JS_RHINO" -> R.drawable.ic_js
            else -> project.getLanguage().getIconFileOrNull()
        }
        load(icon ?: R.drawable.ic_round_developer_mode_24)
    }

    private fun ItemProjectBinding.updateState(project: Project) {
        val context = root.context
        title.text = project.info.name
        pin.visibility = if (project.info.isPinned) View.VISIBLE else View.GONE

        val (statusRes, colorRes) = when {
            !project.isCompiled    -> R.string.project_status_not_compiled to R.color.orange
            project.info.isEnabled -> R.string.project_status_running to R.color.card_enabled
            else                   -> R.string.project_status_off to R.color.text_sub
        }
        subtitle.text = "${project.getLanguage().name} · ${context.getString(statusRes)}"
        status.backgroundTintList = ColorStateList.valueOf(context.getColor(colorRes))

        toggle.setOnCheckedChangeListener(null)
        toggle.isChecked = project.info.isEnabled
        toggle.isEnabled = project.isCompiled
        toggle.setOnCheckedChangeListener { _, isChecked ->
            if (project.info.isEnabled != isChecked)
                project.setEnabled(isChecked)
            updateState(project)
        }
    }

    private fun TextView.setDrawableTint(colorRes: Int) {
        TextViewCompat.setCompoundDrawableTintList(this, ColorStateList.valueOf(context.getColor(colorRes)))
    }

    override fun createBinding(inflater: LayoutInflater, parent: ViewGroup?): ItemProjectBinding =
        ItemProjectBinding.inflate(inflater, parent, false)

    companion object {
        private const val PROGRESS_WAIT_TIME = 400L
        private const val ANIM_DURATION = 200L
    }
}
