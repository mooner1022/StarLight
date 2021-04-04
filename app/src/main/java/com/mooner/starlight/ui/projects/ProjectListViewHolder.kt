package com.mooner.starlight.ui.projects

import android.content.Intent
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.alespero.expandablecardview.ExpandableCardView
import com.google.android.material.snackbar.Snackbar
import com.mooner.starlight.MainActivity
import com.mooner.starlight.R
import com.mooner.starlight.core.ApplicationSession.projectLoader
import com.mooner.starlight.ui.debugroom.DebugRoomActivity
import com.mooner.starlight.ui.editor.EditorActivity
import kotlinx.android.synthetic.main.card_project_inner.view.*
import kotlinx.android.synthetic.main.card_project_list.view.*

class ProjectListViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
    private val expandable = itemView.findViewById<ExpandableCardView>(R.id.card_project)

    fun bind(cardData: ProjectCardData) {
        itemView.cardViewIsEnabled.setBackgroundColor(itemView.context.getColor(if (cardData.isEnabled) R.color.card_enabled else R.color.card_disabled))

        val langIcon = cardData.language.icon
        expandable.setIcon(langIcon)
        expandable.setTitle(cardData.name)

        itemView.buttonEditCode.setOnClickListener {
            val intent = Intent(it.context, EditorActivity::class.java).apply {
                putExtra("code",cardData.fileDir.readText(Charsets.UTF_8))
                putExtra("fileDir", cardData.fileDir.path)
                putExtra("title", cardData.name)
            }
            it.context.startActivity(intent)
        }

        itemView.buttonRecompile.setOnClickListener {
            projectLoader.getProject(cardData.name)?.recompile()
            Snackbar.make(it, "${cardData.name}의 컴파일을 완료했어요!", Snackbar.LENGTH_LONG).show()
        }

        itemView.buttonDebugRoom.setOnClickListener {
            it.context.startActivity(Intent(it.context, DebugRoomActivity::class.java).apply { putExtra("roomName", cardData.name) })
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