package dev.mooner.starlight.ui.agent

import android.app.Activity
import android.os.Bundle

/**
 * Where the browser's "back to StarLight" link after ChatGPT sign-in lands. Opening it brings StarLight's task to the
 * front; it then closes, leaving the screen the user signed in from.
 */
class AgentReturnActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
