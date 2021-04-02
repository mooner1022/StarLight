package com.mooner.starlight.ui.projects

import android.content.Intent
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.alespero.expandablecardview.ExpandableCardView
import com.mooner.starlight.MainActivity
import com.mooner.starlight.R
import com.mooner.starlight.core.ApplicationSession.projectLoader
import com.mooner.starlight.plugincore.language.Languages
import com.mooner.starlight.ui.editor.EditorActivity
import kotlinx.android.synthetic.main.card_project_inner.view.*
import kotlinx.android.synthetic.main.card_project_list.view.*

class ProjectListViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
    private val expandable = itemView.findViewById<ExpandableCardView>(R.id.card_project)

    fun bind(cardData: ProjectCardData) {
        itemView.cardViewIsEnabled.setBackgroundColor(itemView.context.getColor(if (cardData.isEnabled) R.color.card_enabled else R.color.card_disabled))

        val langIcon = when(cardData.language) {
            Languages.JS_RHINO -> R.drawable.ic_js
            Languages.JS_V8 -> R.drawable.ic_nodejs
            Languages.CUSTOM -> R.drawable.ic_python
        }
        expandable.setIcon(langIcon)
        expandable.setTitle(cardData.name)

        itemView.buttonEditCode.setOnClickListener {
            val intent = Intent(it.context, EditorActivity::class.java).apply {
                putExtra("code",cardData.fileDir.readText(Charsets.UTF_8))
                putExtra("fileDir", cardData.fileDir.path)
            }
            it.context.startActivity(intent)
        }

        itemView.switchButton.apply {
            isChecked = cardData.isEnabled
            setOnCheckedChangeListener { _, isChecked ->
                projectLoader.updateProjectConfig(cardData.name) {
                    isEnabled = isChecked
                }
                itemView.cardViewIsEnabled.setBackgroundColor(context.getColor(if (isChecked) R.color.card_enabled else R.color.card_disabled))
                MainActivity.reloadText()
            }
        }
    }
}