package dev.mooner.starlight.ui.editor

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.ActivityEditorBinding
import dev.mooner.starlight.plugincore.logger.Logger
import java.io.File

class EditorActivity : AppCompatActivity() {

    companion object {
        private const val T = "EditorActivity"
        const val ENTRY_POINT = "file:///android_asset/editor/index.html"
    }

    private lateinit var fileDir: File
    private lateinit var name: String
    //private lateinit var monaco: WebView
    private lateinit var codeView: WebView
    private lateinit var orgCode: String
    private var code: String = ""
    private var isCodeChanged = false
    private lateinit var binding: ActivityEditorBinding

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbarEditor)
        name = intent.getStringExtra("title")!!
        supportActionBar!!.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_round_arrow_left_24)
            title = name
        }
        fileDir = File(intent.getStringExtra("fileDir")
                ?: throw IllegalArgumentException("No file directory passed to editor"))

        orgCode = fileDir.readText()
        code = orgCode
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
                        /*
                        val prcCode = orgCode
                            .replace(""""""", """\"""")
                            .replace("\n", "\\n")

                         */
                        val prcCode = Base64.encodeToString(Uri.encode(orgCode, "utf-8").toByteArray(), 0)
                        println("prcCode = $prcCode")
                        codeView.post {
                            codeView.loadUrl(
                                """javascript:setCode("$prcCode")"""
                            )
                        }
                    }

                    @JavascriptInterface
                    override fun onContentChanged(code: String?) {
                        println("onContentChanged")
                        isCodeChanged = code != orgCode
                        if (isCodeChanged && code != null) {
                            this@EditorActivity.code = code
                        }
                    }
                }, "WebviewCallback")
            }
            //loadUrl(indexHtml.path)
            loadUrl(ENTRY_POINT)
        }

        /*
        with(binding.codeView) {
            setAdapter(
                ArrayAdapter(
                    this@EditorActivity,
                    R.layout.code_autocomplete_item,
                    R.id.autoCompleteText,
                    context.getStringArray(R.array.js_keywords)
                )
            )
            JSSyntaxManager.applyMonokaiTheme(
                this@EditorActivity,
                this,
            )
            setTabWidth(1)
            CoroutineScope(Dispatchers.IO).launch {
                orgCode = fileDir.readText()
                setText(orgCode)
            }
            //setSyntaxPatternsMap(
            //        mapOf(
                            //"function|throw".toPattern() to R.color.code_orange,
                            //"\"(.*?)\"|'(.*?)'".toPattern() to R.color.code_string,
                            //"^val|let|const".toPattern() to R.color.code_orange,
            //        )
            //)
            reHighlightSyntax()
            //addErrorLine(1, R.color.code_error)
            addTextChangedListener {
                isCodeChanged = (it!!.toString() != orgCode)
            }
        }

        monaco = findViewById(R.id.monacoWebView)
        with(monaco) {
            settings.apply {
                //builtInZoomControls = false
                javaScriptEnabled = true
                //loadWithOverviewMode = true
                //displayZoomControls = false
                allowFileAccess = true
                allowContentAccess = true
                webViewClient = WebViewClientImpl()
                webChromeClient = WebChromeClient()
                //setSupportZoom(false)
                cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                addJavascriptInterface(object : MonacoCallback {
                    @JavascriptInterface
                    override fun onEditorCreated() {
                        println("onEditorCreated")
                        setCode(orgCode)
                    }

                    @JavascriptInterface
                    override fun onContentChanged(code: String?) {
                        println("onContentChanged")
                        isCodeChanged = code != orgCode
                    }
                }, "MonacoCallback")
            }
            loadUrl(MONACO_DIRECTORY)
        }
        */
    }

    /*
    private fun getCode(callback: (code: String) -> Unit) {
        monaco.post {
            monaco.evaluateJavascript("javascript:getCode()") {
                var returnValue: String = it
                if (returnValue.startsWith("\"")) {
                    println("1")
                    returnValue = returnValue.replaceFirst("\"", "")
                }
                println("val: $this")
                if (returnValue.endsWith("\"")) {
                    println("2")
                    returnValue = returnValue.dropLast(1)
                }
                println("processed: $returnValue")
                callback(returnValue)
            }
        }
    }

    private fun setCode(code: String) {
        monaco.post {
            monaco.loadUrl("""javascript:setCode("$code")""")
        }
    }
    */

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.editor_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN &&
            event.isCtrlPressed &&
            event.keyCode == KeyEvent.KEYCODE_S) {
            saveCode()
        }
        return super.dispatchKeyEvent(event)
    }

    private fun saveCode() {
        if (isCodeChanged) {
            if (fileDir.isFile) fileDir.writeText(this.code)
            else Logger.e(T, "Destination '${fileDir.path}' is not a file")
            isCodeChanged = false
        }
        Snackbar.make(window.decorView.findViewById(android.R.id.content), "$name 저장 완료", Snackbar.LENGTH_LONG).show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_save -> saveCode()
            android.R.id.home -> finish()
            //R.id.text_undo ->
            //R.id.text_redo ->
        }
        return super.onOptionsItemSelected(item)
    }
}