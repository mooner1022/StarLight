/*
 * EditorMessageFragment.kt created by Minki Moon(mooner1022) on 23. 1. 12. 오후 8:39
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.editor.drawer

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import dev.mooner.starlight.databinding.FragmentEditorMessageBinding
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.event.Event
import dev.mooner.starlight.plugincore.event.EventHandler
import dev.mooner.starlight.plugincore.event.eventHandlerScope
import dev.mooner.starlight.plugincore.event.on
import dev.mooner.starlight.ui.editor.DefaultEditorActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

class EditorMessageFragment : Fragment() {

    private var _binding: FragmentEditorMessageBinding? = null
    private val binding get() = _binding!!

    private lateinit var rvAdapter: ItemAdapter<EditorAnnotationItem>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditorMessageBinding.inflate(inflater, container, false)

        rvAdapter = ItemAdapter()
        val fastAdapter = FastAdapter.with(rvAdapter)
        binding.rvEditorMessage.apply {
            layoutManager = LinearLayoutManager(activity)
            adapter = fastAdapter
        }
        fastAdapter.onClickListener = { _, _, item, _ ->
            val annotation = item.annotation
            (requireActivity() as DefaultEditorActivity).apply {
                gotoLine(annotation.row + 1, annotation.column)
                closeDrawer(Gravity.START, true)
            }
            true
        }

        EventHandler.on(lifecycleScope, ::onEditorAnnotationReturn)

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as DefaultEditorActivity).requestAnnotations()
    }

    private fun onEditorAnnotationReturn(event: EditorAnnotationReturnEvent) {
        lifecycleScope.launch(Dispatchers.Main) {
            val annotations: List<Annotation> = Session.json.decodeFromString(event.jsonString)
            rvAdapter.clear()
            rvAdapter.set(annotations.map(EditorAnnotationItem::withAnnotation))
            (requireParentFragment() as FileTreeDrawerFragment).updateMessageCount(annotations.size)
            if (annotations.isNotEmpty())
                binding.boxNoErrorYet.visibility = View.GONE
            else
                binding.boxNoErrorYet.visibility = View.VISIBLE
        }
    }

    @Serializable
    internal data class Annotation(
        val row: Int,
        val column: Int,
        val text: String,
        val type: String,
        val raw: String
    )

    internal data class EditorAnnotationReturnEvent(
        val jsonString: String,
        val coroutineScope: CoroutineScope = eventHandlerScope()
    ): Event, CoroutineScope by coroutineScope
}