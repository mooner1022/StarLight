package dev.mooner.starlight.ui.editor.drawer

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.GravityCompat
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.FragmentFileTreeDrawerBinding
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.utils.getInternalDirectory
import dev.mooner.starlight.ui.editor.DefaultEditorActivity
import dev.mooner.starlight.utils.toFile
import java.io.File
import kotlin.properties.Delegates.notNull

private val LOG = LoggerFactory.logger {  }

private const val ARG_PROJECT_PATH = "projectPath"

class FileTreeDrawerFragment : Fragment() {

    private var _binding: FragmentFileTreeDrawerBinding? = null
    val binding get() = _binding!!

    private var projectPath: File by notNull()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        projectPath = arguments?.getString(ARG_PROJECT_PATH)?.toFile() ?: getInternalDirectory()
        LOG.verbose { "File tree path: ${projectPath.path}" }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFileTreeDrawerBinding.inflate(inflater, container, false)

        val activity = requireActivity() as DefaultEditorActivity

        val treeAdapter = FileTreeAdapter(activity, projectPath) { file ->
            activity.apply {
                openFile(file)
                closeDrawer(GravityCompat.START, true)
            }
        }

        binding.viewPager.apply {
            adapter = FileTreeViewPagerAdapter(
                fragment = this@FileTreeDrawerFragment,
                fileTreeAdapter = treeAdapter
            )
            isUserInputEnabled = false
            registerOnPageChangeCallback(object : OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    val id = when(position) {
                        0 -> R.id.nav_file_tree
                        1 -> R.id.nav_warnings
                        else -> R.id.nav_file_tree
                    }
                    binding.topMenu.setItemSelected(
                        id = id,
                        isSelected = true,
                        dispatchAction = false
                    )
                }
            })
        }

        setOnTabSelectedListener { id ->
            val index = when(id) {
                R.id.nav_file_tree -> 0
                R.id.nav_warnings -> 1
                else -> 0
            }
            binding.viewPager.setCurrentItem(index, true)
        }

        binding.topMenu.showBadge(R.id.nav_warnings, 9)

        return binding.root
    }

    fun updateMessageCount(count: Int) {
        if (count <= 0)
            binding.topMenu.dismissBadge(R.id.nav_warnings)
        else
            binding.topMenu.showBadge(R.id.nav_warnings, count)
    }

    fun setOnTabSelectedListener(block: (id: Int) -> Unit) =
        binding.topMenu.setOnItemSelectedListener(block)

    companion object {

        @JvmStatic
        fun newInstance(project: Project) =
            FileTreeDrawerFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PROJECT_PATH, project.directory.path)
                }
            }
    }
}