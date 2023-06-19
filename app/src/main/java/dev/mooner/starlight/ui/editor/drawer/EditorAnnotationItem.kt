/*
 * EditorAnnotationItem.kt created by Minki Moon(mooner1022) on 6/14/23, 5:58 PM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.editor.drawer

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import com.mikepenz.fastadapter.binding.AbstractBindingItem
import dev.mooner.starlight.databinding.CardEditorAnnotationBinding
import dev.mooner.starlight.plugincore.utils.color

class EditorAnnotationItem private constructor(
    internal var annotation: EditorMessageFragment.Annotation
): AbstractBindingItem<CardEditorAnnotationBinding>() {

    override val type: Int = 0

    @SuppressLint("SetTextI18n")
    override fun bindView(binding: CardEditorAnnotationBinding, payloads: List<Any>) {
        super.bindView(binding, payloads)

        binding.apply {
            tvLineNumber.text        = "${annotation.row + 1}\t:${annotation.column}"
            tvAnnotationMessage.text = annotation.text
            cvTypeIndicator.setCardBackgroundColor(getColorByType(annotation.type))
        }
    }

    override fun createBinding(inflater: LayoutInflater, parent: ViewGroup?): CardEditorAnnotationBinding =
        CardEditorAnnotationBinding.inflate(inflater, parent, false)

    private fun getColorByType(type: String): Int {
        return when(type) {
            "error"   -> color { "#FF6188" }
            "warning" -> color { "#FEC64C" }
            "info"    -> color { "#78dce8" }
            else      -> getColorByType("info")
        }
    }

    companion object {

        internal fun withAnnotation(annotation: EditorMessageFragment.Annotation): EditorAnnotationItem =
            EditorAnnotationItem(annotation)
    }
}