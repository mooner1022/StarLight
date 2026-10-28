package dev.mooner.starlight.ui.plugins.store

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.getSystemService
import androidx.core.content.res.ResourcesCompat
import androidx.core.os.bundleOf
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.transition.MaterialContainerTransform
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.FragmentStoreDetailBinding
import dev.mooner.starlight.databinding.ItemStoreDependencyBinding
import dev.mooner.starlight.databinding.ItemStoreInfoBinding
import dev.mooner.starlight.databinding.ItemStoreReleaseBinding
import dev.mooner.starlight.databinding.ItemStoreStatBinding
import dev.mooner.starlight.plugincore.Info
import dev.mooner.starlight.plugincore.plugin.PluginDependency
import io.noties.markwon.Markwon
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class StoreDetailFragment : Fragment() {

    private var _binding: FragmentStoreDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StoreViewModel by activityViewModels()
    private val store: PluginStoreActivity
        get() = requireActivity() as PluginStoreActivity

    private lateinit var plugin: StorePlugin
    private lateinit var index: StoreIndex
    private var haloAnimator: ValueAnimator? = null
    private var dependencyRows: List<Pair<PluginDependency, ItemStoreDependencyBinding>> = emptyList()

    private val pluginId: String
        get() = requireArguments().getString(ARG_ID)!!

    private val transitionName: String?
        get() = requireArguments().getString(ARG_TRANSITION)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (transitionName != null)
            sharedElementEnterTransition = MaterialContainerTransform().apply {
                drawingViewId = R.id.storeContainer
                duration = 450
                scrimColor = Color.TRANSPARENT
                setAllContainerColors(requireContext().getColor(R.color.background))
            }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStoreDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val index = (viewModel.state.value as? StoreLoadState.Loaded)?.index
        val plugin = index?.find(pluginId)
        if (plugin == null) {
            parentFragmentManager.popBackStack()
            return
        }
        this.index = index
        this.plugin = plugin
        binding.detailRoot.transitionName = transitionName

        bindHeader()
        bindStats()
        bindTags()
        bindScreenshots()
        bindDescription()
        bindReleases()
        bindDependencies()
        bindInfo()

        with(binding) {
            back.setOnClickListener { parentFragmentManager.popBackStack() }
            share.setOnClickListener {
                store.showMockPeek(getString(R.string.plugin_store_mock, plugin.source ?: StoreSource.REPOSITORY_URL))
            }
            install.onCompleted = { button -> store.burst(button) }
            install.setOnClickListener {
                if (viewModel.installStateOf(plugin.id) == StoreInstallState.PendingRestart)
                    store.showMockPeek(getString(R.string.plugin_store_mock_restart))
                else
                    store.requestInstall(plugin)
            }
            scroll.setOnScrollChangeListener { _, _, scrollY, _, _ -> onScrolled(scrollY) }
        }

        viewModel.installStateOf(plugin.id)?.let { bindInstallState(it, animate = false) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.installStates.collect { states ->
                    states[plugin.id]?.let { bindInstallState(it, animate = true) }
                    bindDependencyStates(states)
                }
            }
        }

        playEnterAnimation(delayed = transitionName != null && savedInstanceState == null)
        startHalo()
    }

    private fun bindHeader() {
        with(binding) {
            iconFrame.clipToOutline = true
            icon.loadStoreImage(plugin.icon)
            name.text = plugin.name
            topTitle.text = plugin.name
            authors.text = "${plugin.authorNames()} · ${index.categoryName(plugin.category)}"
            if (plugin.isVerified)
                authors.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_store_sparkle_small, 0, 0, 0)
            pluginId.text = plugin.id
            stars.seed = plugin.id.hashCode()

            val api = plugin.latest.apiVersion
            if (api incompatibleWith Info.PLUGINCORE_VERSION) {
                warning.visibility = View.VISIBLE
                warning.text = getString(
                    R.string.plugin_store_incompatible_warning,
                    "${api.major}.${api.minor}",
                    Info.PLUGINCORE_VERSION.toString(),
                )
            }
        }
    }

    private fun bindInstallState(state: StoreInstallState, animate: Boolean) {
        val latest = plugin.latest
        with(binding) {
            install.setState(state, animate)
            val hint = when (state) {
                StoreInstallState.NotInstalled       -> getString(R.string.plugin_store_hint_install)
                is StoreInstallState.UpdateAvailable -> getString(R.string.plugin_store_hint_update, "v${state.installed}", "v${latest.version}")
                StoreInstallState.Installed          -> getString(R.string.plugin_store_hint_installed)
                is StoreInstallState.Downloading     -> getString(
                    R.string.plugin_store_hint_downloading,
                    requireContext().formatSize(latest.size * state.progress / 100),
                    requireContext().formatSize(latest.size),
                )
                StoreInstallState.Verifying          -> getString(R.string.plugin_store_hint_verifying)
                StoreInstallState.PendingRestart     -> getString(R.string.plugin_store_hint_pending)
                StoreInstallState.Incompatible       -> null
            }
            installHint.visibility = if (hint == null) View.GONE else View.VISIBLE
            installHint.text = hint
        }
    }

    private fun bindStats() {
        val latest = plugin.latest
        val stats = listOf(
            "v${latest.version}"                                    to R.string.plugin_store_stat_version,
            requireContext().formatSize(latest.size)                to R.string.plugin_store_stat_size,
            "${latest.apiVersion.major}.${latest.apiVersion.minor}" to R.string.plugin_store_stat_api,
            compactDate(latest.releasedAtMillis)                    to R.string.plugin_store_stat_updated,
        )
        val parent = binding.stats
        stats.forEachIndexed { i, (value, label) ->
            if (i > 0)
                parent.addView(View(requireContext()).apply {
                    setBackgroundColor(requireContext().getColor(R.color.store_chip_stroke))
                }, LinearLayout.LayoutParams(dp(1f).toInt(), dp(28f).toInt()).apply {
                    gravity = Gravity.CENTER_VERTICAL
                })
            ItemStoreStatBinding.inflate(layoutInflater, parent, true).apply {
                this.value.text = value
                this.label.setText(label)
            }
        }
    }

    private fun bindTags() {
        val parent = binding.tags
        for (tag in plugin.tags) {
            parent.addView(TextView(requireContext()).apply {
                text = "# $tag"
                setBackgroundResource(R.drawable.bg_store_pill)
                setTextColor(requireContext().getColor(R.color.store_gold_deep))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = ResourcesCompat.getFont(requireContext(), R.font.nanumsquare_neo_bold)
                val horizontal = dp(12f).toInt()
                val vertical = dp(6f).toInt()
                setPadding(horizontal, vertical, horizontal, vertical)
            }, FlexboxLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                val margin = dp(4f).toInt()
                setMargins(margin, margin, margin, margin)
            })
        }
    }

    private fun bindScreenshots() {
        with(binding.screenshots) {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = StoreScreenshotAdapter(plugin) { frame ->
                frame.animate().cancel()
                frame.scaleX = 0.94f
                frame.scaleY = 0.94f
                frame.animate().scaleX(1f).scaleY(1f).setInterpolator(OvershootInterpolator(4f)).setDuration(380).start()
            }
        }
        val visibility = if (plugin.screenshots.isEmpty()) View.GONE else View.VISIBLE
        binding.previewTitle.visibility = visibility
        binding.screenshots.visibility = visibility
    }

    private fun bindDescription() {
        val markwon = Markwon.create(requireContext())
        with(binding) {
            markwon.setMarkdown(description, plugin.description)
            descriptionMore.visibility = View.GONE
            description.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
                override fun onLayoutChange(v: View, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or: Int, ob: Int) {
                    if (description.lineCount <= COLLAPSED_LINES)
                        return
                    description.removeOnLayoutChangeListener(this)
                    description.post {
                        description.maxLines = COLLAPSED_LINES
                        descriptionMore.visibility = View.VISIBLE
                    }
                }
            })
            descriptionMore.setOnClickListener {
                val expand = description.maxLines == COLLAPSED_LINES
                TransitionManager.beginDelayedTransition(detailContent, ChangeBounds().setDuration(260))
                description.maxLines = if (expand) Int.MAX_VALUE else COLLAPSED_LINES
                descriptionMore.setText(if (expand) R.string.plugin_store_less else R.string.plugin_store_more)
            }
        }
    }

    private fun bindReleases() {
        val markwon = Markwon.create(requireContext())
        val parent = binding.releases
        val rows = plugin.releases.mapIndexed { i, release ->
            ItemStoreReleaseBinding.inflate(layoutInflater, parent, true).apply {
                version.text = "v${release.version}"
                latest.visibility = if (i == 0) View.VISIBLE else View.GONE
                date.text = relativeTime(release.releasedAtMillis)
                markwon.setMarkdown(changelog, release.changelog)
                meta.text = "API ${release.apiVersion.major}.${release.apiVersion.minor} · ${requireContext().formatSize(release.size)}"
                val isCompatible = release.apiVersion compatibleWith Info.PLUGINCORE_VERSION
                installVersion.visibility = if (i > 0 && isCompatible) View.VISIBLE else View.GONE
                installVersion.setOnClickListener {
                    store.showMockPeek(getString(R.string.plugin_store_mock, StoreSource.resolve(release.file)))
                }
                if (i > 0)
                    root.visibility = View.GONE
            }
        }
        val older = rows.size - 1
        with(binding.releasesMore) {
            if (older <= 0) {
                visibility = View.GONE
                return
            }
            text = getString(R.string.plugin_store_older_versions, older)
            setOnClickListener {
                val expand = rows[1].root.visibility != View.VISIBLE
                TransitionManager.beginDelayedTransition(binding.detailContent, ChangeBounds().setDuration(280))
                for (row in rows.drop(1)) {
                    row.root.visibility = if (expand) View.VISIBLE else View.GONE
                    if (expand) {
                        row.root.alpha = 0f
                        row.root.animate().alpha(1f).setStartDelay(120).setDuration(260).start()
                    }
                }
                text = if (expand) getString(R.string.plugin_store_hide_older) else getString(R.string.plugin_store_older_versions, older)
                setCompoundDrawablesRelativeWithIntrinsicBounds(
                    0, 0,
                    if (expand) dev.mooner.configdsl.R.drawable.ic_round_keyboard_arrow_up_24 else R.drawable.ic_round_keyboard_arrow_down_24,
                    0,
                )
            }
        }
    }

    private fun bindDependencies() {
        val dependencies = plugin.latest.dependency
        if (dependencies.isEmpty()) {
            binding.dependenciesTitle.visibility = View.GONE
            binding.dependencies.visibility = View.GONE
            return
        }
        dependencyRows = dependencies.map { dependency ->
            val target = index.find(dependency.pluginId)
            dependency to ItemStoreDependencyBinding.inflate(layoutInflater, binding.dependencies, true).apply {
                iconFrame.clipToOutline = true
                icon.loadStoreImage(target?.icon)
                name.text = target?.name ?: dependency.pluginId
                version.text = "${dependency.pluginId} · ${requireContext().dependencyRange(dependency)}"
                if (target != null)
                    root.setOnClickListener { store.openDetail(target.id, null) }
            }
        }
        bindDependencyStates(viewModel.installStates.value)
    }

    private fun bindDependencyStates(states: Map<String, StoreInstallState>) {
        for ((dependency, row) in dependencyRows) {
            val state = states[dependency.pluginId]
            val isInstalled = viewModel.installedVersionOf(dependency.pluginId) != null ||
                    state == StoreInstallState.PendingRestart
            val (text, color) = when {
                isInstalled                             -> R.string.plugin_store_dep_installed to R.color.status_ok
                index.find(dependency.pluginId) == null -> R.string.plugin_store_dep_unknown to R.color.status_error
                dependency.isOptional                   -> R.string.plugin_store_dep_optional to R.color.text_sub
                else                                    -> R.string.plugin_store_dep_required to R.color.status_warning
            }
            row.status.setText(text)
            row.status.setTextColor(requireContext().getColor(color))
        }
    }

    private fun bindInfo() {
        val latest = plugin.latest
        val rows = buildList {
            add(Triple(R.string.plugin_store_info_id, plugin.id, true))
            add(Triple(R.string.plugin_store_info_developers, plugin.authorNames(), false))
            add(Triple(R.string.plugin_store_info_category, index.categoryName(plugin.category), false))
            add(Triple(R.string.plugin_store_info_license, plugin.license, false))
            add(Triple(R.string.plugin_store_info_api, latest.apiVersion.toString(), false))
            add(Triple(
                R.string.plugin_store_info_native,
                getString(if (latest.nativeLibrary) R.string.plugin_store_info_native_yes else R.string.plugin_store_info_native_no),
                false,
            ))
            add(Triple(R.string.plugin_store_info_file, latest.fileName, false))
            add(Triple(R.string.plugin_store_info_sha256, latest.sha256, true))
            plugin.source?.let { add(Triple(R.string.plugin_store_info_source, it.removePrefix("https://"), true)) }
            add(Triple(
                R.string.plugin_store_info_published,
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(plugin.publishedAtMillis)),
                false,
            ))
        }
        for ((key, value, copyable) in rows)
            ItemStoreInfoBinding.inflate(layoutInflater, binding.info, true).apply {
                this.key.setText(key)
                this.value.text = value
                if (copyable)
                    root.setOnLongClickListener {
                        requireContext().getSystemService<ClipboardManager>()
                            ?.setPrimaryClip(ClipData.newPlainText(getString(key), value))
                        store.showMockPeek(getString(R.string.plugin_store_copied))
                        true
                    }
            }
    }

    private fun playEnterAnimation(delayed: Boolean) {
        val baseDelay = if (delayed) 260L else 60L
        with(binding) {
            iconFrame.scaleX = 0.5f
            iconFrame.scaleY = 0.5f
            iconFrame.alpha = 0f
            iconFrame.animate()
                .scaleX(1f).scaleY(1f).alpha(1f)
                .setStartDelay(baseDelay)
                .setDuration(520)
                .setInterpolator(OvershootInterpolator(2.2f))
                .start()
            val offset = dp(22f)
            detailContent.children.take(14).forEachIndexed { i, child ->
                child.alpha = 0f
                child.translationY = offset
                child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(baseDelay + 40L + i * 35L)
                    .setDuration(420)
                    .setInterpolator(DecelerateInterpolator(2f))
                    .start()
            }
        }
    }

    private fun startHalo() {
        haloAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2600
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                val binding = _binding ?: return@addUpdateListener
                val t = it.animatedValue as Float
                binding.haloOuter.setHaloPhase(t)
                binding.haloInner.setHaloPhase((t + 0.5f) % 1f)
            }
            start()
        }
    }

    private fun View.setHaloPhase(t: Float) {
        val scale = 0.62f + t * 0.75f
        scaleX = scale
        scaleY = scale
        alpha = (1f - t) * 0.45f
    }

    private fun onScrolled(scrollY: Int) {
        val binding = _binding ?: return
        with(binding) {
            val range = (banner.height - topBar.height).coerceAtLeast(1)
            val progress = (scrollY.toFloat() / range).coerceIn(0f, 1f)
            topBarBackground.alpha = progress
            topTitle.alpha = ((progress - 0.75f) / 0.25f).coerceIn(0f, 1f)
            val tint = ArgbEvaluator().evaluate(
                progress,
                requireContext().getColor(R.color.store_star),
                requireContext().getColor(R.color.text),
            ) as Int
            back.imageTintList = ColorStateList.valueOf(tint)
            share.imageTintList = ColorStateList.valueOf(tint)
            stars.parallax = scrollY * 0.5f
            val shift = scrollY * 0.45f
            iconFrame.translationY = shift
            haloOuter.translationY = shift
            haloInner.translationY = shift
            val scale = 1f - progress * 0.25f
            iconFrame.scaleX = scale
            iconFrame.scaleY = scale
        }
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    override fun onDestroyView() {
        haloAnimator?.cancel()
        haloAnimator = null
        dependencyRows = emptyList()
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_ID = "plugin_id"
        private const val ARG_TRANSITION = "transition_name"
        private const val COLLAPSED_LINES = 6

        fun newInstance(pluginId: String, transitionName: String?): StoreDetailFragment =
            StoreDetailFragment().apply {
                arguments = bundleOf(ARG_ID to pluginId, ARG_TRANSITION to transitionName)
            }
    }
}
