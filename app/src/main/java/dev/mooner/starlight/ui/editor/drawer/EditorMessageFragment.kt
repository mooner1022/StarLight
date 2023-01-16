/*
 * EditorMessageFragment.kt created by Minki Moon(mooner1022) on 23. 1. 12. 오후 8:39
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.editor.drawer

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.FragmentEditorMessageBinding

class EditorMessageFragment : Fragment() {

    private var _binding: FragmentEditorMessageBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditorMessageBinding.inflate(inflater, container, false)

        return binding.root
    }
}