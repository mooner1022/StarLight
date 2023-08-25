package dev.mooner.starlight.ui.editor

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.*
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.TextView
import androidx.core.view.GravityCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.drawerlayout.widget.DrawerLayout
import androidx.drawerlayout.widget.DrawerLayout.DrawerListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.ActivityDefaultEditorBinding
import dev.mooner.starlight.logging.bindLogNotifier
import dev.mooner.starlight.plugincore.Session.json
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.config.config
import dev.mooner.starlight.plugincore.editor.CodeEditorActivity
import dev.mooner.starlight.plugincore.event.EventHandler
import dev.mooner.starlight.plugincore.logger.LoggerFactory
import dev.mooner.starlight.plugincore.translation.Locale
import dev.mooner.starlight.plugincore.translation.translate
import dev.mooner.starlight.plugincore.utils.Icon
import dev.mooner.starlight.plugincore.utils.color
import dev.mooner.starlight.ui.config.ConfigAdapter
import dev.mooner.starlight.ui.debugroom.DebugRoomActivity
import dev.mooner.starlight.ui.debugroom.DebugRoomFragment
import dev.mooner.starlight.ui.editor.drawer.EditorMessageFragment
import dev.mooner.starlight.ui.editor.drawer.FileTreeDrawerFragment
import dev.mooner.starlight.ui.editor.tab.EditorSession
import dev.mooner.starlight.ui.editor.tab.TabItemMoveCallbackListener
import dev.mooner.starlight.ui.editor.tab.TabViewAdapter
import dev.mooner.starlight.utils.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.*
import kotlin.math.max
import kotlin.properties.Delegates.notNull

private val logger = LoggerFactory.logger {  }

class DefaultEditorActivity : CodeEditorActivity(), WebviewCallback {

    private lateinit var name     : String
    private lateinit var binding  : ActivityDefaultEditorBinding
    private          var codeView : WebView? = null

    private var theme          : Theme = Theme.NORD_DARK

    private var mainScriptName : String by notNull()
    private var code           : String by notNull()
    private val sessions       : MutableList<EditorSession> = arrayListOf()
    private var currentSession : EditorSession? = null
    private val isCodeUpdated  : Boolean get() = sessions.any { it.isUpdated }
    private var isDebugTabOpen : Boolean = false

    private var tabAdapter     : TabViewAdapter? = null
    private var configAdapter  : ConfigAdapter? = null

    private var showFileTree   : Boolean by notNull()
    private var showDebugChat  : Boolean by notNull()

    private var bypassEscape   : Boolean by notNull()

