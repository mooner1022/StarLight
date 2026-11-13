/*
 * EditorKeeper.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.editor

import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mooner.starlight.ui.editor.tab.EditorSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

/**
 * What an editor screen keeps while Android takes it down to make it anew, as on a rotation, a switch to dark mode
 * or a fold: the editor page itself, and with it everything typed and not saved, the undo history and the cursors,
 * which live in the page and nowhere else. The screen that comes in its place puts the same page into its layout,
 * whichever layout that is.
 *
 * The page is made with the application's context and reaches a screen only through [bridge], so it holds on to
 * no screen that is gone. It ends when the editor is closed.
 */
internal class EditorKeeper : ViewModel() {

    var webView: WebView? = null

    /** The open tabs. The screen's tab strip shows this list and changes it. */
    val sessions: MutableList<EditorSession> = arrayListOf()

    /** Whether the page has loaded; nothing can be sent to it before. */
    @Volatile
    var isEditorReady: Boolean = false

    /** The file of the tab that was showing when the last screen went. */
    var selectedFile: String? = null

    /** The app's own checks of the page's scripts, for the languages that have a linter. */
    val lint = EditorLint(reply = { requestId, json ->
        // Text of another origin never reaches the script: the findings are JSON, and the ID a number.
        webView?.let { view -> view.post { webView?.evaluateJavascript("onNativeLint($requestId, ${json ?: "null"})", null) } }
    })

    val bridge = Bridge(lint)

    /**
     * Runs [block], which writes files, to its end even if the screen that asked goes meanwhile. The page has
     * already marked what is being written as saved: a write cut short by a rotation would leave text that is on
     * no disk looking saved.
     */
    suspend fun <T> write(block: () -> T): T =
        viewModelScope.async(Dispatchers.IO) { block() }.await()

    override fun onCleared() {
        lint.close()
        webView?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
        }
        webView = null
    }

    /**
     * What the editor page calls. The page outlives each screen, so it calls this, which passes the call on to the
     * screen there is, and keeps for the next one what arrives while there is none.
     */
    class Bridge(private val lint: EditorLint) {

        private var target: WebviewCallback? = null
        private val pending = ArrayList<(WebviewCallback) -> Unit>()

        /** [callback] is the screen now showing the page. It is handed what the page said while no screen was. */
        fun attach(callback: WebviewCallback) {
            val missed = synchronized(this) {
                target = callback
                pending.toList().also { pending.clear() }
            }
            missed.forEach { it(callback) }
        }

        fun detach(callback: WebviewCallback) = synchronized(this) {
            if (target === callback)
                target = null
        }

        private fun pass(call: (WebviewCallback) -> Unit) {
            val current = synchronized(this) {
                target ?: run {
                    pending += call
                    null
                }
            }
            current?.let(call)
        }

        @JavascriptInterface
        fun onLoadComplete() = pass { it.onLoadComplete() }

        @JavascriptInterface
        fun onDirtyChanged(sessionId: String, dirty: Boolean) = pass { it.onDirtyChanged(sessionId, dirty) }

        @JavascriptInterface
        fun onAnnotationUpdated(list: String) = pass { it.onAnnotationUpdated(list) }

        @JavascriptInterface
        fun onEditorBackground(color: String) = pass { it.onEditorBackground(color) }

        // Answered here, not by a screen: checks go on while there is none, and need none.

        /** Whether the app checks the scripts of the project that is open, rather than the page. */
        @JavascriptInterface
        fun lintsNatively(): Boolean = lint.handles()

        /** Asks for a check of [code], which the page is told of with `onNativeLint(requestId, findings)`. */
        @JavascriptInterface
        fun requestLint(requestId: Int, sessionId: String, code: String) = lint.request(requestId, sessionId, code)
    }
}
