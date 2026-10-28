package dev.mooner.starlight.ui.plugins.store

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.marginBottom
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import dev.mooner.peekalert.PeekAlert
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.ActivityPluginStoreBinding
import dev.mooner.starlight.utils.createSimplePeek
import dev.mooner.starlight.utils.setCommonAttrsWithLifecycle
import kotlinx.coroutines.launch

class PluginStoreActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPluginStoreBinding
    private val viewModel: StoreViewModel by viewModels()

    private var restartStarAnimator: ObjectAnimator? = null
    private var isRestartBarShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPluginStoreBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.statusBarColor = getColor(R.color.store_night)

        if (savedInstanceState == null)
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                replace(R.id.storeContainer, StoreHomeFragment())
            }

        binding.restartAction.setOnClickListener {
            showMockPeek(getString(R.string.plugin_store_mock_restart))
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.pendingRestart.collect(::updateRestartBar)
            }
        }
    }

    fun openDetail(pluginId: String, sharedView: View?) {
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            val name = sharedView?.transitionName
            if (sharedView != null && name != null)
                addSharedElement(sharedView, name)
            else
                setCustomAnimations(R.anim.store_enter, R.anim.store_exit, R.anim.store_pop_enter, R.anim.store_pop_exit)
            replace(R.id.storeContainer, StoreDetailFragment.newInstance(pluginId, name))
            addToBackStack(pluginId)
        }
    }

    fun requestInstall(plugin: StorePlugin) {
        val index = (viewModel.state.value as? StoreLoadState.Loaded)?.index ?: return
        val missing = viewModel.missingDependencies(plugin)
        if (missing.isEmpty()) {
            viewModel.install(listOf(plugin.id))
            return
        }
        val (available, unavailable) = missing.partition { index.find(it) != null }
        val message = buildString {
            append(getString(
                R.string.plugin_store_deps_message,
                plugin.name,
                available.joinToString("\n") { "✦ " + index.find(it)!!.name }
            ))
            if (unavailable.isNotEmpty())
                append("\n\n").append(getString(R.string.plugin_store_deps_missing, unavailable.joinToString()))
        }
        MaterialDialog(this, BottomSheet(LayoutMode.WRAP_CONTENT)).show {
            setCommonAttrsWithLifecycle()
            title(res = R.string.plugin_store_deps_title)
            message(text = message)
            positiveButton(res = R.string.plugin_store_deps_install_all) {
                viewModel.install(available + plugin.id)
            }
            negativeButton(res = R.string.cancel)
        }
    }

    fun burst(view: View) =
        binding.sparkles.burst(view)

    fun showMockPeek(text: String) =
        createSimplePeek(text = text) {
            position = PeekAlert.Position.Top
            iconRes = R.drawable.ic_store_sparkle
            iconTint(res = R.color.store_gold)
            backgroundColor(res = R.color.background_popup)
        }.peek()

    private fun updateRestartBar(count: Int) {
        val bar = binding.restartBar
        if (count > 0)
            binding.restartText.text = getString(R.string.plugin_store_restart_pending, count)
        when {
            count > 0 && !isRestartBarShown -> {
                isRestartBarShown = true
                bar.visibility = View.VISIBLE
                bar.translationY = bar.height + bar.marginBottom + 80f
                bar.animate()
                    .translationY(0f)
                    .setInterpolator(OvershootInterpolator(1.1f))
                    .setDuration(520)
                    .start()
                restartStarAnimator = ObjectAnimator.ofFloat(binding.restartStar, View.ROTATION, 0f, 360f).apply {
                    duration = 6000
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = LinearInterpolator()
                    start()
                }
            }
            count > 0 -> {
                binding.restartStar.animate().cancel()
                binding.restartStar.scaleX = 1.5f
                binding.restartStar.scaleY = 1.5f
                binding.restartStar.animate().scaleX(1f).scaleY(1f).setDuration(400).start()
            }
            isRestartBarShown -> {
                isRestartBarShown = false
                restartStarAnimator?.cancel()
                bar.animate()
                    .translationY(bar.height + bar.marginBottom + 80f)
                    .setDuration(260)
                    .withEndAction { bar.visibility = View.INVISIBLE }
                    .start()
            }
        }
    }

    override fun onDestroy() {
        restartStarAnimator?.cancel()
        super.onDestroy()
    }
}
