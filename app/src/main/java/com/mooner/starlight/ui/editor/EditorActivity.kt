package com.mooner.starlight.ui.editor

//import io.github.rosemoe.editor.utils.CrashHandler

import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.mooner.starlight.R
import io.github.rosemoe.editor.langs.desc.JavaScriptDescription
import io.github.rosemoe.editor.langs.java.JavaLanguage
import io.github.rosemoe.editor.langs.s5droid.S5droidAutoComplete
import io.github.rosemoe.editor.langs.universal.UniversalLanguage
import io.github.rosemoe.editor.widget.CodeEditor
import io.github.rosemoe.editor.widget.EditorColorScheme
import io.github.rosemoe.editor.widget.schemes.*
import kotlinx.android.synthetic.main.activity_editor.*
import java.io.*


class EditorActivity : AppCompatActivity() {
    private lateinit var editor: CodeEditor
    private lateinit var panel: LinearLayout
    private lateinit var search: EditText
    private lateinit var replace:EditText
    private lateinit var fileDir: File
    private lateinit var name: String

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

        fileDir = File(intent.getStringExtra("fileDir")?:throw IllegalArgumentException("No file directory passed to editor"))

        S5droidAutoComplete.init(this)
        editor = findViewById(R.id.editor)
        panel = findViewById(R.id.search_panel)
        search = findViewById(R.id.search_editor)
        replace = findViewById(R.id.replace_editor)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun afterTextChanged(editable: Editable) {
                editor.searcher.search(editable.toString())
            }
        })
        editor.typefaceText = Typeface.MONOSPACE
        editor.isOverScrollEnabled = false
        editor.textSizePx = 50f
        editor.setEditorLanguage(UniversalLanguage(JavaScriptDescription()))
        editor.setNonPrintablePaintingFlags(CodeEditor.FLAG_DRAW_WHITESPACE_LEADING or CodeEditor.FLAG_DRAW_LINE_SEPARATOR)
        editor.setText(intent.getStringExtra("code") ?: "")
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.editor_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_save -> {
                if (fileDir.isFile) fileDir.writeText(editor.text.toString())
                else throw IllegalArgumentException("Target [${fileDir.path}] is not a file")
                Snackbar.make(window.decorView.findViewById(android.R.id.content), "$name 저장 완료!", Snackbar.LENGTH_LONG).show()
            }
            android.R.id.home->{ // 메뉴 버튼
                finish()
            }
            R.id.text_undo -> editor.undo()
            R.id.text_redo -> editor.redo()
            R.id.code_navigation -> {
                val labels = editor.textAnalyzeResult.navigation
                if (labels == null) {
                    Toast.makeText(this, R.string.navi_err_msg, Toast.LENGTH_SHORT).show()
                } else {
                    val items = arrayOfNulls<CharSequence>(labels.size)
                    var i = 0
                    while (i < labels.size) {
                        items[i] = labels[i].label
                        i++
                    }
                    AlertDialog.Builder(this)
                        .setTitle(R.string.code_navi)
                        .setSingleChoiceItems(items, 0) { dialog, j ->
                            editor.jumpToLine(labels[j].line)
                            dialog.dismiss()
                        }
                        .setPositiveButton(android.R.string.cancel, null)
                        .show()
                }
            }
            R.id.code_format -> editor.formatCodeAsync()
            R.id.search_panel_st -> if (panel.visibility == View.GONE) {
                replace.setText("")
                search.setText("")
                editor.searcher.stopSearch()
                panel.visibility = View.VISIBLE
            } else {
                panel.visibility = View.GONE
                editor.searcher.stopSearch()
            }
            R.id.search_am -> {
                replace.setText("")
                search.setText("")
                editor.searcher.stopSearch()
                editor.beginSearchMode()
            }
            R.id.switch_colors -> {
                val themes = arrayOf(
                    "Default", "GitHub", "Eclipse",
                    "Darcula", "VS2019", "NotepadXX", "HTML"
                )
                AlertDialog.Builder(this)
                    .setTitle(R.string.color_scheme)
                    .setSingleChoiceItems(themes, -1) { dialog, which ->
                        when (which) {
                            0 -> editor.colorScheme = EditorColorScheme()
                            1 -> editor.colorScheme = SchemeGitHub()
                            2 -> editor.colorScheme = SchemeEclipse()
                            3 -> editor.colorScheme = SchemeDarcula()
                            4 -> editor.colorScheme = SchemeVS2019()
                            5 -> editor.colorScheme = SchemeNotepadXX()
                            6 -> editor.colorScheme = HTMLScheme()
                        }
                        dialog.dismiss()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            R.id.text_wordwrap -> {
                item.isChecked = !item.isChecked
                editor.isWordwrap = item.isChecked
            }
            R.id.editor_line_number -> {
                editor.isLineNumberEnabled = !editor.isLineNumberEnabled
                item.isChecked = editor.isLineNumberEnabled
            }
        }
        return super.onOptionsItemSelected(item)
    }

    fun gotoNext(view: View) {
        try {
            editor.searcher.gotoNext()
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        }
    }

    fun gotoLast(view: View) {
        try {
            editor.searcher.gotoLast()
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        }
    }

    fun replace(view: View) {
        try {
            editor.searcher.replaceThis(replace.text.toString())
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        }
    }

    fun replaceAll(view: View) {
        try {
            editor.searcher.replaceAll(replace.text.toString())
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        }
    }
}