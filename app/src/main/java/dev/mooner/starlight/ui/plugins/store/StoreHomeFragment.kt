package dev.mooner.starlight.ui.plugins.store

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.os.Bundle
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.view.animation.LayoutAnimationController
import android.view.animation.LinearInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.content.getSystemService
import androidx.core.view.children
import androidx.core.view.doOnPreDraw
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.carousel.CarouselLayoutManager
import com.google.android.material.carousel.CarouselSnapHelper
import com.google.android.material.carousel.HeroCarouselStrategy
import com.google.android.material.chip.Chip
import com.google.android.material.transition.Hold
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.FragmentStoreHomeBinding
import dev.mooner.starlight.databinding.ItemStoreInfoBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class StoreHomeFragment : Fragment() {

    private var _binding: FragmentStoreHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StoreViewModel by activityViewModels()
    private val store: PluginStoreActivity
        get() = requireActivity() as PluginStoreActivity

    private lateinit var listAdapter: StorePluginAdapter
    private lateinit var featuredAdapter: StoreFeaturedAdapter
    private val snapHelper = CarouselSnapHelper()

    private var shownIndex: StoreIndex? = null
    private var featuredPosition = 0
    private var logoAnimator: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        exitTransition = Hold().apply { duration = 450 }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStoreHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()
        (view.parent as? ViewGroup ?: view).doOnPreDraw { startPostponedEnterTransition() }

        listAdapter = StorePluginAdapter(
            onClick = { plugin, card -> store.openDetail(plugin.id, card) },
            onInstall = { plugin -> onInstallClicked(plugin) },
            onCompleted = { button -> store.burst(button) },
        )
        featuredAdapter = StoreFeaturedAdapter { plugin, card -> store.openDetail(plugin.id, card) }

        with(binding) {
            list.layoutManager = LinearLayoutManager(requireContext())
            list.adapter = listAdapter
            list.layoutAnimation = LayoutAnimationController(
                AnimationUtils.loadAnimation(requireContext(), R.anim.store_item_enter), 0.12f
            )

            val carousel = CarouselLayoutManager(HeroCarouselStrategy())
            featured.layoutManager = carousel
            featured.adapter = featuredAdapter
            snapHelper.attachToRecyclerView(featured)
            featured.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_IDLE)
                        snapHelper.findSnapView(carousel)?.let { featuredPosition = carousel.getPosition(it) }
                }
            })

            back.setOnClickListener { requireActivity().finish() }
            refresh.setOnClickListener {
                refresh.animate().cancel()
                refresh.rotation = 0f
                refresh.animate().rotation(360f).setDuration(600).start()
                viewModel.load()
            }
            repository.setOnClickListener { showRepositorySheet() }
            retry.setOnClickListener { viewModel.load() }

            searchInput.setText(viewModel.query)
            searchClear.visibility = if (viewModel.query.isEmpty()) View.INVISIBLE else View.VISIBLE
            searchInput.doAfterTextChanged { text ->
                val query = text?.toString()?.trim().orEmpty()
                searchClear.visibility = if (query.isEmpty()) View.INVISIBLE else View.VISIBLE
                if (query != viewModel.query) {
                    viewModel.query = query
                    submitList()
                }
            }
            searchInput.setOnEditorActionListener { input, actionId, _ ->
                if (actionId != EditorInfo.IME_ACTION_SEARCH)
                    return@setOnEditorActionListener false
                requireContext().getSystemService<InputMethodManager>()
                    ?.hideSoftInputFromWindow(input.windowToken, 0)
                input.clearFocus()
                true
            }
            searchClear.setOnClickListener { searchInput.text.clear() }

            sort.setOnClickListener {
                viewModel.sort = StoreSort.entries[(viewModel.sort.ordinal + 1) % StoreSort.entries.size]
                updateSortText()
                submitList()
            }
            updateSortText()

            scroll.setOnScrollChangeListener { _, _, scrollY, _, _ -> onScrolled(scrollY) }
        }

        logoAnimator = ObjectAnimator.ofFloat(binding.heroLogo, View.ROTATION, 0f, 360f).apply {
            duration = 60_000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }

        listAdapter.installStates = viewModel.installStates.value
        render(viewModel.state.value)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::render) }
                launch {
                    viewModel.installStates.collect { states ->
                        listAdapter.installStates = states
                        updateUpdatesCard()
                    }
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    delay(5000)
                    val featured = binding.featured
                    val count = featured.adapter?.itemCount ?: 0
                    if (count > 1 && featured.scrollState == RecyclerView.SCROLL_STATE_IDLE) {
                        featuredPosition = (featuredPosition + 1) % count
                        featured.smoothScrollToPosition(featuredPosition)
                    }
                }
            }
        }
    }

    private fun render(state: StoreLoadState) {
        with(binding) {
            when (state) {
                StoreLoadState.Loading -> {
                    shownIndex = null
                    error.fadeOut()
                    content.fadeOut()
                    skeleton.fadeIn()
                    repositorySync.text = getString(R.string.plugin_store_synced, "…")
                }
                is StoreLoadState.Failed -> {
                    shownIndex = null
                    skeleton.fadeOut()
                    content.fadeOut()
                    errorMessage.text = state.error.message ?: state.error.toString()
                    error.fadeIn()
                    errorStar.rotation = -30f
                    errorStar.animate().rotation(0f).setInterpolator(DecelerateInterpolator(2f)).setDuration(900).start()
                }
                is StoreLoadState.Loaded -> {
                    if (shownIndex === state.index)
                        return
                    shownIndex = state.index
                    bindIndex(state.index)
                    error.visibility = View.GONE
                    if (viewModel.hasRevealedContent) {
                        skeleton.visibility = View.GONE
                        content.visibility = View.VISIBLE
                        content.alpha = 1f
                    } else {
                        viewModel.hasRevealedContent = true
                        reveal()
                    }
                }
            }
        }
    }

    private fun bindIndex(index: StoreIndex) {
        with(binding) {
            repositoryName.text = index.name
            repositorySync.text = getString(R.string.plugin_store_synced, relativeTime(index.generatedAtMillis))
            featuredAdapter.submit(index)
        }
        bindCategories(index)
        submitList()
    }

    private fun reveal() {
        with(binding) {
            skeleton.fadeOut()
            content.alpha = 1f
            content.visibility = View.VISIBLE
            val offset = dp(28f)
            content.children.forEachIndexed { i, child ->
                child.alpha = 0f
                child.translationY = offset
                child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(80L + i * 70L)
                    .setDuration(480)
                    .setInterpolator(DecelerateInterpolator(2.2f))
                    .start()
            }
            list.scheduleLayoutAnimation()
        }
    }

    private fun bindCategories(index: StoreIndex) {
        val group = binding.categories
        group.setOnCheckedStateChangeListener(null)
        group.removeAllViews()
        val entries = listOf<StoreCategory?>(null) + index.categories
        for (category in entries) {
            val chip = createChip(category?.name?.get() ?: getString(R.string.plugin_store_category_all))
            chip.tag = category?.id
            group.addView(chip)
            if (category?.id == viewModel.category)
                chip.isChecked = true
        }
        group.setOnCheckedStateChangeListener { chipGroup, ids ->
            val chip = ids.firstOrNull()?.let { chipGroup.findViewById<Chip>(it) } ?: return@setOnCheckedStateChangeListener
            TransitionManager.beginDelayedTransition(chipGroup, AutoTransition().setDuration(180))
            viewModel.category = chip.tag as String?
            submitList()
        }
    }

    private fun createChip(text: String): Chip =
        Chip(requireContext()).apply {
            id = View.generateViewId()
            this.text = text
            isCheckable = true
            isCheckedIconVisible = false
            setTextAppearance(R.style.StoreChipText)
            chipBackgroundColor = requireContext().getColorStateList(R.color.store_chip_background)
            setTextColor(requireContext().getColorStateList(R.color.store_chip_text))
            chipStrokeColor = requireContext().getColorStateList(R.color.store_chip_stroke_selector)
            chipStrokeWidth = dp(1f)
            chipCornerRadius = dp(18f)
            chipMinHeight = dp(38f)
            chipStartPadding = dp(14f)
            chipEndPadding = dp(14f)
            textStartPadding = 0f
            textEndPadding = 0f
            setChipIconResource(R.drawable.ic_store_sparkle_small)
            chipIconTint = ColorStateList.valueOf(requireContext().getColor(R.color.store_on_gold))
            chipIconSize = dp(12f)
            iconStartPadding = 0f
            iconEndPadding = dp(5f)
            isChipIconVisible = false
            setOnCheckedChangeListener { _, checked -> isChipIconVisible = checked }
        }

    private fun filteredPlugins(index: StoreIndex): List<StorePlugin> {
        val query = viewModel.query
        val category = viewModel.category
        val filtered = index.plugins.filter { plugin ->
            (category == null || plugin.category == category) &&
                    (query.isEmpty() ||
                            plugin.name.contains(query, ignoreCase = true) ||
                            plugin.id.contains(query, ignoreCase = true) ||
                            plugin.summary.contains(query, ignoreCase = true) ||
                            plugin.tags.any { it.contains(query, ignoreCase = true) } ||
                            plugin.authors.any { it.name.contains(query, ignoreCase = true) })
        }
        return when (viewModel.sort) {
            StoreSort.UPDATED   -> filtered.sortedByDescending { it.latest.releasedAtMillis }
            StoreSort.NAME      -> filtered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        }
    }

    private fun submitList() {
        val index = shownIndex ?: return
        val items = filteredPlugins(index)
        val isFiltering = viewModel.query.isNotEmpty() || viewModel.category != null
        with(binding) {
            val featuredVisibility = if (isFiltering) View.GONE else View.VISIBLE
            if (featuredSection.visibility != featuredVisibility) {
                TransitionManager.beginDelayedTransition(content, AutoTransition().setDuration(260))
                featuredSection.visibility = featuredVisibility
            }
            listTitle.setText(if (isFiltering) R.string.plugin_store_results else R.string.plugin_store_all)
            listCount.text = items.size.toString()
            empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
        listAdapter.submitList(items)
        updateUpdatesCard()
    }

    private fun updateUpdatesCard() {
        val index = shownIndex ?: return
        val states = viewModel.installStates.value
        val updatable = index.plugins.filter { states[it.id] is StoreInstallState.UpdateAvailable }
        val isFiltering = viewModel.query.isNotEmpty() || viewModel.category != null
        with(binding) {
            val visibility = if (updatable.isEmpty() || isFiltering) View.GONE else View.VISIBLE
            if (updatesCard.visibility != visibility) {
                TransitionManager.beginDelayedTransition(content, AutoTransition().setDuration(260))
                updatesCard.visibility = visibility
            }
            if (updatable.isEmpty())
                return
            updatesTitle.text = getString(R.string.plugin_store_updates_title, updatable.size)
            updatesNames.text = updatable.joinToString { it.name }
            updateAll.setOnClickListener { viewModel.install(updatable.map(StorePlugin::id)) }
        }
    }

    private fun updateSortText() {
        binding.sort.setText(when (viewModel.sort) {
            StoreSort.UPDATED   -> R.string.plugin_store_sort_updated
            StoreSort.NAME      -> R.string.plugin_store_sort_name
        })
    }

    private fun onInstallClicked(plugin: StorePlugin) {
        if (viewModel.installStateOf(plugin.id) == StoreInstallState.PendingRestart)
            store.showMockPeek(getString(R.string.plugin_store_mock_restart))
        else
            store.requestInstall(plugin)
    }

    private fun showRepositorySheet() {
        val index = shownIndex
        requireContext().showStoreSheet {
            title = getString(R.string.plugin_store_repository)
            icon = dev.mooner.configdsl.R.drawable.ic_github
            note = getString(R.string.plugin_store_repository_message)
            if (index != null)
                content { inflater, parent ->
                    listOf(
                        R.string.plugin_store_repository_name    to index.name,
                        R.string.plugin_store_repository_url     to StoreSource.REPOSITORY_URL.removePrefix("https://"),
                        R.string.plugin_store_repository_branch  to StoreSource.BRANCH,
                        R.string.plugin_store_repository_updated to relativeTime(index.generatedAtMillis),
                    ).forEach { (key, value) ->
                        ItemStoreInfoBinding.inflate(inflater, parent, true).apply {
                            this.key.setText(key)
                            this.value.text = value
                        }
                    }
                }
            positive(getString(R.string.plugin_store_reload)) { viewModel.load() }
            negative(getString(R.string.close))
        }
    }

    private fun onScrolled(scrollY: Int) {
        val binding = _binding ?: return
        with(binding) {
            val range = (hero.height - topBar.height).coerceAtLeast(1)
            val progress = (scrollY.toFloat() / range).coerceIn(0f, 1f)
            topBarBackground.alpha = progress
            topTitle.alpha = ((progress - 0.7f) / 0.3f).coerceIn(0f, 1f)
            val tint = ArgbEvaluator().evaluate(
                progress,
                requireContext().getColor(R.color.store_star),
                requireContext().getColor(R.color.text),
            ) as Int
            back.imageTintList = ColorStateList.valueOf(tint)
            refresh.imageTintList = ColorStateList.valueOf(tint)
            stars.parallax = scrollY * 0.6f
            heroText.translationY = scrollY * 0.35f
            heroText.alpha = 1f - (progress * 1.3f).coerceAtMost(1f)
            heroLogo.translationY = scrollY * 0.2f
        }
    }

    private fun View.fadeIn() {
        animate().cancel()
        if (visibility == View.VISIBLE && alpha == 1f)
            return
        if (visibility != View.VISIBLE)
            alpha = 0f
        visibility = View.VISIBLE
        animate().alpha(1f).setDuration(280).start()
    }

    private fun View.fadeOut() {
        animate().cancel()
        if (visibility != View.VISIBLE)
            return
        animate().alpha(0f).setDuration(200).withEndAction { visibility = View.GONE }.start()
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    override fun onDestroyView() {
        logoAnimator?.cancel()
        logoAnimator = null
        shownIndex = null
        super.onDestroyView()
        _binding = null
    }
}
