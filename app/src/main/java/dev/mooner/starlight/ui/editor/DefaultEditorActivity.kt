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
import androidx.core.view.updatePadding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.ActivityDefaultEditorBinding
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.config.config
import dev.mooner.starlight.plugincore.editor.CodeEditorActivity
import dev.mooner.starlight.plugincore.logger.Logger
import dev.mooner.starlight.plugincore.utils.color
import dev.mooner.starlight.ui.config.ConfigAdapter
import dev.mooner.starlight.ui.dialog.DialogUtils
import dev.mooner.starlight.utils.applyLayoutParams
import dev.mooner.starlight.utils.dp
import dev.mooner.starlight.utils.showErrorLogDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.File
import kotlin.properties.Delegates.notNull

class DefaultEditorActivity : CodeEditorActivity() {

    private lateinit var name: String
    private lateinit var codeView: WebView

    private var code: String by notNull()
    private var isCodeChanged = false
    private lateinit var binding: ActivityDefaultEditorBinding

    private var theme: Theme = Theme.NORD_DARK

    private var configAdapter: ConfigAdapter? = null

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDefaultEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbarEditor)

        name = intent.getStringExtra("title")!!
        supportActionBar!!.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_round_arrow_left_24)
            title = name
        }

        val themeOrdinal = Session.globalConfig.category("e_general").getInt("theme", Theme.NORD_DARK.ordinal)
        theme = Theme.values()[themeOrdinal]

        binding.scrollViewHotKeys.isHorizontalScrollBarEnabled = false

        updateColor()
        //setupHotKeys()

        configAdapter = ConfigAdapter.Builder(this) {
            bind(binding.bottomSheet.configRecyclerView)
            structure(::getStructure)
            savedData(Session.globalConfig.getAllConfigs())
            onConfigChanged { parentId, id, _, data ->
                Session.globalConfig.edit {
                    category(parentId).setAny(id, data)
                }
            }
        }.build()
        binding.bottomSheet.configRecyclerView

        val ext = File(getProject().info.mainScript).extension
        val lang = let {
            for (lang in Language.values()) {
                if (ext in lang.fileExt) return@let lang
            }
            return@let null
        } ?: let {
            Logger.w("Failed to auto-detect language from file extension: $ext")
            Language.JAVASCRIPT
        }
        Logger.v("File extension: $ext, Auto-detected language: $lang")

        code = readCode(getProject().info.mainScript) ?: getProject().getLanguage().defaultCode
        codeView = findViewById(R.id.editorWebView)
        with(codeView) {
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
                addJavascriptInterface(object : WebviewCallback {
                    @JavascriptInterface
                    override fun onLoadComplete() {
                        setLanguage(lang)
                        setTheme(theme)
                        val prcCode = encode(code)
                        executeScript("""setCode("$prcCode")""")
                        resetUndoStack()
                    }

                    @JavascriptInterface
                    override fun onContentChanged(code: String?) {
                        if (code != null && code != this@DefaultEditorActivity.code) {
                            isCodeChanged = true
                            this@DefaultEditorActivity.code = code
                        }
                    }
                }, "WebviewCallback")
            }
            loadUrl(ENTRY_POINT)
        }

        val dp20 = dp(20)
        BottomSheetBehavior.from(binding.bottomSheet.root)
            .addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {}

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
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
            else if (event.keyCode == KeyEvent.KEYCODE_ESCAPE) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun compileProject() {
        saveCode()
        val project = getProject()

        snackbar("프로젝트 [${project.info.name}] 컴파일 중...")
        CoroutineScope(Dispatchers.Default).launch {
            try {
                getProject().compileAsync(throwException = true)
                    .collect()
                snackbar("프로젝트 [${project.info.name}] 컴파일 완료!")
            } catch (e: Exception) {
                snackbar("프로젝트 [${project.info.name}] 컴파일 실패:\n$e", e)
                e.printStackTrace()
            }
        }
    }

    private fun snackbar(text: String, e: Throwable? = null) {
        Snackbar.make(binding.root, text, Snackbar.LENGTH_SHORT).apply {
            if (e != null) {
                setAction("자세히 보기") {
                    binding.root.context.showErrorLogDialog(getProject().info.name + " 에러 로그", e)
                }
            }
        }.show()
    }

    private fun updateColor() {
        setupHotKeys(clear = true)
        if (theme.toolbarColor != null) {
            theme.toolbarColor!!.let { color ->
                supportActionBar!!.setBackgroundDrawable(ColorDrawable(color))
                val stateList = ColorStateList.valueOf(color)
                binding.bottomSheet.apply {
                    root.backgroundTintList = stateList
                    cardViewTopRadius.setCardBackgroundColor(stateList)
                    cardView2.setCardBackgroundColor(color { "#FFFFFF" })
                }
                binding.scrollViewHotKeys.backgroundTintList = stateList
            }
        } else {
            supportActionBar!!.setBackgroundDrawable(ColorDrawable(getColor(R.color.main_purple)))
            val stateList = ColorStateList.valueOf(getColor(R.color.background))
            binding.bottomSheet.apply {
                root.backgroundTintList = stateList
                cardViewTopRadius.setCardBackgroundColor(stateList)
                cardView2.setCardBackgroundColor(getColor(R.color.text))
            }
            binding.scrollViewHotKeys.backgroundTintList = stateList
        }
    }

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
                applyLayoutParams {
                    width = widthDP
                    height = widthDP
                    updatePadding(top = textPadding)
                }
                isHapticFeedbackEnabled = true
                when(key) {
                    '↺' -> setOnClickListener { view ->
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        undo()
                    }
                    '↻' -> setOnClickListener { view ->
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        redo()
                    }
                    else -> setOnClickListener { view ->
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        appendText(key.toString())
                    }
                }
            }
            binding.layoutHotKeys.addView(textView)
        }
    }

    private fun getStructure() = config {
        category {
            id = "e_general"
            title = "일반"
            textColor = getColor(R.color.text)
            items {
                val themes = Theme.values()
                spinner {
                    id = "theme"
                    title = "테마"
                    items = themes.map { it.shownName }
                    setOnItemSelectedListener { _, index ->
                        val selectedTheme = themes[index]
                        theme = selectedTheme
                        setTheme(selectedTheme)
                        updateColor()
                    }
                }
            }
        }
    }

    private fun saveCode() {
        if (isCodeChanged) {
            saveCode(getProject().info.mainScript, code)
            /*
            if (fileDir.isFile) fileDir.writeText(this.code)
            else Logger.e(T, "Destination '${fileDir.path}' is not a file")
             */
            isCodeChanged = false
        }
        Snackbar.make(window.decorView.findViewById(android.R.id.content), "$name 저장 완료", Snackbar.LENGTH_LONG).show()
    }

    private fun beautifyCode() =
        executeScript("beautifyCode()")

    private fun resetUndoStack() =
        executeScript("resetUndoStack()")

    private fun encode(text: String): String =
        Base64.encodeToString(Uri.encode(text, "utf-8").toByteArray(), 0)

    private fun executeScript(script: String) =
        codeView.post { codeView.loadUrl("javascript:$script") }

    private fun setTheme(theme: Theme) =
        executeScript("""setTheme("${theme.editorName}")""")

    private fun setLanguage(lang: Language) =
        executeScript("""setLanguage("${lang.editorName}")""")

    private fun appendText(text: String) =
        executeScript("""appendText("${encode(text)}")""")

    private fun undo() =
        executeScript("undo()")

    private fun redo() =
        executeScript("redo()")

    private fun confirmEditorExit(onExit: () -> Unit) {
        if (isCodeChanged) {
            DialogUtils.showConfirmDialog(
                context = this,
                title = "⚠️ 저장하지 않은 코드가 있어요",
                message = "아직 저장하지 않은 코드가 있어요. 수정을 종료할까요?\n⚠️ 모든 저장되지 않은 수정 사항은 사라집니다.",
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
            android.R.id.home -> confirmEditorExit(::finish)
            //R.id.text_undo ->
            //R.id.text_redo ->
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() =
        confirmEditorExit { super.onBackPressed() }

    companion object {
        const val ENTRY_POINT = "file:///android_asset/editor/index.html"
        private const val HOT_KEYS = "↺↻[]{}()<>;=.'\"+-*/\\:&|"
    }

    @Suppress("unused")
    enum class Language(
        val editorName: String,
        vararg val fileExt: String
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
        JAVASCRIPT("javascript", "js"),
        JSON("json", "json"),
        KOTLIN("kotlin", "kt", "kts"),
        LUA("lua", "lua"),
        MARKDOWN("markdown", "md"),
        OBJECTIVE_C("objectivec", "m", "mm"),
        PASCAL("pascal", "pas"),
        PERL("perl", "pl"),
        PHP("php", "php"),
        PLAIN_TEXT("plain_text", "txt"),
        PYTHON("python", "py"),
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

        // Dark themes
        AMBIANCE("Ambiance", "ambiance", ThemeStyle.Dark),
        CLOUDS_MIDNIGHT("Clouds-Midnight", "clouds_midnight", ThemeStyle.Dark),
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