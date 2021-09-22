package com.mooner.starlight.ui.debugroom

import android.os.Bundle
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.mooner.starlight.databinding.ActivityDebugRoomBinding
import com.mooner.starlight.models.DebugRoomMessage
import com.mooner.starlight.plugincore.core.Session
import com.mooner.starlight.plugincore.models.ChatSender
import com.mooner.starlight.plugincore.models.DebugChatRoom
import com.mooner.starlight.plugincore.models.Message
import com.mooner.starlight.plugincore.project.Project

class DebugRoomActivity: AppCompatActivity() {
    private val chatList: ArrayList<DebugRoomMessage> = arrayListOf()
    private lateinit var userChatAdapter: DebugRoomChatAdapter
    private lateinit var roomName: String
    private lateinit var sender: String
    private lateinit var project: Project
    private lateinit var binding: ActivityDebugRoomBinding
    private var imageHash: Int = 0

    private val onSend: (msg: String) -> Unit = { msg ->
        val viewType = if (msg.length >= 500)
            DebugRoomChatAdapter.CHAT_BOT_LONG
        else
            DebugRoomChatAdapter.CHAT_BOT
        addMessage(msg, viewType)
    }
    private val onMarkAsRead: () -> Unit = {
        Snackbar.make(binding.root, "읽음 처리 호출됨", Snackbar.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDebugRoomBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.messageInput.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                send(binding.messageInput.text.toString())
            }
            false
        }

        imageHash = intent.getIntExtra("imageHash", 0)
        sender = intent.getStringExtra("sender")?: "debug_sender"
        roomName = intent.getStringExtra("roomName")?: "ERROR"
        binding.roomTitle.text = roomName

        project = Session.projectManager.getProject(roomName)!!

        userChatAdapter = DebugRoomChatAdapter(this, chatList)
        binding.chatRecyclerView.adapter = userChatAdapter

        val layoutManager = LinearLayoutManager(this)
        binding.chatRecyclerView.layoutManager = layoutManager

        binding.sendButton.setOnClickListener {
            if (binding.messageInput.text.toString().isBlank()) return@setOnClickListener
            send(binding.messageInput.text.toString())
            binding.messageInput.setText("")
        }

        binding.leave.setOnClickListener {
            finish()
        }
    }

    private fun send(message: String) {
        addMessage(message,
            if (message.length >= 500)
                DebugRoomChatAdapter.CHAT_SELF_LONG
            else
                DebugRoomChatAdapter.CHAT_SELF
        )
        val data = Message(
            message = message,
            sender = ChatSender(
                name = sender,
                profileBase64 = "",
                profileHash = imageHash
            ),
            room = DebugChatRoom(
                name = roomName,
                isGroupChat = false,
                onSend = onSend,
                onMarkAsRead = onMarkAsRead
            ),
            packageName = "com.kakao.talk"
        )
        project.callEvent(
            name = "onMessage",
            args = arrayOf(data)
        )
    }

    private fun addMessage(msg: String, viewType: Int) {
        runOnUiThread {
            chatList.add(
                DebugRoomMessage(
                    message = msg,
                    viewType = viewType
                )
            )
            userChatAdapter.notifyItemInserted(chatList.size)
            binding.messageInput.setText("")
            binding.chatRecyclerView.scrollToPosition(chatList.size - 1)
        }
    }
}