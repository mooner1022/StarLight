package com.mooner.starlight.languages

import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.eclipsesource.v8.NodeJS
import com.eclipsesource.v8.V8
import com.mooner.starlight.R
import com.mooner.starlight.Utils.Companion.addClass
import com.mooner.starlight.core.ApplicationSession
import com.mooner.starlight.plugincore.language.Language
import com.mooner.starlight.plugincore.language.LanguageConfig
import com.mooner.starlight.plugincore.language.MethodBlock

class JSNode: Language {
    override val id: String
        get() = "NodeJS"
    override val name: String
        get() = "자바스크립트 (NodeJS)"
    override val icon: Drawable
        get() = ContextCompat.getDrawable(ApplicationSession.context, R.drawable.ic_nodejs)!!
    override val requireRelease: Boolean
        get() = true
    override val configList: List<LanguageConfig>
        get() = listOf()

    override fun onConfigChanged(changed: Map<String, Any>) {

    }

    override fun compile(code: String, methods: Array<MethodBlock>): Any {
        val nodejs = NodeJS.createNodeJS()
        return nodejs
    }

    override fun release(engine: Any) {
        val nodejs = engine as NodeJS
        nodejs.release()
    }

    override fun execute(engine: Any, methodName: String, args: Array<Any>) {
        val nodejs = NodeJS.createNodeJS()
        while(nodejs.isRunning) {
            nodejs.handleMessage()
        }
    }
}