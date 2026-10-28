package dev.mooner.starlight.ui.editor.drawer

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.google.android.material.tabs.TabLayoutMediator
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.FragmentFileTreeDrawerBinding
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.utils.getStarLightDirectory
import dev.mooner.starlight.ui.editor.DefaultEditorActivity
import java.io.File
import java.util.*
import kotlin.properties.Delegates.notNull

private val logger = LoggerFactory.logger {  }

class FileTreeDrawerFragment : Fragment() {

    private var _binding: FragmentFileTreeDrawerBinding? = null
    val binding get() = _binding!!

    private var projectPath: String by notNull()
    private var projectId: String? = null
    private var mainScript: String? = null

    private val project: Project?
        get() = projectId?.let(UUID::fromString)?.let { id ->
            Session.projectManager.getProjects().find { it.info.id == id }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        projectPath = arguments?.getString(ARG_PROJECT_PATH) ?: getStarLightDirectory().path
        projectId   = arguments?.getString(ARG_PROJECT_ID)
        mainScript  = arguments?.getString(ARG_MAIN_SCRIPT)
        logger.verbose { "File tree path: $projectPath, mainScript: $mainScript" }
    }

    override fun onGetLayoutInflater(savedInstanceState: Bundle?): LayoutInflater =
        editorThemedInflater(super.onGetLayoutInflater(savedInstanceState))

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFileTreeDrawerBinding.inflate(inflater, container, false)

        val pagerAdapter = FileTreeViewPagerAdapter(
            fragment   = this,
            mainScript = mainScript,
            rootPath   = projectPath,
            projectId  = projectId,
        )
        binding.viewPager.apply {
            adapter = pagerAdapter
            isUserInputEnabled = false
            offscreenPageLimit = pagerAdapter.itemCount - 1
        }
        TabLayoutMediator(binding.tabLayout, binding.viewPager, false) { tab, position ->
            tab.text = getString(pagerAdapter.titleAt(position))
        }.attach()

        refreshHeader()
        (activity as? DefaultEditorActivity)?.let {
            updateMessageCount(it.problemCounts)
            applyBackground(it.editorBackground)
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun openTabAt(index: Int) {
        _binding?.viewPager?.setCurrentItem(index, false)
    }

    fun openLogs() {
        val adapter = _binding?.viewPager?.adapter as? FileTreeViewPagerAdapter ?: return
        adapter.logsPosition?.let(::openTabAt)
    }

    fun updateMessageCount(counts: Pair<Int, Int>) {
        val binding = _binding ?: return
        val adapter = binding.viewPager.adapter as? FileTreeViewPagerAdapter ?: return
        val tab = binding.tabLayout.getTabAt(adapter.problemsPosition) ?: return
        val (errors, warnings) = counts
        if (errors + warnings == 0) {
            tab.removeBadge()
            return
        }
        tab.orCreateBadge.apply {
            number = errors + warnings
            backgroundColor = requireContext().getColor(if (errors > 0) R.color.code_orange else R.color.code_yellow)
            badgeTextColor = requireContext().getColor(R.color.white)
        }
    }

    fun applyBackground(color: Int?) {
        val binding = _binding ?: return
        if (color != null)
            binding.root.setBackgroundColor(color)
    }

    fun refreshHeader() {
        val binding = _binding ?: return
        val project = project
        binding.textProjectName.text = project?.info?.name ?: File(projectPath).name
        binding.layoutProjectStatus.isVisible = project != null
        if (project == null)
            return
        val (statusRes, colorRes) = when {
            !project.isCompiled    -> R.string.project_status_not_compiled to R.color.orange
            project.info.isEnabled -> R.string.project_status_running to R.color.card_enabled
            else                   -> R.string.project_status_off to R.color.text_sub
        }
        val context = binding.root.context
        binding.textProjectStatus.text = "${project.getLanguage().name} · ${context.getString(statusRes)}"
        binding.viewStatusDot.backgroundTintList = ColorStateList.valueOf(context.getColor(colorRes))
    }

    fun refreshFiles(currentFile: File?) {
        if (_binding == null) return
        refreshHeader()
        (childFragmentManager.findFragmentByTag("f0") as? FileTreeFragment)?.refresh(currentFile)
    }

    companion object {
        private const val ARG_PROJECT_PATH = "projectPath"
        private const val ARG_PROJECT_ID   = "projectId"
        private const val ARG_MAIN_SCRIPT  = "mainScript"

        @JvmStatic
        fun newInstance(basePath: File, project: Project?) =
            FileTreeDrawerFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PROJECT_PATH, basePath.path)

                    if (project != null) {
                        putString(ARG_PROJECT_ID, project.info.id.toString())
                        putString(ARG_MAIN_SCRIPT, project.info.mainScript)
                    }
                }
            }
    }
}
