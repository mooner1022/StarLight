package com.mooner.starlight.ui.debugroom

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.mooner.starlight.R
import com.mooner.starlight.models.DebugRoomMessage
import com.skydoves.needs.textForm

class DebugRoomChatAdapter(
    val context: Context,
    private val chatList: ArrayList<DebugRoomMessage>
) : RecyclerView.Adapter<DebugRoomChatAdapter.ViewHolder>() {

    companion object {
        const val CHAT_SELF = 0
        const val CHAT_BOT = 1
        const val CHAT_SELF_LONG = 2
        const val CHAT_BOT_LONG = 3
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        var view: View? = null
        when (viewType) {
            CHAT_SELF -> {
                view = LayoutInflater.from(context).inflate(R.layout.chat_self, parent, false)
            }
            CHAT_BOT -> {
                view = LayoutInflater.from(context).inflate(R.layout.chat_other, parent, false)
            }
            CHAT_SELF_LONG -> {
                view = LayoutInflater.from(context).inflate(R.layout.chat_self_long, parent, false)
            }
            CHAT_BOT_LONG -> {
                view = LayoutInflater.from(context).inflate(R.layout.chat_other_long, parent, false)
            }
        }
        return ViewHolder(view!!, viewType)
    }

    override fun getItemCount(): Int {
        return chatList.size
    }

    override fun getItemViewType(position: Int): Int {
        return chatList[position].viewType
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val messageData = chatList[position]

        when (messageData.viewType) {
            CHAT_SELF -> {
                holder.message.text = messageData.message
            }
            CHAT_BOT -> {
                holder.sender.text = "BOT"
                holder.message.text = messageData.message
                holder.profileImage.setImageResource(R.drawable.default_profile)
            }
            CHAT_SELF_LONG -> {
                holder.message.text = messageData.message
                    .substring(0..500)
                    .replace("\u200B", "") + "..."
                holder.showAllButton.setOnClickListener {
                    MaterialDialog(context, BottomSheet(LayoutMode.WRAP_CONTENT)).show {
                        cornerRadius(25f)
                        cancelOnTouchOutside(true)
                        noAutoDismiss()
                        title(text = context.getString(R.string.title_show_all))
                        message(text = messageData.message)
                        positiveButton(text = context.getString(R.string.close)) {
                            dismiss()
                        }
                    }
                }
            }
            CHAT_BOT_LONG -> {
                holder.sender.text = "BOT"
                holder.message.text = messageData.message
                    .substring(0..500)
                    .replace("\u200B", "") + "..."
                holder.profileImage.setImageResource(R.drawable.default_profile)
                holder.showAllButton.setOnClickListener {
                    MaterialDialog(context, BottomSheet(LayoutMode.WRAP_CONTENT)).show {
                        cornerRadius(25f)
                        cancelOnTouchOutside(true)
                        noAutoDismiss()
                        title(text = context.getString(R.string.title_show_all))
                        message(text = messageData.message)
                        positiveButton(text = context.getString(R.string.close)) {
                            dismiss()
                        }
                    }
                }
            }
        }
    }

    inner class ViewHolder(itemView: View, viewType: Int) : RecyclerView.ViewHolder(itemView) {
        lateinit var sender: TextView
        lateinit var message: TextView
        lateinit var profileImage: ImageView
        lateinit var text: TextView
        lateinit var showAllButton: Button

        init {
            when (viewType) {
                CHAT_SELF -> {
                    message = itemView.findViewById(R.id.message)
                    //text = itemView.findViewById(R.id.text)
                }
                CHAT_BOT -> {
                    sender = itemView.findViewById(R.id.sender)
                    message = itemView.findViewById(R.id.message)
                    profileImage = itemView.findViewById(R.id.profile)
                    //text = itemView.findViewById(R.id.text)
                }
                CHAT_SELF_LONG -> {
                    message = itemView.findViewById(R.id.message)
                    showAllButton = itemView.findViewById(R.id.buttonShowAll)
                }
                CHAT_BOT_LONG -> {
                    sender = itemView.findViewById(R.id.sender)
                    message = itemView.findViewById(R.id.message)
                    profileImage = itemView.findViewById(R.id.profile)
                    showAllButton = itemView.findViewById(R.id.buttonShowAll)
                }
            }
        }
    }
}