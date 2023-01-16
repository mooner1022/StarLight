/*
 * DebugRoomActivity.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.debugroom

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.lifecycle.lifecycleOwner
import com.github.dhaval2404.imagepicker.ImagePicker
import com.google.android.material.bottomsheet.BottomSheetBehavior.*
import com.google.android.material.snackbar.Snackbar
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.ActivityDebugRoomBinding
import dev.mooner.starlight.listener.ProjectOnMessageEvent
import dev.mooner.starlight.listener.legacy.ImageDB
import dev.mooner.starlight.listener.legacy.LegacyEvent
import dev.mooner.starlight.listener.legacy.Replier
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.Session.json
import dev.mooner.starlight.plugincore.chat.ChatSender
import dev.mooner.starlight.plugincore.chat.DebugChatRoom
import dev.mooner.starlight.plugincore.chat.Message
import dev.mooner.starlight.plugincore.config.ColorPickerConfigObject
import dev.mooner.starlight.plugincore.config.ConfigStructure
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.config.config
import dev.mooner.starlight.plugincore.logger.Logger
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.utils.Icon
import dev.mooner.starlight.plugincore.utils.color
import dev.mooner.starlight.plugincore.utils.fireEvent
import dev.mooner.starlight.ui.config.ConfigAdapter
import dev.mooner.starlight.ui.debugroom.DebugRoomChatAdapter.Companion.CHAT_SELF
import dev.mooner.starlight.ui.debugroom.DebugRoomChatAdapter.Companion.CHAT_SELF_LONG
import dev.mooner.starlight.ui.debugroom.models.DebugRoomMessage
import dev.mooner.starlight.utils.PACKAGE_KAKAO_TALK
import dev.mooner.starlight.utils.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.util.*

class DebugRoomActivity: AppCompatActivity() {

    companion object {
        private const val RESULT_BOT      = 0x0
        private const val RESULT_USER     = 0x1

        private const val CHATS_FILE_NAME = "chats.json"

        private val mentionRegex = "<@(.*?)>".toRegex()
    }

    private val mutex: Mutex by lazy { Mutex(locked = false) }
    private lateinit var chatList: MutableList<DebugRoomMessage>
    private var userChatAdapter: DebugRoomChatAdapter? = null
    val dir: File by lazy {
        val directory = project.directory.resolve("debugroom")
        if (!directory.exists()) directory.mkdirs()
        directory
    }

    private var roomName    = "undefined"
    private var sender      = "debug_sender"
    private var botName     = "BOT"
    private var sentPackage = PACKAGE_KAKAO_TALK
    private var isGroupChat = false
    private var sendOnEnter = false
    private var senderChatColor = color { "#F6946E" }
    private var botChatColor    = color { "#706EB9" }

    //private lateinit var botProfileBitmap: Bitmap
    private lateinit var selfProfileBitmap: Bitmap

    private var bottomSheetState: Int = STATE_COLLAPSED

    private lateinit var project: Project
    private lateinit var binding: ActivityDebugRoomBinding
    private var configAdapter: ConfigAdapter? = null
    private var imageHash: Int = 0

    private val onSend: (msg: String) -> Unit = { msg ->
        val viewType = if (msg.length >= 500)
            DebugRoomChatAdapter.CHAT_BOT_LONG
        else
            DebugRoomChatAdapter.CHAT_BOT
        addMessage(botName, msg, viewType, false)
    }
    private val onMarkAsRead: () -> Unit = {
        Snackbar.make(binding.root, "읽음 처리 호출됨", Snackbar.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDebugRoomBinding.inflate(layoutInflater)
        setContentView(binding.root)
        /*
        imageHash = intent.getIntExtra("imageHash", 0)
        sender = intent.getStringExtra("sender")?: "debug_sender"
         */
        imageHash = 0
        val projectName = intent.getStringExtra("projectName")?: "undefined"
        binding.roomTitle.text = if (roomName == "undefined") projectName else roomName

        Session.projectManager.getProject(projectName).let {
            if (it == null) {
                Toast.makeText(applicationContext, "프로젝트 '${projectName}'을 찾을 수 없어요 :(", Toast.LENGTH_LONG).show()
                finish()
                return
            }
            project = it
        }

        updateConfig()

        val selfProfilePath = GlobalConfig.category("d_user").getString("profile_image_path")
        selfProfileBitmap = if (selfProfilePath == null) {
            loadBitmapFromResource(R.drawable.default_profile)
        } else {
            runCatching {
                loadBitmapFromFile(File(selfProfilePath))
            }.onFailure {
                Toast.makeText(this, "프로필 사진 '$selfProfilePath'를 찾을 수 없어 기본 프로필로 대체했어요.", Toast.LENGTH_LONG).show()
                GlobalConfig.edit {
                    category("d_user").remove("profile_image_path")
                }
            }.getOrElse {
                loadBitmapFromResource(R.drawable.default_profile)
            }
        }

        /*
        val botProfilePath = globalConfig.getCategory("d_bot").getString("profile_image_path")
        botProfileBitmap = if (botProfilePath == null) {
            loadBitmapFromResource(R.drawable.default_profile)
        } else {
            loadBitmapFromFile(File(botProfilePath))
        }
         */

        val listFile = dir.resolve(CHATS_FILE_NAME)
        chatList = if (listFile.exists() && listFile.isFile)
            json.decodeFromString(listFile.readText())
        else
            mutableListOf()

        userChatAdapter = DebugRoomChatAdapter(this, dir, chatList)

        binding.chatRecyclerView.apply {
            adapter = userChatAdapter
            layoutManager = LinearLayoutManager(this@DebugRoomActivity)
            if (chatList.isNotEmpty()) {
                scrollToPosition(chatList.size - 1)
            }
        }

        binding.sendButton.setOnClickListener {
            if (binding.messageInput.text.toString().isBlank()) return@setOnClickListener
            send(binding.messageInput.text.toString())
            binding.messageInput.setText("")
        }

        from(binding.bottomSheet.root).addBottomSheetCallback(object : BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                bottomSheetState = newState
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })

        configAdapter = ConfigAdapter.Builder(this) {
            bind(binding.bottomSheet.configRecyclerView)
            structure(::getStructure)
            savedData(GlobalConfig.getAllConfigs())
            onConfigChanged { parentId, id, _, data ->
                GlobalConfig.edit {
                    category(parentId).setAny(id, data)
                }
                updateConfig()
            }
        }.build()

        binding.leave.setOnClickListener {
            finish()
        }
    }

    private fun loadBitmapFromResource(@DrawableRes res: Int): Bitmap = BitmapFactory.decodeResource(resources, res)

    private fun loadBitmapFromFile(file: File): Bitmap = Uri.fromFile(file).toBitmap(applicationContext)

    @SuppressLint("CheckResult")
    private fun send(_message: String) {
        val hasMention = mentionRegex.containsMatchIn(_message)
        val message: String = if (hasMention) {
            val mentionedNames = mentionRegex.findAll(_message).map { it.value.drop(2).dropLast(1) }
            var mMsg: String = _message
            for (name in mentionedNames)
                mMsg = mMsg.replace("<@$name>", "@$name")
            mMsg
        } else
            _message

        val viewType = if (message.length >= 500) CHAT_SELF_LONG else CHAT_SELF
        addMessage(sender, message, viewType, true)
        val data = Message(
            message = message,
            sender = ChatSender(
                name = sender,
                profileBitmap = selfProfileBitmap
            ),
            room = DebugChatRoom(
                name = roomName,
                isGroupChat = isGroupChat,
                onSend = onSend,
                onMarkAsRead = onMarkAsRead
            ),
            packageName = sentPackage,
            hasMention = hasMention,
            chatLogId = -1L
        )

        project.fireEvent<ProjectOnMessageEvent>(data, ::showErrorSnackbar)

        if (GlobalConfig.category("legacy").getBoolean("use_legacy_event", false)) {
            val replier = Replier { _, msg, _ ->
                onSend(msg)
                true
            }

            val imageDB = ImageDB(selfProfileBitmap)

            project.fireEvent<LegacyEvent>(roomName, message, sender, isGroupChat, replier, imageDB, ::showErrorSnackbar)
        }
    }

    private fun showErrorSnackbar(e: Exception) {
        Snackbar.make(binding.root, e.toString(), Snackbar.LENGTH_LONG).apply {
            setAction("자세히 보기") {
                MaterialDialog(view.context, BottomSheet(LayoutMode.WRAP_CONTENT)).show {
                    cornerRadius(25f)
                    cancelOnTouchOutside(false)
                    noAutoDismiss()
                    lifecycleOwner(this@DebugRoomActivity)
                    title(text = project.info.name + " 에러 로그")
                    message(text = e.toString() + "\n\nstacktrace:\n" + e.stackTraceToString())
                    positiveButton(text = "닫기") {
                        dismiss()
                    }
                }
            }
        }.show()
    }

    private fun addMessage(sender: String, msg: String, viewType: Int, clearInput: Boolean) {
        chatList += if (msg.length >= 500) {
            val fileName = UUID.randomUUID().toString() + ".chat"
            File(dir, "chats").apply {
                mkdirs()
                resolve(fileName).writeText(msg)
            }
            DebugRoomMessage(
                sender = sender,
                message = msg.substring(0..499).replace("\u200b", ""),
                fileName = fileName,
                viewType = viewType
            )
        } else {
            DebugRoomMessage(
                sender = sender,
                message = msg,
                viewType = viewType
            )
        }
        runOnUiThread {
            userChatAdapter?.notifyItemInserted(chatList.size)
            if (clearInput)
                binding.messageInput.setText("")
            binding.chatRecyclerView.post {
                binding.chatRecyclerView.smoothScrollToPosition(chatList.size - 1)
            }
        }
        CoroutineScope(Dispatchers.IO).launch {
            mutex.lock()
            val encoded = json.encodeToString(chatList.toList())
            File(dir, CHATS_FILE_NAME).writeText(encoded)
            mutex.unlock()
        }
    }

    override fun onDestroy() {
        userChatAdapter = null
        configAdapter?.destroy()
        configAdapter = null
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (bottomSheetState != STATE_COLLAPSED && bottomSheetState != STATE_HIDDEN) {
            from(binding.bottomSheet.root).state = STATE_COLLAPSED
        } else
            super.onBackPressed()
    }

    private fun updateConfig() {
        roomName = GlobalConfig.category("d_room").getString("name", "undefined")
        sender = GlobalConfig.category("d_user").getString("name", "debug_sender")
        botName = GlobalConfig.category("d_bot").getString("name", "BOT")
        sentPackage = GlobalConfig.category("d_room").getString("package", PACKAGE_KAKAO_TALK)
        isGroupChat = GlobalConfig.category("d_room").getBoolean("is_group_chat", false)
        sendOnEnter = GlobalConfig.category("d_room").getBoolean("send_with_enter", false)
        senderChatColor = GlobalConfig.category("d_user").getInt("chat_color", senderChatColor)
        botChatColor = GlobalConfig.category("d_bot").getInt("chat_color", botChatColor)

        runOnUiThread {
            binding.roomTitle.text = roomName
            if (sendOnEnter) {
                binding.messageInput.setOnKeyListener { _, keyCode, event ->
                    if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                        send(binding.messageInput.text.toString())
                        true
                    } else
                        false
                }
            } else {
                binding.messageInput.setOnKeyListener { _, _, _ -> false }
            }
        }
    }

    private fun openImagePicker(resultCode: Int) {
        ImagePicker.with(this@DebugRoomActivity).apply {
            galleryOnly()
            crop(1f, 1f)
            maxResultSize(512, 512)
            saveDir(cacheDir.resolve("DebugRoom"))
            galleryMimeTypes(
                mimeTypes = arrayOf(
                    "image/png",
                    "image/jpg",
                    "image/jpeg"
                )
            )
        }.start(resultCode)
    }

    private fun reloadConfig() = configAdapter?.reload()

    private fun getStructure(): ConfigStructure {
        val defaultColor = getColor(R.color.main_bright)
        return config {
            category {
                id = "d_room"
                title = "일반"
                textColor = defaultColor
                items {
                    string {
                        id = "name"
                        title = "방 이름"
                        icon = Icon.BOOKMARK
                        iconTintColor = defaultColor
                        require = { text ->
                            if (text.isBlank()) "이름을 입력해주세요."
                            else null
                        }
                    }
                    string {
                        id = "package"
                        title = "앱 패키지"
                        icon = Icon.LIST_BULLETED
                        iconTintColor = defaultColor
                        hint = "ex) $PACKAGE_KAKAO_TALK"
                        defaultValue = PACKAGE_KAKAO_TALK
                    }
                    toggle {
                        id = "is_group_chat"
                        title = "isGroupChat"
                        icon = Icon.MARK_CHAT_UNREAD
                        iconTintColor = defaultColor
                        defaultValue = false
                    }
                    toggle {
                        id = "send_with_enter"
                        title = "엔터 키로 전송"
                        icon = Icon.SEND
                        iconTintColor = defaultColor
                        defaultValue = false
                    }
                }
            }
            category {
                id = "d_bot"
                title = "봇"
                textColor = defaultColor
                items {
                    string {
                        id = "name"
                        title = "이름"
                        icon = Icon.BOOKMARK
                        iconTintColor = defaultColor
                        require = { text ->
                            if (text.isBlank()) "이름을 입력해주세요."
                            else null
                        }
                    }
                    button {
                        id = "set_profile_image"
                        title = "프로필 이미지 설정"
                        val profileImagePath = GlobalConfig.category("d_bot").getString("profile_image_path")
                        if (profileImagePath == null) {
                            setIcon(icon = Icon.ACCOUNT_BOX)
                            iconTintColor = defaultColor
                        } else {
                            val file = File(profileImagePath)
                            if (file.exists() && file.isFile) {
                                setIcon(iconFile = file)
                                iconTintColor = null
                            } else {
                                GlobalConfig.edit {
                                    category("d_bot") -= "profile_image_path"
                                }
                                setIcon(icon = Icon.ACCOUNT_BOX)
                                iconTintColor = defaultColor
                            }
                        }
                        setOnClickListener { _ ->
                            openImagePicker(RESULT_BOT)
                        }
                    }
                    colorPicker {
                        id = "chat_color"
                        title = "채팅 말풍선 색 설정"
                        icon = Icon.MARK_CHAT_READ
                        flags = ColorPickerConfigObject.FLAG_CUSTOM_ARGB or ColorPickerConfigObject.FLAG_ALPHA_SELECTOR
                        colors {
                            color(getColor(R.color.main_bright))
                            color(getColor(R.color.main_dark))
                            color(color { "#789395" })
                            color(color { "#b4cfb0" })
                        }
                        defaultSelection = color { "#789395" }
                        setOnColorSelectedListener { _, color ->
                            botChatColor = color
                        }
                    }
                }
            }
            category {
                id = "d_user"
                title = "전송자"
                textColor = defaultColor
                items {
                    string {
                        id = "name"
                        title = "이름"
                        icon = Icon.BOOKMARK
                        iconTintColor = defaultColor
                        require = { text ->
                            if (text.isBlank()) "이름을 입력해주세요."
                            else null
                        }
                    }
                    button {
                        id = "set_profile_image"
                        title = "프로필 이미지 설정"
                        iconTintColor = defaultColor
                        val profileImagePath = GlobalConfig.category("d_user").getString("profile_image_path")
                        if (profileImagePath == null) {
                            setIcon(icon = Icon.ACCOUNT_BOX)
                        } else {
                            val file = File(profileImagePath)
                            if (file.exists() && file.isFile) {
                                setIcon(iconFile = file)
                                iconTintColor = null
                            } else {
                                GlobalConfig.edit {
                                    category("d_user") -= "profile_image_path"
                                }
                                setIcon(icon = Icon.ACCOUNT_BOX)
                            }
                        }
                        setOnClickListener { _ ->
                            openImagePicker(RESULT_USER)
                        }
                    }
                    colorPicker {
                        id = "chat_color"
                        title = "채팅 말풍선 색 설정"
                        icon = Icon.MARK_CHAT_READ
                        iconTintColor = defaultColor
                        flags = ColorPickerConfigObject.FLAG_CUSTOM_ARGB or ColorPickerConfigObject.FLAG_ALPHA_SELECTOR
                        colors {
                            color(getColor(R.color.main_bright))
                            color(getColor(R.color.main_dark))
                            color(color { "#789395" })
                            color(color { "#b4cfb0" })
                        }
                        defaultSelection = color { "#b4cfb0" }
                        setOnColorSelectedListener { _, color ->
                            senderChatColor = color
                        }
                    }
                }
            }
            category {
                id = "d_cautious"
                title = "위험"
                textColor = defaultColor
                items {
                    button {
                        id = "clear_chat"
                        title = "대화 기록 초기화"
                        setOnClickListener { _ ->
                            dir.deleteRecursively()
                            dir.mkdirs()
                            val listSize = chatList.size
                            chatList.clear()
                            userChatAdapter?.notifyItemRangeRemoved(0, listSize)
                        }
                        icon = Icon.LAYERS_CLEAR
                        //backgroundColor = Color.parseColor("#B8DFD8")
                    }
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (resultCode) {
            Activity.RESULT_OK -> {
                val uri: Uri = data?.data!!
                GlobalConfig.edit {
                    val category = when(requestCode) {
                        RESULT_BOT -> category("d_bot")
                        RESULT_USER -> category("d_user")
                        else -> return@edit
                    }
                    category["profile_image_path"] = uri.path!!
                }
                val bitmap = uri.toBitmap(applicationContext)
                when(requestCode) {
                    //RESULT_BOT -> {
                    //    botProfileBitmap = bitmap
                    //}
                    RESULT_USER -> {
                        selfProfileBitmap = bitmap
                    }
                }
                reloadConfig()
            }
            ImagePicker.RESULT_ERROR -> {
                Toast.makeText(this, ImagePicker.getError(data), Toast.LENGTH_SHORT).show()
            }
            else -> {
                Logger.v(this::class.simpleName, "Image select task canceled")
            }
        }
    }
}