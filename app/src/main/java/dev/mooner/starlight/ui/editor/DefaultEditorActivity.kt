package dev.mooner.starlight.ui.editor

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Build
import android.os.Bundle
import android.view.*
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.*
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import com.google.android.material.bottomsheet.BottomSheetBehavior
import dev.mooner.configdsl.Icon
import dev.mooner.configdsl.adapters.ConfigAdapter
import dev.mooner.configdsl.config
import dev.mooner.configdsl.options.seekbar
import dev.mooner.configdsl.options.spinner
import dev.mooner.configdsl.options.toggle
import dev.mooner.neonjs.NeonSyntaxException
import dev.mooner.peekalert.PeekAlert
import dev.mooner.peekalert.PeekAlertBuilder
import dev.mooner.peekalert.createPeekAlert
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.ActivityDefaultEditorBinding
import dev.mooner.starlight.logging.bindLogNotifier
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.Session.json
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.editor.CodeEditorActivity
import dev.mooner.starlight.plugincore.event.EventHandler
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.project.event.ProjectEventManager
import dev.mooner.starlight.plugincore.project.event.getInstance
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.plugincore.utils.color
import dev.mooner.starlight.ui.common.showSheet
import dev.mooner.starlight.ui.debugroom.DebugRoomActivity
import dev.mooner.starlight.ui.debugroom.DebugRoomFragment
import dev.mooner.starlight.ui.editor.drawer.EditorMessageFragment
import dev.mooner.starlight.ui.editor.drawer.FileTreeDrawerFragment
import dev.mooner.starlight.ui.editor.tab.EditorSession
import dev.mooner.starlight.ui.editor.tab.TabItemMoveCallbackListener
import dev.mooner.starlight.ui.editor.tab.TabViewAdapter
import dev.mooner.starlight.utils.*
import jp.wasabeef.recyclerview.animators.FadeInAnimator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.mozilla.javascript.RhinoException
import java.io.File
import kotlin.coroutines.resume
import kotlin.properties.Delegates.notNull

private val logger = LoggerFactory.logger {  }

class DefaultEditorActivity : CodeEditorActivity(), WebviewCallback {

    private lateinit var name     : String
    private lateinit var binding  : ActivityDefaultEditorBinding
    private          var codeView : WebView? = null

    private var theme          : Theme = DEFAULT_THEME

    private var mainScriptName : String by notNull()
    private var mainLanguage   : Language by notNull()
    private val sessions       : MutableList<EditorSession> = arrayListOf()
    private val isCodeUpdated  : Boolean get() = sessions.any { it.isUpdated }
    private var bottomInsetDp  : Int = 0
    @Volatile
    private var isEditorReady  : Boolean = false

    private var tabAdapter     : TabViewAdapter? = null
    private var configAdapter  : ConfigAdapter? = null
    private var fileTreeDrawer : FileTreeDrawerFragment? = null

    private var showFileTree   : Boolean by notNull()
    //private var showDebugChat  : Boolean by notNull()

    private var bypassEscape   : Boolean by notNull()

    private val isHotKeyShown  : Boolean get() =
        GlobalConfig
            .category("e_general")
            .getBoolean("show_hot_keys", true)

