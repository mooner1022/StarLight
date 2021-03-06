package dev.mooner.starlight.ui.projects

import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import coil.transform.RoundedCornersTransformation
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.snackbar.Snackbar
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.CardProjectButtonsBinding
import dev.mooner.starlight.databinding.CardProjectsBinding
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.config.TypedString
import dev.mooner.starlight.plugincore.editor.CodeEditorActivity
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.utils.Icon
import dev.mooner.starlight.ui.async.AsyncCell
import dev.mooner.starlight.ui.debugroom.DebugRoomActivity
import dev.mooner.starlight.ui.editor.DefaultEditorActivity
import dev.mooner.starlight.ui.presets.ExpandableCard
import dev.mooner.starlight.ui.projects.config.ProjectConfigActivity
import dev.mooner.starlight.ui.projects.info.startProjectInfoActivity
import dev.mooner.starlight.utils.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.decodeFromString
import java.io.File
import kotlin.properties.Delegates
import kotlin.system.measureTimeMillis

class ProjectListAdapter(
    private val context: Context,
    var data: List<Project> = listOf()
) : RecyclerView.Adapter<ProjectListAdapter.ProjectListViewHolder>() {

    private val containers: MutableMap<String, ViewContainer> = hashMapOf()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProjectListViewHolder {
        //val view = LayoutInflater.from(context).inflate(R.layout.card_projects, parent, false)
        return ProjectListViewHolder(ProjectViewCell(parent.context).apply(ProjectViewCell::inflate))
    }

    override fun getItemCount(): Int = data.size

    override fun getItemViewType(position: Int): Int = 0

    @Suppress("DEPRECATED_SMARTCAST")
    override fun onBindViewHolder(holder: ProjectListViewHolder, position: Int) {
        (holder.itemView as ProjectViewCell).bindWhenInflated {
            val project = data[position]
            val binding = holder.itemView.binding

            fun refreshState() {
                binding.cardViewIsEnabled.setCardBackgroundColor(getCardColor(project))
                with(binding.cardProject) {
                    switchValue = project.info.isEnabled
                    isSwitchEnabled = project.isCompiled
                }
            }

            binding.cardProject.apply {
                setOnSwitchChangeListener { _, isChecked ->
                    if (project.info.isEnabled != isChecked) {
                        project.info.isEnabled = isChecked
                        project.saveInfo()
                    }
                    refreshState()
                }

                refreshState()

                setIcon {
                    val icon: Any? = when(project.getLanguage().id) {
                        "JS_RHINO" -> R.drawable.ic_js
                        //"JS_V8" -> R.drawable.ic_v8
                        else -> project.getLanguage().getIconFileOrNull()
                    }
                    val tint = if (icon == null) R.color.main_purple else null
                    it.loadAnyWithTint(
                        data = icon?: R.drawable.ic_round_developer_mode_24,
                        tintColor = tint
                    ) {
                        transformations(RoundedCornersTransformation(context.resources.getDimension(R.dimen.lang_icon_corner_radius)))
                    }
                }
                title = project.info.name

                setOnInnerViewInflateListener {
                    val container = containers[project.info.name] ?: let {
                        val mContainer = ViewContainer(binding, project)
                        containers[project.info.name] = mContainer
                        mContainer
                    }

                    val tintColor = if (project.info.isPinned)
                        R.color.code_yellow
                    else
                        R.color.text
                    container.binding.buttonProjectPin.loadWithTint(R.drawable.ic_round_star_24, tintColor)

                    fun findButtonById(id: Int): ImageButton = findViewById(id)

                    for (id in buttonIds) {
                        findButtonById(id).setOnClickListener(container.onClickListener)
                    }

                    val buttonBackgroundColor = context.getColor(R.color.transparent)
                    val customButtons: List<MutableMap<String, TypedString>> =
                        Session.json.decodeFromString(project.config.category("beta_features").getString("custom_buttons", "[]"))
                    for (button in customButtons) {
                        val buttonId = button["button_id"]!!.castAs<String>()
                        val mButton = ImageButton(context).apply {
                            applyLayoutParams {
                                width = dp(48)
                                height = dp(48)
                            }
                            setBackgroundColor(buttonBackgroundColor)
                            //load(button.icon.drawableRes)
                            val icon = Icon.valueOf(button["button_icon"]!!.castAs())
                            loadAnyWithTint(data = icon.drawableRes, tintColor = R.color.text)
                            setOnClickListener {
                                //Toast.makeText(context, "Button Clicked!", Toast.LENGTH_SHORT).show()
                                project.callFunction("onProjectButtonClicked", arrayOf(buttonId)) {
                                    Toast.makeText(context, "?????? ??????????????? ????????? ???????????????: $it", Toast.LENGTH_LONG).show()
                                    it.printStackTrace()
                                }
                            }
                        }

                        container.customButtonInstances[buttonId] = mButton
                        findViewById<FlexboxLayout>(R.id.innerView).addView(mButton)
                    }
                }
            }
        }
    }

    private fun getCardColor(project: Project): Int {
        val colorRes = if (project.isCompiled) {
            if (project.info.isEnabled) {
                R.color.card_enabled
            } else {
                R.color.card_disabled
            }
        } else {
            R.color.orange
        }
        return colorCache[colorRes] ?: let {
            val color = context.getColor(colorRes)
            colorCache[colorRes] = color
            color
        }
    }

    private fun Context.startActivityWithExtra(clazz: Class<*>, extras: Map<String, String> = mapOf()) {
        val intent = Intent(this, clazz).apply {
            if (extras.isNotEmpty()) {
                for ((name, value) in extras) {
                    putExtra(name, value)
                }
            }
        }
        startActivity(intent)
    }

    private fun ViewContainer.showProgress(show: Boolean) {
        val buttonVisibility = if (show) View.GONE else View.VISIBLE
        binding.apply {
            (arrayOf(
                buttonEditCode,
                buttonRecompile,
                buttonDebugRoom,
                buttonProjectConfig,
                buttonProjectInfo,
                buttonProjectPin
            ) + customButtonInstances.map { it.value }).forEach { it.visibility = buttonVisibility }

            progressWrapper.visibility = if (!show) View.GONE else View.VISIBLE
        }
    }

    private inner class OnClickListenerImpl(
        private val container: ViewContainer,
        private val project: Project,
    ): View.OnClickListener {
        override fun onClick(view: View) {
            val context = view.context
            when(view.id) {
                R.id.buttonDebugRoom -> {
                    if (!project.isCompiled) {
                        Snackbar.make(view, "?????? ??????????????? ??????????????? ????????????.", Snackbar.LENGTH_SHORT).show()
                        return
                    }
                    context.startActivityWithExtra(
                        DebugRoomActivity::class.java,
                        mapOf("projectName" to project.info.name)
                    )
                }
                R.id.buttonProjectConfig ->
                    context.startActivityWithExtra(
                        ProjectConfigActivity::class.java,
                        mapOf("projectName" to project.info.name)
                    )
                R.id.buttonProjectInfo ->
                    context.startProjectInfoActivity(project)
                R.id.buttonEditCode ->
                    context.startActivityWithExtra(
                        DefaultEditorActivity::class.java,
                        mapOf(
                            CodeEditorActivity.KEY_BASE_DIRECTORY to project.directory.path,
                            CodeEditorActivity.KEY_PROJECT_NAME   to project.info.name,
                            "fileDir" to File(project.directory, project.info.mainScript).path,
                            "title" to project.info.name
                        )
                    )
                R.id.buttonProjectPin -> {
                    project.info.isPinned = !project.info.isPinned
                    val tintColor = if (project.info.isPinned)
                        R.color.code_yellow
                    else
                        R.color.text
                    container.binding.buttonProjectPin.loadWithTint(R.drawable.ic_round_star_24, tintColor)
                    project.saveInfo()
                }
                R.id.buttonRecompile -> {
                    container.showProgress(true)
                    container.binding.progressBar.progress = 0

                    val compileFlow = project.compileAsync(true)
                        .flowOn(Dispatchers.Default)
                        .onEach { (stage, percent) ->
                            container.binding.progressState.text = stage
                            container.binding.progressBar.graceProgress = percent
                        }
                        .catch { e ->
                            Snackbar.make(view, "[${project.info.name}]??? ???????????? ???????????????.\n$e", Snackbar.LENGTH_LONG).apply {
                                setAction("????????? ??????") {
                                    view.context.showErrorLogDialog(project.info.name + " ?????? ??????", e)
                                }
                            }.show()
                            container.cardViewIsEnabled.setCardBackgroundColor(getCardColor(project))
                            container.expandable.isSwitchEnabled = false
                        }

                    CoroutineScope(Dispatchers.Default).launch {
                        val compileTime = measureTimeMillis { compileFlow.collect() }

                        withContext(Dispatchers.Main) {
                            container.binding.progressBar.graceProgress = 100
                        }
                        val awaitAnimation = Session.globalConfig.category("project").getBoolean("compile_animation", true)
                        if (awaitAnimation && compileTime < MIN_COMPILE_TIME) {
                            delay(MIN_COMPILE_TIME - compileTime)
                        }
                        withContext(Dispatchers.Main) {
                            Snackbar.make(view, "[${project.info.name}] ????????? ??????!", Snackbar.LENGTH_SHORT).show()
                            container.apply {
                                cardViewIsEnabled.setCardBackgroundColor(getCardColor(project))
                                expandable.isSwitchEnabled = true
                                showProgress(false)
                            }
                        }
                    }
                }
            }
        }
    }

    private inner class ViewContainer(mBinding: CardProjectsBinding, project: Project) {
        val binding = CardProjectButtonsBinding.bind(mBinding.cardProject.findViewById(R.id.innerView))

        val cardViewIsEnabled: CardView = mBinding.cardViewIsEnabled
        val expandable: ExpandableCard  = mBinding.cardProject

        val customButtonInstances: MutableMap<String, ImageButton> = hashMapOf()

        val onClickListener: View.OnClickListener by lazy { OnClickListenerImpl(this, project) }
    }

    private inner class ProjectViewCell(context: Context): AsyncCell(context) {
        var binding: CardProjectsBinding by Delegates.notNull()
        override val layoutId: Int = R.layout.card_projects
        override fun createDataBindingView(view: View): View? {
            binding = CardProjectsBinding.bind(view)
            return view.rootView
        }
    }

    companion object {
        private const val MIN_COMPILE_TIME = 400
        private val buttonIds: Array<Int> = arrayOf(
            R.id.buttonDebugRoom,
            R.id.buttonProjectInfo,
            R.id.buttonProjectConfig,
            R.id.buttonEditCode,
            R.id.buttonRecompile,
            R.id.buttonProjectPin
        )

        private val colorCache: MutableMap<Int, Int> = hashMapOf()
    }

    inner class ProjectListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
    /*{
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
    }*/
}