package com.mooner.starlight.ui.debugroom

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mooner.starlight.R
import com.mooner.starlight.models.Message

class DebugRoomChatAdapter(val context : Context, private val chatList : ArrayList<Message>) : RecyclerView.Adapter<DebugRoomChatAdapter.ViewHolder>() {
    val CHAT_SELF = 0
    val CHAT_BOT = 1
    //private var botProfileImage: Bitmap

    init {
        //val img = Base64.decode(profileBase64, Base64.DEFAULT)
        //botProfileImage = BitmapFactory.decodeByteArray(img, 0, img.size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        Log.d("chatlist size", chatList.size.toString())
        var view: View? = null
        when (viewType) {
            CHAT_SELF -> {
                view = LayoutInflater.from(context).inflate(R.layout.chat_self, parent, false)
            }
            CHAT_BOT -> {
                view = LayoutInflater.from(context).inflate(R.layout.chat_other, parent, false)
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

        }
    }

    inner class ViewHolder(itemView: View, viewType: Int) : RecyclerView.ViewHolder(itemView) {
        lateinit var sender: TextView
        lateinit var message: TextView
        lateinit var profileImage: ImageView
        lateinit var text: TextView

        init {
            when (viewType) {
                CHAT_SELF -> {
                    message = itemView.findViewById(R.id.message)
                    message.text = "TextView"
                    //text = itemView.findViewById(R.id.text)
                }
                CHAT_BOT -> {
                    sender = itemView.findViewById(R.id.sender)
                    message = itemView.findViewById(R.id.message)
                    profileImage = itemView.findViewById(R.id.profile)
                    //text = itemView.findViewById(R.id.text)
                }
            }
        }
    }
}