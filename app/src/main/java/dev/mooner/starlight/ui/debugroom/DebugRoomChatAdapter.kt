/*
 * DebugRoomChatAdapter.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.debugroom

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.google.android.material.snackbar.Snackbar
import dev.mooner.starlight.R
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.ui.debugroom.models.DebugRoomMessage
import java.io.File

class DebugRoomChatAdapter(
    private val parent: Activity,
    private val dir: File,
    private val chatList: MutableList<DebugRoomMessage>
) : RecyclerView.Adapter<DebugRoomChatAdapter.ViewHolder>() {

    companion object {
        const val CHAT_SELF = 0
        const val CHAT_BOT = 1
        const val CHAT_SELF_LONG = 2
        const val CHAT_BOT_LONG = 3
    }

    private val context = parent as Context

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
                val chatColor = GlobalConfig.category("d_user").getInt("chat_color")
                holder.message.backgroundTintList = chatColor?.let(ColorStateList::valueOf)
            }
            CHAT_SELF_LONG -> {
                holder.message.text = messageData.message + "..."
                holder.showAllButton.setOnClickListener {
                    val fullMessage = dir.resolve("chats").resolve(messageData.fileName!!).readText()
                    showFullMessageDialog(fullMessage)
                }
                val chatColor = GlobalConfig.category("d_user").getInt("chat_color")
                holder.message.backgroundTintList = chatColor?.let(ColorStateList::valueOf)
            }
            CHAT_BOT, CHAT_BOT_LONG -> {
                val chatColor = GlobalConfig.category("d_bot").getInt("chat_color")

                if (isSentAgain) {
                    holder.sender.visibility = View.GONE
                    holder.profileImage.visibility = View.INVISIBLE
                } else {
                    holder.sender.visibility = View.VISIBLE
                    holder.profileImage.visibility = View.VISIBLE
                    holder.sender.text = messageData.sender

                    holder.profileImage.apply {
                        val profileFilePath = GlobalConfig.category("d_bot").getString("profile_image_path")
                        if (profileFilePath != null) {
                            runCatching {
                                load(File(profileFilePath)) {
                                    transformations(RoundedCornersTransformation(resources.getDimension(R.dimen.debugroom_profile_corner_radius)))
                                }
                                colorFilter = null
                            }.onFailure { e ->
                                Toast.makeText(context, "프로필 사진 '$profileFilePath'를 불러오지 못했어요: $e", Toast.LENGTH_LONG).show()
                                GlobalConfig.edit {
                                    category("d_bot").remove("profile_image_path")
                                }

                                load(R.drawable.default_profile)
                                setColorFilter(chatColor ?: ContextCompat.getColor(context, R.color.main_purple))
                            }
                        } else {
                            load(R.drawable.default_profile)
                            setColorFilter(chatColor ?: ContextCompat.getColor(context, R.color.main_purple))
                        }
                    }
                }

                if (messageData.viewType == CHAT_BOT) {
                    holder.message.backgroundTintList = chatColor?.let(ColorStateList::valueOf)
                    holder.message.text = messageData.message
                } else {
                    holder.backgroundLayout.backgroundTintList = chatColor?.let(ColorStateList::valueOf)
                    holder.message.text = messageData.message + "..."
                    val fullMessage = dir.resolve("chats").resolve(messageData.fileName!!).readText()
                    holder.showAllButton.setOnClickListener {
                        showFullMessageDialog(fullMessage)
                    }
                }
            }
        }

        holder.message.setOnLongClickListener {
            val clip = ClipData.newPlainText("디버그룸 채팅", holder.message.text)
            clipboard.setPrimaryClip(clip)
            Snackbar.make(it, "텍스트를 클립보드에 복사했어요.", Snackbar.LENGTH_SHORT).show()
            true
        }
    }

    private fun showFullMessageDialog(fullMessage: String) {
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

    inner class ViewHolder(itemView: View, viewType: Int) : RecyclerView.ViewHolder(itemView) {

        lateinit var sender: TextView
        lateinit var message: TextView
        lateinit var profileImage: ImageView
        lateinit var showAllButton: Button
        lateinit var backgroundLayout: LinearLayout

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
                    backgroundLayout = itemView.findViewById(R.id.linearLayout)
                }
            }
        }
    }
}