    private val isHotKeyShown  : Boolean get() =
        GlobalConfig
            .category("e_general")
            .getBoolean("show_hot_keys", true)

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDefaultEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbarEditor)

        bindLogNotifier()

        showFileTree  = intent.getBooleanExtra(EXTRA_SHOW_FILE_TREE, true)
        showDebugChat = intent.getBooleanExtra(EXTRA_SHOW_DEBUG_CHAT, true)

        name = intent.getStringExtra("title")!!
        supportActionBar!!.apply {
            if (showFileTree) {
                setDisplayHomeAsUpEnabled(true)
                setHomeAsUpIndicator(R.drawable.ic_round_menu_24)
            }

            if (showDebugChat)
                setDisplayShowHomeEnabled(true)

            title = name
        }

        theme = GlobalConfig
            .category("e_general")
            .getInt("theme", Theme.TOMORROW_NIGHT.ordinal)
            .let(Theme.values()::get)

        bypassEscape = GlobalConfig
            .category("e_general")
            .getBoolean("bypass_escape", true)

        binding.scrollViewHotKeys.isHorizontalScrollBarEnabled = false

        // Lock drawer layout open by swipe
        binding.root.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.END)
        if (!showFileTree)
            binding.root.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.START)

        binding.root.addDrawerListener(object : DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}

            override fun onDrawerOpened(drawerView: View) {
                if (drawerView.id == R.id.drawer_debugChat)
                    isDebugTabOpen = true
            }

            override fun onDrawerClosed(drawerView: View) {
                if (drawerView.id == R.id.drawer_debugChat)
                    isDebugTabOpen = false
            }

            override fun onDrawerStateChanged(newState: Int) {}
        })

        if (showDebugChat && layoutMode == LAYOUT_TABLET) {
            // Left drawer (Debug Chat)
            //binding.debugChat!!.leave.visibility = View.INVISIBLE
            supportFragmentManager.beginTransaction().apply {
                replace(
                    R.id.fragment_container_debug_room,
                    DebugRoomFragment.newInstance(
                        projectName = getProject().info.name,
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
            supportFragmentManager.beginTransaction().apply {
                replace(
                    R.id.drawer_fileTree,
                    FileTreeDrawerFragment
                        .newInstance(getProject())
                )
            }.commit()
        }

        updateColor()
        //setupHotKeys()

        configAdapter = ConfigAdapter.Builder(this) {
            bind(binding.bottomSheet.configRecyclerView)
            structure(::getStructure)
            savedData(GlobalConfig.getDataMap())
            onConfigChanged { parentId, id, _, data ->
                GlobalConfig.edit {
                    category(parentId).setAny(id, data)
                }
            }
        }.build()

        val fileName = getProject().info.mainScript
        mainScriptName = fileName
        val ext = File(fileName).extension
        val lang = let {
            for (lang in Language.values()) {
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
        logger.verbose {
            translate {
                Locale.ENGLISH { "File extension: $ext, Auto-detected language: $lang" }
                Locale.KOREAN  { "파일 확장자: $ext, 자동으로 인식한 언어: $lang" }
            }
        }

        code = readCodeOrDefault(fileName, getProject().getLanguage())

        val savedTabs: Collection<String> =
            getProject().config.category("editor")
                .getString("saved_tabs", "[]")
                .let<_, MutableList<String>>(json::decodeFromString)
                .also { if (fileName in it) it -= fileName }

        addSession(fileName, lang, code)
        for (tab in savedTabs) {
            val language = getLanguageFromFileName(tab, Language.PLAIN_TEXT)
            addSession(tab, language)
        }
        //addSession("dummy.ts", Language.TYPESCRIPT)
        //addSession("fuck_python.py", Language.PYTHON)

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
                webChromeClient = WebChromeClient()
                setSupportZoom(false)
                cacheMode = WebSettings.LOAD_CACHE_ONLY
                addJavascriptInterface(this@DefaultEditorActivity, "WebviewCallback")
            }
            loadUrl(ENTRY_POINT)
        }
        binding.webviewContainer.addView(codeView)

        setHotKeyVisibility(isHotKeyShown)
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
                    else if (visibility == View.GONE)
                        visibility = View.VISIBLE
                }
            }
        })
    }

    override fun onDestroy() {
        getProject().config.edit {
            val str = Json.encodeToString(sessions.map { it.fileName })
            category("editor").setString("saved_tabs", str)
        }

        codeView?.destroy()
        codeView = null

        tabAdapter?.destroy()
        tabAdapter = null

        configAdapter?.destroy()
        configAdapter = null

        super.onDestroy()
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
                    KeyEvent.KEYCODE_S -> {
                        saveCode()
                    }
                    else -> {
                        return super.dispatchKeyEvent(event)
                    }
                }
                return true
            }
            // Bypass Escape
            else if (event.keyCode == KeyEvent.KEYCODE_ESCAPE && bypassEscape) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    @JavascriptInterface
    override fun onLoadComplete() {
        setTheme(theme)
        runOnUiThread {
            tabAdapter!!.setSelected(mainScriptName)
        }
        resetUndoStack()
    }

    @JavascriptInterface
    override fun onContentChanged(sessionId: String?, code: String?) {
        if (isDebugTabOpen)
            return
        currentSession?.let {
            it.code = code
            if (!it.isUpdated) {
                it.isUpdated = true
                runOnUiThread {
                    tabAdapter?.notifyItemChanged(tabAdapter!!.sessions.indexOf(currentSession))
                }
            }
        }
    }

    @JavascriptInterface
    override fun onAnnotationUpdated(list: String) {
        logger.info { "list: $list" }
        lifecycleScope.launch {
            EventHandler.fireEvent(EditorMessageFragment.EditorAnnotationReturnEvent(list))
        }
    }

    @JavascriptInterface
    override fun requestSession(sessionId: String?) {
        if (sessionId == null) return
        logger.verbose {
            translate {
                Locale.ENGLISH { "Editor requested session with id: $sessionId" }
                Locale.KOREAN  { "에디터가 세션을 요청함: $sessionId" }
            }
        }
        val requestedSession = sessions.find { it.sessionId == sessionId.toInt() } ?: let {
            logger.error {
                translate {
                    Locale.ENGLISH { "Editor requested session with id: $sessionId, but not found" }
                    Locale.KOREAN  { "데이터가 세션을 요청했지만 찾지 못함: $sessionId" }
                }
            }
            return
        }
        if (requestedSession.code == null) {
            requestedSession.code = readCode(requestedSession.fileName) ?: ""
        }
        setSession(requestedSession)
    }

    fun openFile(file: File) {
        //val relativePath =
        val parentPath = getProject().directory.path
        val filePath = file.path

        if (!filePath.startsWith(parentPath)) {
            logger.error {
                translate {
                    Locale.ENGLISH { "Requested file [$filePath] is not a child of project path." }
                    Locale.KOREAN  { "요청한 파일 [$filePath]은 프로젝트의 하위 폴더가 아닙니다." }
                }
            }
            return
        }

        val relPath = filePath.drop(parentPath.length + 1)

        val language = getLanguageByExtension(file.extension) ?: Language.PLAIN_TEXT
        addSession(relPath, language, file.readText(), true)
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
        saveCode()
        val project = getProject()

        snackbar(translate {
            Locale.ENGLISH { "Compiling project [${project.info.name}]..." }
            Locale.KOREAN  { "프로젝트 [${project.info.name}] 컴파일 중..." }
        })

        lifecycleScope.launch {
            getProject()
                .compileAsync()
                .onCompletion {
                    snackbar(translate {
                        Locale.ENGLISH { "Finished compile of [${project.info.name}!]" }
                        Locale.KOREAN  { "프로젝트 [${project.info.name}] 컴파일 완료!" }
                    })
                }
                .catch { e ->
                    snackbar(translate {
                        Locale.ENGLISH { "Failed to compile [${project.info.name}]" }
                        Locale.KOREAN  { "프로젝트 [${project.info.name}] 컴파일 실패:\n$e" }
                    }, e)
                }
                .collect()
        }
        requestAnnotations()
    }

    private fun setupTabAdapter(): TabViewAdapter {
        val adapter = TabViewAdapter(this, sessions) { type, index, item ->
            when(type) {
                TabViewAdapter.EVENT_SELECTED -> {
                    logger.verbose {
                        translate {
                            Locale.ENGLISH { "selected index: $index, item: ${item.fileName}" }
                            Locale.KOREAN  { "선택한 index: $index, item: ${item.fileName}" }
                        }
                    }
                    item.code = readCode(item.fileName) ?: ""
                    currentSession = item
                    setSession(item)
                }
                TabViewAdapter.EVENT_CLOSED -> {
                    sessions.remove(item)
                    if (currentSession != item)
                        return@TabViewAdapter

                    logger.verbose { "closed, idx = $index" }
                    val idx = max(index - 1, 0)
                    val nearest = tabAdapter!!.sessions[idx]
                    tabAdapter!!.setSelected(idx)
                    closeSession(item.sessionId, nearest.sessionId)
                }
            }
        }.also(binding.rvOpenFiles::setAdapter)
        ItemTouchHelper(TabItemMoveCallbackListener(adapter))
            .attachToRecyclerView(binding.rvOpenFiles)
        return adapter
    }

    private fun openDebugRoomDrawer() {
        binding.root.openDrawer(GravityCompat.END, true)
        //binding.root.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.END)
    }

    private fun snackbar(text: String, e: Throwable? = null) {
        Snackbar.make(binding.root, text, Snackbar.LENGTH_SHORT).apply {
            if (e != null) {
                setAction("자세히") {
                    binding.root.context.showErrorLogDialog(getProject().info.name + " 에러 로그", e)
                }
            }
            animationMode = Snackbar.ANIMATION_MODE_SLIDE
        }.show()
    }

    private fun updateColor() {
        setupHotKeys(clear = true)

        val color = theme.toolbarColor ?: getColor(R.color.background)
        val textColor =
            if (theme.toolbarColor != null)
                color { "#FFFFFF" }
            else
                getColor(R.color.text)

        supportActionBar!!.setBackgroundDrawable(ColorDrawable(color))
        binding.rvOpenFiles.setBackgroundColor(color)
        val stateList = ColorStateList.valueOf(color)
        binding.bottomSheet.apply {
            root.backgroundTintList = stateList
            cardViewTopRadius.setCardBackgroundColor(stateList)
            cardView2.setCardBackgroundColor(textColor)
        }
        binding.scrollViewHotKeys.backgroundTintList = stateList
    }

    @Suppress("SameParameterValue")
    private fun setupHotKeys(clear: Boolean = false) {
        if (clear)
            binding.layoutHotKeys.removeAllViewsInLayout()

        val widthDP = dp(36)
        val textPadding = dp(8)
        val backgroundColor = ColorStateList.valueOf(theme.toolbarColor ?: getColor(R.color.transparent))
        val textColor = ColorStateList.valueOf(if (theme.toolbarColor != null) color { "#FFFFFF" } else getColor(R.color.text))

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
                setOnClickListener { view ->
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    when(key) {
                        '↺' -> undo()
                        '↻' -> redo()
                        else -> appendText(key.toString())
                    }
                }
            }
            binding.layoutHotKeys.addView(textView)
        }
    }

    private fun setHotKeyVisibility(visible: Boolean) {
        binding.bottomSheet.cardViewTopRadius.radius = 0f
        val (peekHeight, visibility) = when(visible) {
            true -> dp(64) to View.VISIBLE
            false -> dp(24) to View.GONE
        }
        BottomSheetBehavior
            .from(binding.bottomSheet.rootBottomSheet)
            .setPeekHeight(peekHeight, true)
        binding.scrollViewHotKeys.visibility = visibility
        binding.layoutHotKeys.visibility = visibility
    }

    private fun getStructure() = config {
        category {
            id = "e_general"
            title = translate {
                Locale.ENGLISH { "General" }
                Locale.KOREAN  { "일반" }
            }
            textColor = getColor(R.color.text)
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
                        Locale.ENGLISH { "Bypass Escape" }
                        Locale.ENGLISH { "ESC 키 입력 무시" }
                        icon = Icon.EXIT_TO_APP
                        defaultValue = true
                        setOnValueChangedListener { _, isEnabled ->
                            bypassEscape = isEnabled
                        }
                    }
                }
                val themes = Theme.values()
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
                    setOnItemSelectedListener { _, index ->
                        val selectedTheme = themes[index]
                        theme = selectedTheme
                        setTheme(selectedTheme)
                        updateColor()
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
            textColor = getColor(R.color.text)
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

    private fun saveCode() {
        var savedCount = 0
        for ((index, session) in tabAdapter!!.sessions.withIndex()) {
            if (session.isUpdated && session.code != null) {
                session.isUpdated = false
                tabAdapter?.notifyItemChanged(index)
                saveCode(session.fileName, session.code!!)
                savedCount++
            }
        }
        /*
        if (isCodeChanged) {
            saveCode(getProject().info.mainScript, code)
            /*
            if (fileDir.isFile) fileDir.writeText(this.code)
            else logger.error { "Destination '${fileDir.path}' is not a file") }
             */
            isCodeChanged = false
        }
         */
        Snackbar.make(window.decorView.findViewById(android.R.id.content), "${savedCount}개 파일 저장 완료", Snackbar.LENGTH_LONG).show()
    }

    private fun beautifyCode() =
        executeScript("beautifyCode()")

    private fun resetUndoStack() =
        executeScript("resetUndoStack()")

    private fun encode(text: String): String =
        Base64.encodeToString(Uri.encode(text, "utf-8").toByteArray(), 0)

    private fun executeScript(script: String) =
        codeView?.post { codeView!!.loadUrl("javascript:$script") }

    private fun setSession(session: EditorSession) {
        setSession(session.sessionId, session.language, session.code!!)
    }

    private fun setSession(sessionId: Int, lang: Language, code: String) =
        executeScript("""setSession("$sessionId", "${lang.editorName}", "${encode(code)}")""")

    private fun closeSession(sessionId: Int, openSessionId: Int?) =
        executeScript("""closeSession("$sessionId", "$openSessionId")""")

    private fun setTheme(theme: Theme) =
        executeScript("""setTheme("${theme.editorName}")""")

    @Suppress("unused")
    private fun setLanguage(lang: Language) =
        executeScript("""setLanguage("${lang.editorName}")""")

    private fun appendText(text: String) =
        executeScript("""appendText("${encode(text)}")""")

    private fun setChanged(changed: Boolean) =
        executeScript("""setChanged("$changed")""")

    private fun undo() =
        executeScript("undo()")

    private fun redo() =
        executeScript("redo()")

    internal fun requestAnnotations() =
        executeScript("requestAnnotations()")

    internal fun gotoLine(row: Int, column: Int) =
        executeScript("_gotoLine($row, $column)")

    private fun confirmEditorExit(onExit: () -> Unit) {
        if (isCodeUpdated) {
            showConfirmDialog(
                context = this,
                title = translate {
                    Locale.ENGLISH { "⚠️ There are changes unsaved" }
                    Locale.KOREAN  { "⚠️ 저장하지 않은 변경사항이 있어요" }
                },
                message = translate {
                    Locale.ENGLISH { "There are files or changes that are unsaved yet, force close?\n" +
                            "⚠️ All unsaved changes will be discarded." }
                    Locale.KOREAN  { "아직 저장하지 않은 코드가 있어요. 수정을 종료할까요?\n" +
                            "⚠️ 모든 저장되지 않은 수정 사항은 사라집니다." }
                },
                onDismiss = { confirm ->
                    if (confirm)
                        onExit()
                }
            )
        } else
            onExit()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_save -> saveCode()
            R.id.menu_recompile -> compileProject()
            R.id.code_beautify -> beautifyCode()
            R.id.menu_debug_room -> {
                if (!getProject().isCompiled) {
                    Snackbar.make(binding.root,
                        translate {
                            Locale.ENGLISH { "Project isn't compiled yet." }
                            Locale.KOREAN  { "아직 프로젝트가 컴파일되지 않았어요." }
                        }, Snackbar.LENGTH_SHORT).show()
                } else {
                    when(layoutMode) {
                        LAYOUT_TABLET -> openDebugRoomDrawer()
                        LAYOUT_DEFAULT -> startActivityWithExtra(
                            DebugRoomActivity::class.java,
                            mapOf(DebugRoomActivity.EXTRA_PROJECT_NAME to getProject().info.name)
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

    override fun onBackPressed() {
        if (binding.root.isDrawerOpen(GravityCompat.END))
            binding.fragmentContainerDebugRoom!!
                .getFragment<DebugRoomFragment>()
                .onBackPressed().also { wasOpen ->
                    if (!wasOpen)
                        binding.root.closeDrawer(GravityCompat.END)
                }
        else
            confirmEditorExit { super.onBackPressed() }
    }

    companion object {
        const val EXTRA_SHOW_FILE_TREE  = "showFileTree"
        const val EXTRA_SHOW_DEBUG_CHAT = "showDebugChat"

        const val ENTRY_POINT = "file:///android_asset/editor/index.html"
        private const val HOT_KEYS = "↺↻[]{}()<>;=.'\"+-*/\\:&|"
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
        object Light: ThemeStyle()
        object Dark: ThemeStyle()
    }

    @Suppress("unused")
    enum class Theme(
        val shownName: String,
        val editorName: String,
        val themeStyle: ThemeStyle,
        val toolbarColor: Int? = null,
    ) {
        // Light themes
        GITHUB("Github", "github", ThemeStyle.Light, color { "#e2f1fe" }),
        ECLIPSE("Eclipse", "eclipse", ThemeStyle.Light),
        CLOUDS("Clouds", "clouds", ThemeStyle.Light),
        XCODE("XCode", "xcode", ThemeStyle.Light),
        CHROME("Chrome", "chrome", ThemeStyle.Light),
        DREAMWEAVER("Dreamweaver","dreamweaver", ThemeStyle.Light),
        IPLASTIC("IPlastic", "iplastic", ThemeStyle.Light),
        KATZEN_MILCH("Katzenmilch", "katzenmilch", ThemeStyle.Light, color { "#e2f1fe" }),
        SOLARIZED_LIGHT("Solarized-Light", "solarized_light", ThemeStyle.Light),
        TOMORROW("Tomorrow", "tomorrow", ThemeStyle.Light),

        // Dark themes
        AMBIANCE("Ambiance", "ambiance", ThemeStyle.Dark),
        CLOUDS_MIDNIGHT("Clouds-Midnight", "clouds_midnight", ThemeStyle.Dark),
        PASTEL_ON_DARK("Pastel On Dark", "pastel_on_dark", ThemeStyle.Dark),
        SOLARIZED_DARK("Solarized-Dark", "solarized_dark", ThemeStyle.Dark),
        TERMINAL("Terminal", "terminal", ThemeStyle.Dark),
        TOMORROW_NIGHT("Tomorrow Night", "tomorrow_night", ThemeStyle.Dark, color { "#3e4047" }),
        TOMORROW_NIGHT_EIGHTIES("Tomorrow Night-Eighties", "tomorrow_night_eighties", ThemeStyle.Dark, color { "#535153" }),
        MONOKAI("Monokai", "monokai", ThemeStyle.Dark),
        GREEN_ON_BLACK("Green on black", "green_on_black", ThemeStyle.Dark),
        KR_THEME("KR Theme", "kr_theme", ThemeStyle.Dark, color { "#322129" }),
        NORD_DARK("Nord Dark", "nord_dark", ThemeStyle.Dark, color { "#92A9BD" }),
        ONE_DARK("One Dark", "one_dark", ThemeStyle.Dark, color { "#656980" }),
        TWILIGHT("Twilight", "twilight", ThemeStyle.Dark),
    }
}