package com.mooner.starlight.ui.debugroom

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.mooner.starlight.R
import com.mooner.starlight.core.ApplicationSession.projectLoader
import com.mooner.starlight.core.ApplicationSession.taskHandler
import com.mooner.starlight.models.Message
import com.mooner.starlight.project.Project
import com.mooner.starlight.project.Replier
import kotlinx.android.synthetic.main.activity_debug_room.*

class DebugRoomActivity : AppCompatActivity() {
    private val chatList: ArrayList<Message> = arrayListOf()
    private lateinit var userChatAdapter: DebugRoomChatAdapter
    private lateinit var roomName:String
    private lateinit var sender: String
    private var imageHash: Int = 0
    private lateinit var project: Project

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug_room)

        imageHash = intent.getIntExtra("imageHash", 0)
        sender = intent.getStringExtra("sender").toString()
        roomName = intent.getStringExtra("roomName").toString()
        println("roomName= $roomName")
        roomTitle.text = roomName

        project = projectLoader.getProject(roomName)!!
        project.bindReplier { room, msg ->
            addMessage(
                Message(
                    msg,
                    room,
                    1
                )
            )
        }

        userChatAdapter = DebugRoomChatAdapter(this, chatList)
        chatRecyclerView.adapter = userChatAdapter

        val layoutManager = LinearLayoutManager(this)
        chatRecyclerView.layoutManager = layoutManager

        sendButton.setOnClickListener {
            if (messageInput.text.toString().isBlank()) return@setOnClickListener
            send(messageInput.text.toString())
            messageInput.setText("")
        }

        leave.setOnClickListener {
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
        project.callEvent("response", arrayOf(roomName, message, sender, imageHash))
    }

    private fun addMessage(message: Message) {
        runOnUiThread {
            chatList.add(message)
            userChatAdapter.notifyItemInserted(chatList.size)
            messageInput.setText("")
            chatRecyclerView.scrollToPosition(chatList.size - 1)
        }
    }
}