    private val rememberTabs   : Boolean get() =
        GlobalConfig
            .category("e_files")
            .getBoolean("remember_tabs", true)

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDefaultEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbarEditor)

        bindLogNotifier { log ->
            !log.message.startsWith("ECOMF: ")
        }

        val project = getProject()

        showFileTree  = intent.getBooleanExtra(EXTRA_SHOW_FILE_TREE, true)
        //showDebugChat = intent.getBooleanExtra(EXTRA_SHOW_DEBUG_CHAT, true)

        name = intent.getStringExtra("title")!!
        supportActionBar!!.apply {
            if (showFileTree) {
                setDisplayHomeAsUpEnabled(true)
                setHomeAsUpIndicator(R.drawable.ic_round_menu_24)
            }

            setDisplayShowHomeEnabled(true)

            title = name
        }

        theme = try {
            GlobalConfig
                .category("e_general")
                .getInt("theme", DEFAULT_THEME.ordinal)
                .let(Theme.entries.toTypedArray()::get)
        } catch (_: Exception) {
            DEFAULT_THEME
        }

        bypassEscape = GlobalConfig
            .category("e_general")
            .getBoolean("bypass_escape", true)

        binding.scrollViewHotKeys.isHorizontalScrollBarEnabled = false
        binding.toolbarEditor.setTitleTextAppearance(this, R.style.EditorTitleTextAppearance)

        // Lock drawer layout open by swipe
        binding.root.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.END)
        if (!showFileTree)
            binding.root.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.START)

        binding.root.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                if (drawerView.id == R.id.drawer_fileTree)
                    fileTreeDrawer?.refreshFiles(currentFile)
            }

            override fun onDrawerStateChanged(newState: Int) {
                if (newState == DrawerLayout.STATE_IDLE)
                    return
                codeView?.clearFocus()
                WindowCompat.getInsetsController(window, window.decorView)
                    .hide(WindowInsetsCompat.Type.ime())
            }
        })

        if (getProject() != null && layoutMode == LAYOUT_TABLET) {
            // Left drawer (Debug Chat)
            //binding.debugChat!!.leave.visibility = View.INVISIBLE
            supportFragmentManager.beginTransaction().apply {
                replace(
                    R.id.fragment_container_debug_room,
                    DebugRoomFragment.newInstance(
                        projectName = getProject()!!.info.name,
                        showLeave = false,
                        fixedPadding = true
                    )
                )
            }.commit()

            binding.buttonCloseDebugChat!!.setOnClickListener {
                binding.root.closeDrawer(GravityCompat.END, true)
            }
        }

        if (showFileTree) {
            //Right drawer (File Tree)
            attachFileTreeDrawer()
        }

        configAdapter = ConfigAdapter.Builder(this) {
            bind(binding.bottomSheet.configRecyclerView)
            structure(::getStructure)
            configData(GlobalConfig.getMutableData())
            onValueUpdated { parentId, id, data, jsonData ->
                GlobalConfig.edit {
                    category(parentId).setRaw(id, jsonData)
                }
                if (parentId == "e_code" && id == "font_size")
                    (data as? Int)?.let(::setFontSize)
            }
        }.build()

        val fileName = project?.info?.mainScript ?: getMainFileName()
        mainScriptName = fileName
        val ext = File(fileName).extension
        val lang = let {
            for (lang in Language.entries) {
                if (ext in lang.fileExt) return@let lang
            }
            return@let null
        } ?: let {
            logger.warn {
                translate {
                    Locale.ENGLISH { "Failed to auto-detect language from file extension: $ext" }
                    Locale.KOREAN  { "파일 확장자로부터 언어를 자동 감지하지 못함: $ext" }
                }
            }
            Language.JAVASCRIPT
        }
        mainLanguage = lang
        logger.verbose {
            translate {
                Locale.ENGLISH { "File extension: $ext, Auto-detected language: $lang" }
                Locale.KOREAN  { "파일 확장자: $ext, 자동으로 인식한 언어: $lang" }
            }
        }

        val code = readCode(fileName) ?: return let {
            Toast.makeText(this, "Unable to locate main script file '${fileName}'", Toast.LENGTH_LONG).show()
            finish()
        }

        val savedTabs: Collection<String> =
            project?.config
                ?.takeIf { rememberTabs }
                ?.category("editor")
                ?.getString("saved_tabs", "[]")
                ?.let { runCatching { json.decodeFromString<MutableList<String>>(it) }.getOrNull() }
                ?.also { if (fileName in it) it -= fileName }
                ?.filter { getBaseDirectory().resolve(it).isFile }
                ?: emptyList()

        addSession(fileName, lang, code)
        for (tab in savedTabs) {
            val language = getLanguageFromFileName(tab, Language.PLAIN_TEXT)
            addSession(tab, language)
        }

        tabAdapter = setupTabAdapter()

        codeView = WebView(applicationContext)
        with(codeView!!) {
            settings.apply {
                builtInZoomControls = false
                javaScriptEnabled = true
                //loadWithOverviewMode = true
                displayZoomControls = false
                allowFileAccess = true
                allowContentAccess = true
                setSupportZoom(false)
                cacheMode = WebSettings.LOAD_CACHE_ONLY
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR)
                        logger.verbose { "Editor console: ${message.message()} (${message.sourceId()}:${message.lineNumber()})" }
                    return true
                }
            }
            addJavascriptInterface(this@DefaultEditorActivity, "WebviewCallback")
            loadUrl(ENTRY_POINT)
        }
        binding.webviewContainer.addView(codeView)

        setHotKeyVisibility(isHotKeyShown)
        updateColor()

        val dp20 = dp(20)
        BottomSheetBehavior.from(binding.bottomSheet.root)
            .addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {}

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                if (!isHotKeyShown) return

                val offsetFlip = (1f - slideOffset)
                val radius = dp20 * offsetFlip
                binding.bottomSheet.cardViewTopRadius.radius = radius
                binding.scrollViewHotKeys.apply {
                    alpha = offsetFlip
                    if (offsetFlip == 0f)
                        visibility = View.GONE
                    else if (isGone)
                        visibility = View.VISIBLE
                }
            }
        })
    }

    override fun onDestroy() {
        getProject()?.let { proj ->
            proj.config.edit {
                val tabs = if (rememberTabs) sessions.map { it.fileName } else emptyList()
                category("editor").setString("saved_tabs", Json.encodeToString(tabs))
            }
        }

        binding.webviewContainer.removeAllViews()
        codeView?.destroy()
        codeView = null

        tabAdapter?.destroy()
        tabAdapter = null

        configAdapter?.destroy()
        configAdapter = null

        super.onDestroy()
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val superRes = super.onPrepareOptionsMenu(menu)
        val iconColor = ColorStateList.valueOf(getTextColor(theme.isTextDark))
        for (idx in 0 until menu.size) {
            val item = menu[idx]
            if (Build.VERSION.SDK_INT < 26)
                item.icon?.setTintList(iconColor)
            else
                item.iconTintList = iconColor

            if (item.itemId == R.id.menu_debug_room && getProject() == null)
                item.isVisible = false
            if (item.itemId == R.id.menu_recompile && getProject() == null)
                item.isVisible = false
        }
        return superRes
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.editor_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Shortcuts
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.isCtrlPressed) {
                when(event.keyCode) {
                    KeyEvent.KEYCODE_S ->
                        lifecycleScope.launch { saveAll() }
                    KeyEvent.KEYCODE_E ->
                        compileProject()
                    KeyEvent.KEYCODE_L -> {
                        if (binding.root.isDrawerOpen(GravityCompat.START))
                            binding.root.closeDrawer(GravityCompat.START, true)
                        else {
                            binding.root.openDrawer(GravityCompat.START, true)
                            fileTreeDrawer?.openLogs()
                        }
                    }
                    else ->
                        return super.dispatchKeyEvent(event)
                }
                return true
            }
            // Bypass Escape
            else if (event.keyCode == KeyEvent.KEYCODE_ESCAPE && bypassEscape) {
                return true
            } else if (event.keyCode == KeyEvent.KEYCODE_TAB) {
                execCommand(if (event.isShiftPressed) "blockoutdent" else "indent")
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    @JavascriptInterface
    override fun onLoadComplete() {
        isEditorReady = true
        setTheme(theme)
        setBottomInset(bottomInsetDp)
        runOnUiThread {
            tabAdapter!!.setSelected(mainScriptName)
        }

        with(GlobalConfig.category("e_code")) {
            getInt("font_size")?.let(::setFontSize)
            if (getBoolean("wrap_text") == true)
                setWordWrap(true)
        }

        executeScript("setApis(${buildApiCompletions()})")
        buildEventSnippets()?.let { snippets ->
            executeScript("setSnippets(${JsonPrimitive(mainLanguage.editorName)}, $snippets)")
        }
    }

    @JavascriptInterface
    override fun onDirtyChanged(sessionId: String, dirty: Boolean) {
        runOnUiThread {
            val index = sessions.indexOfFirst { it.sessionId.toString() == sessionId }
            if (index != -1)
                tabAdapter?.setSessionContentUpdated(index, dirty)
        }
    }

    @JavascriptInterface
    override fun onAnnotationUpdated(list: String) {
        val types = runCatching {
            json.parseToJsonElement(list).jsonArray.map { it.jsonObject["type"]?.jsonPrimitive?.content }
        }.getOrDefault(emptyList())
        runOnUiThread {
            problemCounts = types.count { it == "error" } to types.count { it == "warning" }
            fileTreeDrawer?.updateMessageCount(problemCounts)
            val fileName = currentFile?.name
            lifecycleScope.launch {
                EventHandler.fireEvent(EditorMessageFragment.EditorAnnotationReturnEvent(list, fileName))
            }
        }
    }

    internal var problemCounts: Pair<Int, Int> = 0 to 0
        private set

    internal var editorBackground: Int? = null
        private set

    @JavascriptInterface
    override fun onEditorBackground(color: String) {
        val channels = CSS_RGB.find(color)?.groupValues?.drop(1)?.map(String::toInt) ?: return
        runOnUiThread {
            editorBackground = Color.rgb(channels[0], channels[1], channels[2])
            fileTreeDrawer?.applyBackground(editorBackground)
        }
    }

    internal val isDarkTheme: Boolean
        get() = theme.themeStyle == ThemeStyle.Dark

    private fun attachFileTreeDrawer() {
        fileTreeDrawer = FileTreeDrawerFragment
            .newInstance(getBaseDirectory(), getProject())
            .also { supportFragmentManager.beginTransaction().replace(R.id.drawer_fileTree, it).commit() }
    }

    fun openFile(file: File) {
        //val relativePath =
        val parentPath = getBaseDirectory().path
        val filePath = file.path

        if (!filePath.startsWith(parentPath)) {
            logger.error {
                translate {
                    Locale.ENGLISH { "Requested file [$filePath] is not a child of base path [${parentPath}]." }
                    Locale.KOREAN  { "요청한 파일 [$filePath]은 기본 경로 [${parentPath}]의 하위 폴더가 아닙니다." }
                }
            }
            return
        }

        val relPath = filePath.drop(parentPath.length + 1)

        val language = getLanguageByExtension(file.extension) ?: Language.PLAIN_TEXT
        addSession(relPath, language, file.readText(), true)
    }

    internal val currentFile: File?
        get() = tabAdapter?.selectedSession?.let { getBaseDirectory().resolve(it.fileName) }

    private fun sessionsUnder(target: File): List<EditorSession> {
        val base = getBaseDirectory()
        return sessions.filter { base.resolve(it.fileName).startsWith(target) }
    }

    internal fun renameFile(target: File, dest: File, onDone: (renamed: Boolean) -> Unit) {
        lifecycleScope.launch {
            val affected = sessionsUnder(target)
            val dirty = affected.filter(EditorSession::isUpdated).map(EditorSession::sessionId)
            if (dirty.isNotEmpty() && !saveAll(dirty, silent = true))
                return@launch onDone(false)
            if (!target.renameTo(dest))
                return@launch onDone(false)

            val base = getBaseDirectory()
            for (session in affected) {
                val oldFile = base.resolve(session.fileName)
                val newFile = if (oldFile == target) dest else dest.resolve(oldFile.relativeTo(target))
                replaceSession(session, newFile.relativeTo(base).path)
            }
            onDone(true)
        }
    }

    internal fun onFileDeleted(target: File) {
        for (session in sessionsUnder(target))
            tabAdapter?.remove(session)
    }

    private fun replaceSession(old: EditorSession, fileName: String) {
        val index = sessions.indexOf(old)
        if (index == -1) return
        val session = EditorSession(fileName.hashCode(), fileName, getLanguageFromFileName(fileName, Language.PLAIN_TEXT), null)
        val wasSelected = tabAdapter?.selectedSession == old
        sessions[index] = session
        closeSession(old.sessionId)
        tabAdapter?.notifyItemChanged(index)
        if (wasSelected)
            setSession(session)
    }

    fun closeDrawer(gravity: Int, animate: Boolean) {
        binding.root.closeDrawer(gravity, animate)
    }

    @Suppress("SameParameterValue")
    private fun getLanguageFromFileName(fileName: String, fallback: Language): Language {
        val ext = File(fileName).extension
        return getLanguageByExtension(ext) ?: fallback
    }

    private fun addSession(fileName: String, lang: Language, code: String? = null, setAsMain: Boolean = false) {
        val sessionId = fileName.hashCode()
        if (sessions.any { it.sessionId == sessionId }) {
            if (setAsMain)
                tabAdapter?.setSelected(fileName)

            return
        }
        EditorSession(sessionId, fileName, lang, code).also {
            sessions += it
        }

        val idx = sessions.size - 1
        tabAdapter?.notifyItemInserted(idx)

        if (setAsMain) {
            tabAdapter?.setSelected(idx)
        }
    }

    private fun compileProject() {
        val project = getProject() ?: return
        lifecycleScope.launch {
            if (saveAll(silent = true))
                compileProject(project)
        }
    }

    private fun compileProject(project: Project) {
        val peek = createSimplePeek(
            text = translate {
                Locale.ENGLISH { "Compiling project [${project.info.name}]..." }
                Locale.KOREAN  { "프로젝트 [${project.info.name}] 컴파일 중..." }
            }
        ) {
            //backgroundColor(value = color { "#424242" })
            autoHideMillis = null
            iconRes = R.drawable.ic_round_developer_mode_24
            iconTint(R.color.text_clear)
        }.apply {
            setTextColor(R.color.text_clear)
        }
        peek.peek()

        lifecycleScope.launch {
            project
                .compileAsync()
                .onCompletion { e ->
                    fileTreeDrawer?.refreshHeader()
                    if (e != null)
                        return@onCompletion
                    peek.hide()
                    delay(300L)
                    peek.apply {
                        setAutoHide(3000L)
                        setIcon(dev.mooner.configdsl.R.drawable.ic_round_check_24)
                        setText(translate {
                            Locale.ENGLISH { "Successfully compiled [${project.info.name}]!" }
                            Locale.KOREAN  { "프로젝트 [${project.info.name}] 컴파일 완료!" }
                        })
                        setBackgroundColor(res = R.color.noctis_green)
                    }.peek()
                }
                .catch { e ->
                    e.sourcePosition()?.let { (line, column) ->
                        tabAdapter?.setSelected(mainScriptName)
                        markError(line - 1, column, e.message ?: e.toString())
                    }
                    peek.hide()
                    delay(300L)
                    peek.apply {
                        setPaddingDp(12)
                        setAutoHide(4000L)
                        setIcon(R.drawable.ic_round_error_outline_24)
                        setTitleColor(R.color.white)
                        setTitle(translate {
                            Locale.ENGLISH { "Failed to compile [${project.info.name}]" }
                            Locale.KOREAN  { "[${project.info.name}] 컴파일 실패" }
                        })
                        setTitleTypeface(getTypeface(this@DefaultEditorActivity, R.font.nanumsquare_neo_regular)!!)
                        setTitleSize(10f)
                        setTextColor(R.color.white)
                        setText(e.toString())
                        setTextTypeface(getTypeface(this@DefaultEditorActivity, R.font.nanumsquare_neo_bold)!!)
                        setTextSize(12f)
                        setAction(translate {
                            Locale.ENGLISH { "Details" }
                            Locale.KOREAN  { "자세히" }
                        }, textColorRes = R.color.text) {
                            binding.root.context.showErrorLogDialog(translate {
                                Locale.ENGLISH { "${project.info.name} error log" }
                                Locale.KOREAN  { "${project.info.name} 에러 로그" }
                            }, e)
                        }
                        setBackgroundColor(res = R.color.code_error)
                    }.peek()
                }
                .collect()
        }
        requestAnnotations()
    }

    private fun setupTabAdapter(): TabViewAdapter {
        val adapter = TabViewAdapter(this, sessions, theme.isTextDark) { type, index, item ->
            when(type) {
                TabViewAdapter.EVENT_SELECTED -> {
                    logger.verbose {
                        translate {
                            Locale.ENGLISH { "selected index: $index, item: ${item.fileName}" }
                            Locale.KOREAN  { "선택한 index: $index, item: ${item.fileName}" }
                        }
                    }
                    setSession(item)
                }
                TabViewAdapter.EVENT_CLOSE_REQUESTED ->
                    confirmTabClose(item)
                TabViewAdapter.EVENT_CLOSED ->
                    closeSession(item.sessionId)
            }
        }.also(binding.rvOpenFiles::setAdapter)
        binding.rvOpenFiles.itemAnimator = FadeInAnimator()
        ItemTouchHelper(TabItemMoveCallbackListener(adapter))
            .attachToRecyclerView(binding.rvOpenFiles)
        return adapter
    }

    private fun openDebugRoomDrawer() {
        binding.root.openDrawer(GravityCompat.END, true)
        //binding.root.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.END)
    }

    private fun createPeek(text: String, builder: PeekAlertBuilder.() -> Unit): PeekAlert {
        return createPeekAlert(this) {
            //paddingDp = 16
            position = PeekAlert.Position.Top
            width = ViewGroup.LayoutParams.WRAP_CONTENT
            cornerRadius = dp(14).toFloat()
            autoHideMillis = 3000L
            draggable = true
            iconTint(res = R.color.white)
            text(text) {
                textSize = 14f
                typeface = getTypeface(this@DefaultEditorActivity, R.font.nanumsquare_round_bold)
            }
            this.apply(builder)
        }
    }

    private fun updateColor() {
        setupHotKeys(clear = true)

        val color = theme.toolbarColor ?: getColor(R.color.background)
        val textColor = getTextColor(theme.isTextDark)
        val textColorStateList = ColorStateList.valueOf(textColor)

        supportActionBar!!.setBackgroundDrawable(color.toDrawable())
        window.statusBarColor = color
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = theme.isTextDark
        binding.toolbarEditor.apply {
            setTitleTextColor(textColor)
            navigationIcon!!.colorFilter = PorterDuffColorFilter(textColor, PorterDuff.Mode.MULTIPLY)
            for (idx in 0 until menu.size) {
                if (Build.VERSION.SDK_INT < 26)
                    menu[idx].icon?.setTintList(textColorStateList)
                else
                    menu[idx].iconTintList = textColorStateList
            }
        }
        //binding.toolbarEditor.foregroundTintList = ColorStateList.valueOf(textColor)
        binding.rvOpenFiles.setBackgroundColor(color)
        val stateList = ColorStateList.valueOf(color)
        binding.bottomSheet.apply {
            root.backgroundTintList = stateList
            cardViewTopRadius.setCardBackgroundColor(stateList)
            cardView2.setCardBackgroundColor(textColor)
        }
        tabAdapter?.updateTextColor(theme.isTextDark)
        binding.scrollViewHotKeys.backgroundTintList = stateList
    }

    private fun getTextColor(isDark: Boolean): Int {
        return if (isDark)
            getColor(R.color.editor_text_dark)
        else
            getColor(R.color.editor_text)
    }

    @Suppress("SameParameterValue")
    private fun setupHotKeys(clear: Boolean = false) {
        if (clear)
            binding.layoutHotKeys.removeAllViewsInLayout()

        val widthDP = dp(36)
        val textPadding = dp(8)
        val backgroundColor = ColorStateList.valueOf(theme.toolbarColor ?: getColor(R.color.transparent))
        val textColor = ColorStateList.valueOf(getTextColor(theme.isTextDark))

        for (key in HOT_KEYS) {
            val textView = TextView(binding.root.context).apply {
                text = key.toString()
                textSize = 14f
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                setTextColor(textColor)
                backgroundTintList = backgroundColor

                if (layoutParams == null)
                    layoutParams = ViewGroup.LayoutParams(widthDP, widthDP)
                updateLayoutParams {
                    width = widthDP
                    height = widthDP
                    updatePadding(top = textPadding)
                }
                isHapticFeedbackEnabled = true
                val action = {
                    when(key) {
                        '↺' -> undo()
                        '↻' -> redo()
                        '⇒' -> execCommand("indent")
                        '←' -> moveCursor(-1)
                        '→' -> moveCursor(1)
                        else -> insertText(key.toString())
                    }
                }
                if (key in REPEATING_HOT_KEYS)
                    setRepeatingAction(action)
                else
                    setOnClickListener { view ->
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        action()
                    }
            }
            binding.layoutHotKeys.addView(textView)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun View.setRepeatingAction(action: () -> Unit) {
        var repeated = false
        val repeat = object : Runnable {
            override fun run() {
                repeated = true
                action()
                postDelayed(this, HOT_KEY_REPEAT_INTERVAL)
            }
        }
        setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    repeated = false
                    view.isPressed = true
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    view.postDelayed(repeat, HOT_KEY_REPEAT_DELAY)
                }
                MotionEvent.ACTION_UP -> {
                    view.isPressed = false
                    view.removeCallbacks(repeat)
                    if (!repeated)
                        action()
                }
                MotionEvent.ACTION_CANCEL -> {
                    view.isPressed = false
                    view.removeCallbacks(repeat)
                }
            }
            true
        }
    }

    private fun setHotKeyVisibility(visible: Boolean) {
        val behavior = BottomSheetBehavior
            .from(binding.bottomSheet.rootBottomSheet)

        binding.bottomSheet.cardViewTopRadius.radius =
            if (visible && behavior.state != BottomSheetBehavior.STATE_EXPANDED) dp(20).toFloat() else 0f
        val (peekHeightDp, visibility) = when(visible) {
            true -> 64 to View.VISIBLE
            false -> 24 to View.GONE
        }
        val cornerDp = if (visible) 20 else 0
        behavior.setPeekHeight(dp(peekHeightDp), true)
        binding.webviewContainer.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = dp(peekHeightDp - cornerDp)
        }
        bottomInsetDp = cornerDp
        setBottomInset(cornerDp)
        binding.scrollViewHotKeys.visibility = visibility
        binding.layoutHotKeys.visibility = visibility
    }

    private fun getStructure() = config {
        val titleTextColor = getTextColor(theme.isTextDark)
        category {
            id = "e_general"
            title = translate {
                Locale.ENGLISH { "General" }
                Locale.KOREAN  { "일반" }
            }
            textColor = titleTextColor
            items {
                toggle {
                    id = "show_hot_keys"
                    title = translate {
                        Locale.ENGLISH { "Show hot-keys" }
                        Locale.KOREAN  { "핫 키 표시" }
                    }
                    icon = Icon.KEYBOARD
                    defaultValue = true
                    setOnValueChangedListener { _, isEnabled ->
                        setHotKeyVisibility(isEnabled)
                    }
                }
                toggle {
                    id = "bypass_escape"
                    title = translate {
                        Locale.ENGLISH { "Ignore ESC key" }
                        Locale.KOREAN  { "ESC 키 입력 무시" }
                    }
                    icon = Icon.EXIT_TO_APP
                    defaultValue = true
                    setOnValueChangedListener { _, isEnabled ->
                        bypassEscape = isEnabled
                    }
                }
                val themes = Theme.entries.toTypedArray()
                spinner {
                    id = "theme"
                    title = translate {
                        Locale.ENGLISH { "Theme" }
                        Locale.KOREAN  { "테마" }
                    }
                    icon = Icon.LAYERS
                    items = themes.map { t ->
                        val styleName = when(t.themeStyle) {
                            ThemeStyle.Light -> "Light"
                            ThemeStyle.Dark -> "Dark"
                        }
                        "${t.shownName} ($styleName)"
                    }
                    defaultIndex = DEFAULT_THEME.ordinal
                    setOnItemSelectedListener { _, index ->
                        val selectedTheme = themes[index]
                        val styleChanged = selectedTheme.themeStyle != theme.themeStyle
                        theme = selectedTheme
                        setTheme(selectedTheme)
                        updateColor()
                        if (styleChanged && showFileTree)
                            attachFileTreeDrawer()
                    }
                }
            }
        }
        category {
            id = "e_code"
            title = translate {
                Locale.ENGLISH { "Code" }
                Locale.KOREAN  { "코드" }
            }
            textColor = titleTextColor
            items {
                seekbar {
                    id = "font_size"
                    title = translate {
                        Locale.ENGLISH { "Font size" }
                        Locale.KOREAN  { "글자 크기" }
                    }
                    icon = Icon.TEXT_FIELDS
                    min = 8
                    max = 32
                    defaultValue = DEFAULT_FONT_SIZE
                }
                toggle {
                    id = "wrap_text"
                    title = translate {
                        Locale.ENGLISH { "Wrap text" }
                        Locale.KOREAN  { "자동 줄바꿈(워드 랩)" }
                    }
                    icon = Icon.WRAP_TEXT
                    defaultValue = false
                    setOnValueChangedListener { _, toggle ->
                        setWordWrap(toggle)
                    }
                }
            }
        }
        category {
            id = "e_files"
            title = translate {
                Locale.ENGLISH { "Tabs" }
                Locale.KOREAN  { "탭" }
            }
            textColor = titleTextColor
            items {
                toggle {
                    id = "always_show_close_button"
                    title = translate {
                        Locale.ENGLISH { "Always show close button" }
                        Locale.KOREAN  { "닫기 버튼 항상 표시" }
                    }
                    icon = Icon.CLOSE
                    defaultValue = true
                }
                toggle {
                    id = "show_lang_icon"
                    title = translate {
                        Locale.ENGLISH { "Show file icon by language" }
                        Locale.KOREAN  { "파일 언어 아이콘 표시" }
                    }
                    icon = Icon.CHECK
                    defaultValue = true
                }
                toggle {
                    id = "remember_tabs"
                    title = translate {
                        Locale.ENGLISH { "Remember open tabs" }
                        Locale.KOREAN  { "열린 탭 기억하기" }
                    }
                    icon = Icon.BOOKMARK
                    defaultValue = true
                }
            }
        }
    }

    private suspend fun collectDirty(sessionIds: List<Int>?): Map<String, String> =
        suspendCancellableCoroutine { cont ->
            val view = codeView ?: return@suspendCancellableCoroutine cont.resume(emptyMap())
            val filter = sessionIds?.let { ids -> JsonArray(ids.map { JsonPrimitive(it.toString()) }) } ?: "null"
            view.evaluateJavascript("collectDirty($filter)") { result ->
                cont.resume(runCatching { json.decodeFromString<Map<String, String>>(result) }.getOrDefault(emptyMap()))
            }
        }

    private suspend fun saveAll(sessionIds: List<Int>? = null, silent: Boolean = false): Boolean {
        val targets = collectDirty(sessionIds).mapNotNull { (id, code) ->
            sessions.find { it.sessionId.toString() == id }?.let { it to code }
        }
        val failed = withContext(Dispatchers.IO) {
            targets
                .filterNot { (session, code) -> saveCode(session.fileName, code) }
                .map { (session, _) -> session }
        }
        for ((session, _) in targets) {
            val index = sessions.indexOf(session)
            if (session in failed)
                markDirty(session.sessionId)
            else if (index != -1)
                tabAdapter?.setSessionContentUpdated(index, false)
        }

        if (failed.isNotEmpty()) {
            createPeek(translate {
                Locale.ENGLISH { "Failed to save ${failed.joinToString { it.fileName }}" }
                Locale.KOREAN  { "${failed.joinToString { it.fileName }} 저장 실패" }
            }) {
                iconRes = R.drawable.ic_round_error_outline_24
                backgroundColor(R.color.code_error)
            }.apply {
                setTextColor(R.color.white)
            }.peek()
            return false
        }
        if (silent)
            return true

        val savedCount = targets.size
        createPeek(translate {
            Locale.ENGLISH { if (savedCount == 0) "No changes to save" else "Saved $savedCount file(s)" }
            Locale.KOREAN  { if (savedCount == 0) "저장할 변경사항이 없어요" else "${savedCount}개 파일 저장 완료" }
        }) {
            iconRes = dev.mooner.configdsl.R.drawable.ic_round_check_24
            iconTint(R.color.noctis_green)
        }.apply {
            setTextColor(R.color.text_clear)
        }.peek()
        return true
    }

    private fun showUnsavedDialog(title: String, message: String, onSave: () -> Unit, onDiscard: () -> Unit) {
        showSheet {
            this.title = title
            this.message = message
            icon = R.drawable.ic_round_save_24
            positive(translate {
                Locale.ENGLISH { "Save" }
                Locale.KOREAN  { "저장" }
            }) { onSave() }
            negative(R.string.cancel)
            neutral(translate {
                Locale.ENGLISH { "Don't save" }
                Locale.KOREAN  { "저장 안 함" }
            }) { onDiscard() }
        }
    }

    private fun confirmTabClose(session: EditorSession) {
        if (!session.isUpdated) {
            tabAdapter?.remove(session)
            return
        }
        showUnsavedDialog(
            title = translate {
                Locale.ENGLISH { "Save changes to ${session.fileName}?" }
                Locale.KOREAN  { "${session.fileName}의 변경사항을 저장할까요?" }
            },
            message = translate {
                Locale.ENGLISH { "Unsaved changes will be lost if you close this tab." }
                Locale.KOREAN  { "저장하지 않으면 이 탭의 변경사항이 사라져요." }
            },
            onSave = {
                lifecycleScope.launch {
                    if (saveAll(listOf(session.sessionId), silent = true))
                        tabAdapter?.remove(session)
                }
            },
            onDiscard = { tabAdapter?.remove(session) }
        )
    }

    private fun confirmEditorExit(onExit: () -> Unit) {
        if (!isCodeUpdated)
            return onExit()
        val unsaved = sessions.filter(EditorSession::isUpdated)
        showUnsavedDialog(
            title = translate {
                Locale.ENGLISH { "Save changes before leaving?" }
                Locale.KOREAN  { "나가기 전에 저장할까요?" }
            },
            message = translate {
                Locale.ENGLISH { "Unsaved: ${unsaved.joinToString { it.fileName }}" }
                Locale.KOREAN  { "저장하지 않은 파일: ${unsaved.joinToString { it.fileName }}" }
            },
            onSave = {
                lifecycleScope.launch {
                    if (saveAll(silent = true))
                        onExit()
                }
            },
            onDiscard = onExit
        )
    }

    private fun beautifyCode() =
        executeScript("beautifyCode()")

    private fun executeScript(script: String) {
        if (isEditorReady)
            codeView?.post { codeView?.evaluateJavascript(script, null) }
    }

    private fun setSession(session: EditorSession) {
        if (!isEditorReady)
            return
        val code = if (session.isLoaded) null else session.code ?: readCode(session.fileName) ?: ""
        session.isLoaded = true
        session.code = null
        val codeArg = code?.let(::JsonPrimitive) ?: "null"
        executeScript("setSession(${JsonPrimitive(session.sessionId.toString())}, ${JsonPrimitive(session.language.editorName)}, $codeArg)")
    }

    private fun closeSession(sessionId: Int) =
        executeScript("closeSession(${JsonPrimitive(sessionId.toString())})")

    private fun markDirty(sessionId: Int) =
        executeScript("markDirty(${JsonPrimitive(sessionId.toString())})")

    private fun setTheme(theme: Theme) =
        executeScript("setTheme(${JsonPrimitive(theme.editorName)})")

    private fun insertText(text: String) =
        executeScript("insertText(${JsonPrimitive(text)})")

    private fun execCommand(name: String) =
        executeScript("execCommand(${JsonPrimitive(name)})")

    private fun moveCursor(dx: Int) =
        executeScript("moveCursor($dx)")

    private fun undo() =
        executeScript("undo()")

    private fun redo() =
        executeScript("redo()")

    private fun setFontSize(size: Int) =
        executeScript("setFontSize($size)")

    private fun setWordWrap(wrap: Boolean) =
        executeScript("setWordWrap($wrap)")

    private fun setBottomInset(dp: Int) =
        executeScript("setBottomInset($dp)")

    internal fun requestAnnotations() =
        executeScript("requestAnnotations()")

    internal fun gotoLine(row: Int, column: Int) =
        executeScript("_gotoLine($row, $column)")

    private fun markError(row: Int, column: Int, text: String) =
        executeScript("markError($row, $column, ${JsonPrimitive(text)})")

    private fun Throwable.sourcePosition(): Pair<Int, Int>? =
        generateSequence(this, Throwable::cause).firstNotNullOfOrNull { e ->
            when (e) {
                is NeonSyntaxException -> e.line to e.column
                is RhinoException      -> e.lineNumber() to e.columnNumber()
                else                   -> null
            }
        }?.takeIf { (line, _) -> line > 0 }

    private fun buildApiCompletions(): String =
        buildJsonArray {
            for (api in Session.apiManager.getApis()) {
                addJsonObject {
                    put("name", api.name)
                    putJsonArray("members") {
                        api.objects
                            .filterNot { '$' in it.name }
                            .distinctBy(ApiObject::name)
                            .forEach { member ->
                                addJsonObject {
                                    put("name", member.name)
                                    put("caption", when (member) {
                                        is ApiObject.Function ->
                                            member.name + member.args.joinToString(prefix = "(", postfix = ")") { it.simpleName }
                                        is ApiObject.Value ->
                                            member.name
                                    })
                                    put("returns", member.returns.simpleName.takeUnless { it == "Unit" } ?: "void")
                                }
                            }
                    }
                }
            }
        }.toString()

    private fun buildEventSnippets(): String? {
        val generator = getProject()?.getLanguage()?.codeGenerator
            ?: return null
        val events = ProjectEventManager.filterAllowedEvents(setOf("*"))
            .mapNotNull { id -> ProjectEventManager.findFirst(id)?.getInstance() }
            .distinctBy { it.functionName }
        return buildJsonArray {
            for (event in events) {
                val function = generator.generateFunction(event.functionName, event.argTypes, null).function
                addJsonObject {
                    put("name", event.functionName)
                    put("tabTrigger", event.functionName)
                    put("content", function.replace("$", "\\$").replaceFirst("\n    \n", "\n\t\${0}\n"))
                }
            }
        }.toString()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_save -> lifecycleScope.launch { saveAll() }
            R.id.menu_recompile -> compileProject()
            R.id.menu_find -> execCommand("find")
            R.id.menu_replace -> execCommand("replace")
            R.id.menu_goto_line -> execCommand("gotoline")
            R.id.code_beautify -> beautifyCode()
            R.id.menu_debug_room -> {
                val project = getProject() ?: return false
                if (!project.isCompiled) {
                    createPeek(translate {
                        Locale.ENGLISH { "Project isn't compiled yet." }
                        Locale.KOREAN  { "아직 프로젝트가 컴파일 되지 않았어요." }
                    }) {
                        iconRes = dev.mooner.configdsl.R.drawable.ic_round_close_24
                        backgroundColor(R.color.orange)
                    }.apply {
                        setTextColor(R.color.white)
                    }.peek()
                } else {
                    when(layoutMode) {
                        LAYOUT_TABLET -> openDebugRoomDrawer()
                        LAYOUT_DEFAULT -> startActivityWithExtra(
                            DebugRoomActivity::class.java,
                            mapOf(DebugRoomActivity.EXTRA_PROJECT_NAME to project.info.name)
                        )
                    }
                }
            }
            //android.R.id.home -> confirmEditorExit(::finish)
            android.R.id.home -> binding.root.openDrawer(GravityCompat.START, true)
            //R.id.text_undo ->
            //R.id.text_redo ->
        }
        return super.onOptionsItemSelected(item)
    }

    @Deprecated("")
    override fun onBackPressed() {
        if (binding.root.isDrawerOpen(GravityCompat.END))
            binding.fragmentContainerDebugRoom!!
                .getFragment<DebugRoomFragment>()
                .onBackPressed().also { wasOpen ->
                    if (!wasOpen)
                        binding.root.closeDrawer(GravityCompat.END)
                }
        else if (binding.root.isDrawerOpen(GravityCompat.START))
            binding.root.closeDrawer(GravityCompat.START)
        else
            confirmEditorExit(::finish)
    }

    companion object {
        const val EXTRA_SHOW_FILE_TREE = "showFileTree"
        const val ENTRY_POINT          = "file:///android_asset/editor/index.html"

        private const val HOT_KEYS = "↺↻⇒←→[]{}()<>;=.'\"+-*/\\:&|"
        private const val DEFAULT_FONT_SIZE = 15
        private val CSS_RGB = "rgba?\\((\\d+),\\s*(\\d+),\\s*(\\d+)".toRegex()
        private const val REPEATING_HOT_KEYS = "↺↻←→"
        private const val HOT_KEY_REPEAT_DELAY = 400L
        private const val HOT_KEY_REPEAT_INTERVAL = 60L
        private val DEFAULT_THEME = Theme.TOMORROW_NIGHT_EIGHTIES
    }

    @Suppress("unused")
    enum class Language(
        val editorName: String,
        vararg val fileExt: String,
        val icon: Int? = null
    ) {
        ACTION_SCRIPT("actionscript", "as"),
        ADA("ada", "ada"),
        ASSEMBLY_X86("assembly_x86", "s", "asm"),
        AUTOHOTKEY("autohotkey", "ahk"),
        C("c_cpp", "c"),
        CPP("c_cpp", "cpp"),
        COFFEESCRIPT("coffee", "coffee", "litcoffee"),
        CSHARP("csharp", "cs"),
        DART("dart", "dart"),
        ELIXIR("elixir", "ex", "exs"),
        FORTRAN("fortran", "f90", "for", "f"),
        GOLANG("golang", "go"),
        GROOVY("groovy", "groovy", "gvy", "gy", "gsh"),
        JAVA("java", "java"),
        JAVASCRIPT("javascript", "js", icon = dev.mooner.starlight.R.drawable.ic_js),
        JSON("json", "json"),
        KOTLIN("kotlin", "kt", "kts"),
        LUA("lua", "lua"),
        MARKDOWN("markdown", "md"),
        OBJECTIVE_C("objectivec", "m", "mm"),
        PASCAL("pascal", "pas"),
        PERL("perl", "pl"),
        PHP("php", "php"),
        PLAIN_TEXT("plain_text", "txt"),
        PYTHON("python", "py", icon = dev.mooner.starlight.R.drawable.ic_python),
        R("r", "r"),
        RUBY("ruby", "rb"),
        RUST("rust", "rs"),
        SCALA("scala", "scala"),
        SWIFT("swift", "swift"),
        TYPESCRIPT("typescript", "ts"),
        VBSCRIPT("vbscript", "vbs", "vbe", "wsf", "wsc"),
    }

    sealed class ThemeStyle {
        data object Light: ThemeStyle()
        data object Dark: ThemeStyle()
    }

    @Suppress("unused")
    enum class Theme(
        val shownName       : String,
        val editorName      : String,
        val themeStyle      : ThemeStyle,
        val toolbarColor    : Int? = if (themeStyle == ThemeStyle.Light) color { "#b7b7b7" } else color { "#202020" },
        val isTextDark      : Boolean = false,
    ) {
        // Light themes
        GITHUB("Github", "github", ThemeStyle.Light, color { "#e2f1fe" }, true),
        ECLIPSE("Eclipse", "eclipse", ThemeStyle.Light, color { "#b7b7b7" }, true),
        CLOUDS("Clouds", "clouds", ThemeStyle.Light, color { "#b7b7b7" }, true),
        XCODE("XCode", "xcode", ThemeStyle.Light, color { "#b7b7b7" }, true),
        CHROME("Chrome", "chrome", ThemeStyle.Light, color { "#b7b7b7" }, true),
        DREAMWEAVER("Dreamweaver","dreamweaver", ThemeStyle.Light, color { "#b7b7b7" }, true),
        IPLASTIC("IPlastic", "iplastic", ThemeStyle.Light, color { "#909090" }),
        KATZEN_MILCH("Katzenmilch", "katzenmilch", ThemeStyle.Light, color { "#a2a9b1" }),
        SOLARIZED_LIGHT("Solarized-Light", "solarized_light", ThemeStyle.Light, color { "#bbb6a7" }, true),
        TOMORROW("Tomorrow", "tomorrow", ThemeStyle.Light, color { "#b7b7b7" }, true),

        // Dark themes
        //AMBIANCE("Ambiance", "ambiance", ThemeStyle.Dark),
        GITHUB_DARK("Github-Dark", "github_dark", ThemeStyle.Dark),
        DRACULA("Dracula", "dracula", ThemeStyle.Dark),
        GRUVBOX("Gruvbox", "gruvbox", ThemeStyle.Dark, color { "#1d2021" }),
        CLOUDS_MIDNIGHT("Clouds-Midnight", "clouds_midnight", ThemeStyle.Dark),
        PASTEL_ON_DARK("Pastel On Dark", "pastel_on_dark", ThemeStyle.Dark, color { "#332f2f" }),
        SOLARIZED_DARK("Solarized-Dark", "solarized_dark", ThemeStyle.Dark, color { "#1a414a" }),
        TERMINAL("Terminal", "terminal", ThemeStyle.Dark, color { "#1a0005" }),
        TOMORROW_NIGHT("Tomorrow Night", "tomorrow_night", ThemeStyle.Dark, color { "#3e4047" }),
        TOMORROW_NIGHT_EIGHTIES("Tomorrow Night-Eighties", "tomorrow_night_eighties", ThemeStyle.Dark, color { "#535153" }),
        MONOKAI("Monokai", "monokai", ThemeStyle.Dark, color { "#202020" }),
        //GREEN_ON_BLACK("Green on black", "green_on_black", ThemeStyle.Dark),
        KR_THEME("KR Theme", "kr_theme", ThemeStyle.Dark, color { "#322129" }),
        NORD_DARK("Nord Dark", "nord_dark", ThemeStyle.Dark, color { "#92A9BD" }),
        ONE_DARK("One Dark", "one_dark", ThemeStyle.Dark, color { "#656980" }),
        TWILIGHT("Twilight", "twilight", ThemeStyle.Dark),
    }
}