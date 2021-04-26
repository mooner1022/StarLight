package com.mooner.starlight.ui.editor

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import com.amrdeveloper.codeview.CodeView
import com.google.android.material.snackbar.Snackbar
import com.mooner.starlight.R
import kotlinx.android.synthetic.main.activity_editor.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class EditorActivity : AppCompatActivity() {
    companion object {
        //private const val MONACO_DIRECTORY = "file:///android_asset/monaco/index.html"
    }

    private lateinit var fileDir: File
    private lateinit var name: String
    //private lateinit var monaco: WebView
    private lateinit var codeView: CodeView
    private lateinit var orgCode: String
    private var isCodeChanged = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor)

        setSupportActionBar(toolbar_editor)
        name = intent.getStringExtra("title")!!
        supportActionBar!!.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_round_arrow_left_24)
            title = name
        }
        fileDir = File(intent.getStringExtra("fileDir")
                ?: throw IllegalArgumentException("No file directory passed to editor"))

        codeView = findViewById(R.id.codeView)
        with(codeView) {
            setTabWidth(4)
            CoroutineScope(Dispatchers.IO).launch {
                orgCode = fileDir.readText()
                setText(orgCode)
            }
            addTextChangedListener {
                isCodeChanged = (it!!.toString() != orgCode)
            }
        }

        /*
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_save -> {
                if (isCodeChanged) {
                    if (fileDir.isFile) fileDir.writeText(codeView.text.toString())
                    else throw IllegalArgumentException("Target [${fileDir.path}] is not a file")
                    isCodeChanged = false
                }
                Snackbar.make(window.decorView.findViewById(android.R.id.content), "$name 저장 완료!", Snackbar.LENGTH_LONG).show()
            }
            android.R.id.home -> { // 메뉴 버튼
                finish()
            }
            //R.id.text_undo ->
            //R.id.text_redo ->
        }
        return super.onOptionsItemSelected(item)
    }
}