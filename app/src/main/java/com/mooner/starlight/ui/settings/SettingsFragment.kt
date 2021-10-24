package com.mooner.starlight.ui.settings

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.mooner.starlight.R
import com.mooner.starlight.databinding.FragmentSettingsBinding
import com.mooner.starlight.plugincore.config.CategoryConfigObject
import com.mooner.starlight.plugincore.config.config
import com.mooner.starlight.plugincore.models.TypedString
import com.mooner.starlight.plugincore.utils.Icon
import com.mooner.starlight.ui.config.ParentAdapter

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val settings: List<CategoryConfigObject> = config {
        category {
            id = "general"
            title = "일반"
            textColor = color { "#706EB9" }
            items = items {
                toggle {
                    id = "dummy"
                    name = "나츠이로 마츠리"
                    icon = Icon.NIGHTS_STAY
                    defaultValue = false
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)

        val recyclerAdapter = ParentAdapter(requireContext()) { parentId, id, view, data ->

        }.apply {
            data = settings
            saved = mutableMapOf()
            notifyDataSetChanged()
        }

        binding.configRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = recyclerAdapter
        }

        return binding.root
    }
}