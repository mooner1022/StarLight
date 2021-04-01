package com.mooner.starlight.ui.projects

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.alespero.expandablecardview.ExpandableCardView
import com.mooner.starlight.R
import com.mooner.starlight.plugincore.language.Languages
import com.mooner.starlight.ui.editor.EditorActivity
import kotlinx.android.synthetic.main.card_project_inner.view.*
import kotlinx.android.synthetic.main.card_project_list.view.*

class ProjectListViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
    private val expandable = itemView.findViewById<ExpandableCardView>(R.id.card_project)

    fun bind(cardData: ProjectCardData) {
        val langIcon = when(cardData.language) {
            Languages.JS_RHINO -> resize(R.drawable.ic_js)
            Languages.JS_V8 -> resize(R.drawable.ic_nodejs)
            Languages.PYTHON -> resize(R.drawable.ic_python)
            Languages.CUSTOM -> resize(R.drawable.ic_python)
        }
        expandable.setIcon(langIcon)
        expandable.setTitle(cardData.name)

        itemView.card_project.setOnExpandedListener { v, isExpanded ->
            v.buttonEditCode.setOnClickListener {
                val intent = Intent(v.context, EditorActivity::class.java).apply {
                    putExtra("code",cardData.fileDir.readText(Charsets.UTF_8))
                    putExtra("fileDir", cardData.fileDir.path)
                }
                itemView.context.startActivity(intent)
            }
        }
    }

    private fun resize(image: Int): Drawable {
        val b = (ContextCompat.getDrawable(itemView.context, image) as BitmapDrawable).bitmap
        val bitmapResized = Bitmap.createScaledBitmap(b, 24, 24, false)
        return BitmapDrawable(itemView.context.resources, bitmapResized)
    }
}