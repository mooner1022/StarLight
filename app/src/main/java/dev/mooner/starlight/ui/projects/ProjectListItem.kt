/*
 * ProjectListItem.kt created by Minki Moon(mooner1022) on 1/19/23, 1:54 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.projects

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.core.view.updateLayoutParams
import coil.transform.RoundedCornersTransformation
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.snackbar.Snackbar
import com.mikepenz.fastadapter.binding.AbstractBindingItem
import dev.mooner.starlight.ID_VIEW_ITEM_PROJECT
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.CardProjectButtonsBinding
import dev.mooner.starlight.databinding.CardProjectsBinding
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.config.data.PrimitiveTypedString
import dev.mooner.starlight.plugincore.editor.CodeEditorActivity
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.plugincore.utils.Icon
import dev.mooner.starlight.ui.debugroom.DebugRoomActivity
import dev.mooner.starlight.ui.editor.DefaultEditorActivity
import dev.mooner.starlight.ui.presets.ExpandableCard
import dev.mooner.starlight.ui.projects.config.ProjectConfigActivity
import dev.mooner.starlight.utils.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import java.io.File
import dev.mooner.starlight.ui.projects.info.startProjectInfoActivity as mStartProjectInfoActivity
import dev.mooner.starlight.utils.layoutMode as utils_layoutMode

private val LOG = LoggerFactory.logger {  }

class ProjectListItem: AbstractBindingItem<CardProjectsBinding>() {

    override var identifier: Long
        get() = project?.info?.id?.hashCode()?.toLong() ?: -1L
        set(_) {}
    var project: Project? = null

    private val buttonIds: MutableSet<String> = hashSetOf()
    private var binding: CardProjectsBinding? = null
    private var innerBinding: CardProjectButtonsBinding? = null
    private var isExpanded: Boolean = false

    override val type: Int
        get() = ID_VIEW_ITEM_PROJECT

    fun withProject(project: Project): ProjectListItem {
        this.project = project
        return this
    }

    override fun bindView(binding: CardProjectsBinding, payloads: List<Any>) {
        this.binding = binding
        project?.let { project ->
            val context = binding.root.context

            binding.cardProject.apply {
                if (isExpanded && !expanded)
                    expand()
                else if (!isExpanded && expanded)
                    collapse()

                title = project.info.name
                if (context.utils_layoutMode == LAYOUT_SLIM)
                    setIconVisibility(View.GONE)
                else
                    setIcon(project)
                setOnSwitchChangeListener { _, isChecked ->
                    if (project.info.isEnabled != isChecked) {
                        project.info.isEnabled = isChecked
                        project.saveInfo()
                    }
                    binding.updateState(project)
                }
            }
            binding.updateState(project)

            binding.cardProject.setOnInnerViewInflateListener {
                val innerView = binding.root.findViewById<FlexboxLayout>(R.id.innerView)
                innerBinding = getInnerViewBinding(innerView)

                for ((id, icon) in project.getCustomButtons()) {
                    val button = project.createCustomButton(context, id, icon)
                    buttonIds += id

                    innerBinding!!.flexLayout.addView(button)
                }

                innerBinding!!.apply {
                    buttonEditCode.setOnClickListener(onClickListener)
                    buttonRecompile.setOnClickListener(onClickListener)
                    buttonDebugRoom.setOnClickListener(onClickListener)
                    buttonProjectConfig.setOnClickListener(onClickListener)
                    buttonProjectInfo.setOnClickListener(onClickListener)
                    buttonProjectPin.setOnClickListener(onClickListener)

                    val tintColor = if (project.info.isPinned) R.color.code_yellow else R.color.text
                    buttonProjectPin
                        .loadWithTint(R.drawable.ic_round_star_24, tintColor)
                }
            }
        }
    }

    override fun unbindView(binding: CardProjectsBinding) {
        if (buttonIds.isNotEmpty()) {
            innerBinding?.flexLayout?.apply {
                for (id in buttonIds)
                    removeView(findViewById(id.hashCode()))
            }
            buttonIds.clear()
        }
        isExpanded = binding.cardProject.expanded
    }

    fun tryUpdateView() {
        project?.let { project -> binding?.updateState(project) }
    }

    private val onClickListener = OnClickListener { view ->
        val context = view.context
        if (project == null)
            return@OnClickListener
        val project = project!!

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
                val tintColor = if (project.info.isPinned) R.color.code_yellow else R.color.text

                innerBinding?.buttonProjectPin
                    ?.loadWithTint(R.drawable.ic_round_star_24, tintColor)
                project.saveInfo()
            }
        }
    }

    private fun getInnerViewBinding(innerView: View): CardProjectButtonsBinding =
        CardProjectButtonsBinding.bind(innerView)

    private fun CardProjectButtonsBinding.showProgress(show: Boolean) {
        flexLayout.visibility = if (show) View.GONE else View.VISIBLE
        progressWrapper.visibility = if (!show) View.GONE else View.VISIBLE
    }

    private fun compileProject(view: View, project: Project) {
        if (innerBinding == null) {
            LOG.error { "innerBinding == null" }
            return
        }
        val innerBinding = innerBinding!!

        innerBinding.showProgress(true)
        innerBinding.progressBar.progress = 0

        val startMillis = System.currentTimeMillis()
        project.compileAsync()
            //.flowWithLifecycle((this@ProjectListAdapter.context as MainActivity).lifecycle)
            .buffer(3)
            .onEach { (stage, percent) ->
                withContext(Dispatchers.Main) {
                    innerBinding.progressState.text = stage.name
                    innerBinding.progressBar.graceProgress = percent
                }
            }
            .onCompletion { e ->
                withContext(Dispatchers.Main) {
                    innerBinding.progressBar.graceProgress = 100
                }
                val compileTime = System.currentTimeMillis() - startMillis
                if (e == null) {
                    withContext(Dispatchers.Main) {
                        Snackbar.make(view,
                            translate {
                                Locale.ENGLISH { "Successfully compiled ${project.info.name} (${compileTime}ms)" }
                                Locale.KOREAN  { "${project.info.name} 컴파일 완료! (${compileTime}ms)" }
                            }, Snackbar.LENGTH_SHORT)
                            .setAnimationMode(Snackbar.ANIMATION_MODE_SLIDE)
                            .show()
                        binding?.updateState(project)
                    }
                }

                val awaitAnimation = GlobalConfig.category("project").getBoolean("compile_animation", true)
                if (awaitAnimation)
                    delay(PROGRESS_WAIT_TIME)

                withContext(Dispatchers.Main) {
                    innerBinding.showProgress(false)
                }
            }
            .catch { e ->
                val title = translate {
                    Locale.ENGLISH { "Failed to compile ${project.info.name}\n$e" }
                    Locale.KOREAN  { "${project.info.name} 컴파일 실패\n$e" }
                }
                LOG.error { title }
                withContext(Dispatchers.Main) {
                    Snackbar.make(view, title, Snackbar.LENGTH_LONG)
                        .apply {
                            setAction(translate {
                                Locale.ENGLISH { "Show all" }
                                Locale.KOREAN  { "자세히 보기" }
                            }) {
                                view.context.showErrorLogDialog(
                                    translate {
                                        Locale.ENGLISH { "Error log of ${project.info.name}\n$e" }
                                        Locale.KOREAN  { "${project.info.name} 에러 로그\n$e" }
                                    }, e)
                            }
                            animationMode = Snackbar.ANIMATION_MODE_SLIDE
                        }.show()
                    binding?.updateState(project)
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
                "fileDir" to File(project.directory, project.info.mainScript).path,
                "title"   to project.info.name
            )
        )
    }

    private fun Context.startDebugRoomActivity(view: View, project: Project) {
        if (!project.isCompiled) {
            Snackbar.make(view,
                translate {
                    Locale.ENGLISH { "Project isn't compiled yet." }
                    Locale.KOREAN  { "프로젝트 컴파일이 완료된 후 사용할 수 있어요." }
                }, Snackbar.LENGTH_SHORT)
                .setAnimationMode(Snackbar.ANIMATION_MODE_SLIDE)
                .show()
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
            setId(id.hashCode())
            if (layoutParams == null)
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            updateLayoutParams {
                width = dp(48)
                height = dp(48)
            }
            setBackgroundColor(context.getColor(R.color.transparent))
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
            config
                .category("beta_features")
                .getString("custom_buttons")
                ?.let<_, List<Map<String, PrimitiveTypedString>>>(Session.json::decodeFromString)
                ?.map { it["button_id"]!!.castAs<String>() to Icon.values()[it["button_icon"]!!.castAs()] }
                ?: listOf()
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

    private fun ExpandableCard.setIcon(project: Project) {
        setIcon {
            val icon: Any? = when(project.getLanguage().id) {
                "JS_RHINO" -> R.drawable.ic_js
                //"JS_V8" -> R.drawable.ic_v8
                else -> project.getLanguage().getIconFileOrNull()
            }
            val tint = if (icon == null) R.color.main_bright else null
            it.loadWithTint(
                data = icon ?: R.drawable.ic_round_developer_mode_24,
                tintColor = tint
            ) {
                transformations(RoundedCornersTransformation(context.resources.getDimension(R.dimen.lang_icon_corner_radius)))
            }
        }
    }

    private fun CardProjectsBinding.updateState(project: Project) {
        cardViewIsEnabled.setCardBackgroundColor(
            root.context.getCardColor(project))
        with(cardProject) {
            title           = project.info.name
            switchValue     = project.info.isEnabled
            isSwitchEnabled = project.isCompiled
        }
    }

    private fun Context.getCardColor(project: Project): Int {
        val colorRes = if (project.isCompiled) {
            if (project.info.isEnabled) {
                R.color.card_enabled
            } else {
                R.color.card_disabled
            }
        } else {
            R.color.orange
        }
        return getColor(colorRes)
    }

    override fun createBinding(inflater: LayoutInflater, parent: ViewGroup?): CardProjectsBinding =
        CardProjectsBinding.inflate(inflater, parent, false)

    companion object {
        private const val PROGRESS_WAIT_TIME = 400L
    }
}