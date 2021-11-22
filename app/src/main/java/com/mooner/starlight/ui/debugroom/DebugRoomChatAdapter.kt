package com.mooner.starlight.ui.debugroom

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
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
import com.google.android.material.snackbar.Snackbar
import com.mooner.starlight.R
import com.mooner.starlight.models.DebugRoomMessage


class DebugRoomChatAdapter(
    private val debugRoomActivity: DebugRoomActivity,
    private val chatList: MutableList<DebugRoomMessage>
) : RecyclerView.Adapter<DebugRoomChatAdapter.ViewHolder>() {

    companion object {
        const val CHAT_SELF = 0
        const val CHAT_BOT = 1
        const val CHAT_SELF_LONG = 2
        const val CHAT_BOT_LONG = 3
    }

    private val context = debugRoomActivity as Context

    private val clipboard: ClipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

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
        val isSentAgain: Boolean = chatList.size != 1 && position != 0 && chatList[position - 1].viewType in arrayOf(CHAT_BOT, CHAT_BOT_LONG)

        when (messageData.viewType) {
            CHAT_SELF -> {
                holder.message.text = messageData.message
            }
            CHAT_BOT -> {
                if (isSentAgain) {
                    holder.sender.visibility = View.GONE
                    holder.profileImage.visibility = View.INVISIBLE
                } else {
                    holder.sender.visibility = View.VISIBLE
                    holder.profileImage.visibility = View.VISIBLE
                    holder.sender.text = messageData.sender
                    holder.profileImage.setImageResource(R.drawable.default_profile)
                }
                holder.message.text = messageData.message
            }
            CHAT_SELF_LONG -> {
                holder.message.text = messageData.message + "..."
                holder.showAllButton.setOnClickListener {
                    val fullMessage = debugRoomActivity.dir.resolve("chats").resolve(messageData.fileName!!).readText()
                    MaterialDialog(context, BottomSheet(LayoutMode.WRAP_CONTENT)).show {
                        cornerRadius(25f)
                        cancelOnTouchOutside(true)
                        noAutoDismiss()
                        title(text = context.getString(R.string.title_show_all))
                        message(text = fullMessage)
                        positiveButton(text = context.getString(R.string.close)) {
                            dismiss()
                        }
                    }
                }
            }
            CHAT_BOT_LONG -> {
                if (isSentAgain) {
                    holder.sender.visibility = View.GONE
                    holder.profileImage.visibility = View.INVISIBLE
                } else {
                    holder.sender.visibility = View.VISIBLE
                    holder.profileImage.visibility = View.VISIBLE
                    holder.sender.text = messageData.sender
                    holder.profileImage.setImageResource(R.drawable.default_profile)
                }

                holder.message.text = messageData.message + "..."
                val fullMessage = debugRoomActivity.dir.resolve("chats").resolve(messageData.fileName!!).readText()
                holder.showAllButton.setOnClickListener {
                    MaterialDialog(context, BottomSheet(LayoutMode.WRAP_CONTENT)).show {
                        cornerRadius(25f)
                        cancelOnTouchOutside(true)
                        noAutoDismiss()
                        title(text = context.getString(R.string.title_show_all))
                        message(text = fullMessage)
                        positiveButton(text = context.getString(R.string.close)) {
                            dismiss()
                        }
                    }
                }
            }
        }

        holder.message.setOnLongClickListener {
            val clip = ClipData.newPlainText("copied text", holder.message.text)
            clipboard.setPrimaryClip(clip)
            Snackbar.make(it, "텍스트를 클립보드에 복사했어요.", Snackbar.LENGTH_SHORT).show()
            true
        }
    }

    inner class ViewHolder(itemView: View, viewType: Int) : RecyclerView.ViewHolder(itemView) {

        lateinit var sender: TextView
        lateinit var message: TextView
        lateinit var profileImage: ImageView
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