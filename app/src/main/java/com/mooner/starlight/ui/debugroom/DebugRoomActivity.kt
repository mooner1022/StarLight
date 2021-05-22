package com.mooner.starlight.ui.debugroom

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.mooner.starlight.databinding.ActivityDebugRoomBinding
import com.mooner.starlight.models.Message
import com.mooner.starlight.plugincore.Session
import com.mooner.starlight.plugincore.project.Project
import com.mooner.starlight.plugincore.project.Replier

class DebugRoomActivity : AppCompatActivity() {
    private val chatList: ArrayList<Message> = arrayListOf()
    private lateinit var userChatAdapter: DebugRoomChatAdapter
    private lateinit var roomName:String
    private lateinit var sender: String
    private var imageHash: Int = 0
    private lateinit var project: Project
    private lateinit var binding: ActivityDebugRoomBinding
    private var lastRoom: String? = null
    private val replier = object : Replier {
        override fun reply(msg: String) {
            if (lastRoom != null) {
                Message(
                    msg,
                    lastRoom!!,
                    1
                )
            }
        }

        override fun reply(room: String, msg: String) {
            Message(
                msg,
                room,
                1
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDebugRoomBinding.inflate(layoutInflater)
        setContentView(binding.root)

        imageHash = intent.getIntExtra("imageHash", 0)
        sender = intent.getStringExtra("sender").toString()
        roomName = intent.getStringExtra("roomName").toString()
        println("roomName= $roomName")
        binding.roomTitle.text = roomName

        project = Session.getProjectLoader().getProject(roomName)!!

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
        addMessage(
            Message(
                message = message,
                roomName = roomName,
                viewType = 0
            )
        )
        project.callEvent("response", arrayOf(roomName, message, sender, imageHash, replier))
    }

    private fun addMessage(message: Message) {
        runOnUiThread {
            chatList.add(message)
            userChatAdapter.notifyItemInserted(chatList.size)
            binding.messageInput.setText("")
            binding.chatRecyclerView.scrollToPosition(chatList.size - 1)
        }
    }
}