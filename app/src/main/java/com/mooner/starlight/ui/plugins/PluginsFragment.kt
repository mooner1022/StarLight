package com.mooner.starlight.ui.plugins

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.mooner.starlight.MainActivity
import com.mooner.starlight.R
import com.mooner.starlight.core.ApplicationSession
import com.mooner.starlight.databinding.FragmentPluginsBinding
import com.mooner.starlight.utils.Utils
import jp.wasabeef.recyclerview.animators.SlideInLeftAnimator

class PluginsFragment : Fragment() {
    private var _binding: FragmentPluginsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPluginsBinding.inflate(inflater, container, false)
        val plugins = ApplicationSession.plugins

        MainActivity.setToolbarText("Plugins")
        MainActivity.reloadText(
            Utils.formatStringRes(
                R.string.subtitle_plugins,
                mapOf(
                    "count" to plugins.size.toString()
                )
            )
        )

        val adapter = PluginsListAdapter(requireContext())
        adapter.data = plugins
        val layoutManager = LinearLayoutManager(requireContext())
        with(binding.recyclerViewProjectList) {
            this.adapter = adapter
            this.layoutManager = layoutManager
            this.itemAnimator = SlideInLeftAnimator()
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}