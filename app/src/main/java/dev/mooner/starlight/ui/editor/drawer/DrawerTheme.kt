package dev.mooner.starlight.ui.editor.drawer

import android.content.res.Configuration
import android.view.LayoutInflater
import androidx.appcompat.view.ContextThemeWrapper
import androidx.fragment.app.Fragment
import dev.mooner.starlight.R
import dev.mooner.starlight.ui.editor.DefaultEditorActivity

// The drawer sits on top of the editor, so it follows the editor theme's light/dark style
// instead of the app's day/night setting.
internal fun Fragment.editorThemedInflater(inflater: LayoutInflater): LayoutInflater {
    val isDark = (activity as? DefaultEditorActivity)?.isDarkTheme ?: return inflater
    val config = Configuration(inflater.context.resources.configuration).apply {
        val night = if (isDark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
    }
    val context = ContextThemeWrapper(inflater.context, R.style.Theme_StarLight).apply {
        applyOverrideConfiguration(config)
    }
    return inflater.cloneInContext(context)
}
