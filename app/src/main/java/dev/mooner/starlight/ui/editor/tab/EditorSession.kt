package dev.mooner.starlight.ui.editor.tab

import dev.mooner.starlight.ui.editor.DefaultEditorActivity

@kotlinx.serialization.Serializable
data class EditorSession(
    val sessionId: Int,
    val fileName: String,
    val language: DefaultEditorActivity.Language,
    var code: String?,
    @kotlinx.serialization.Transient
    var isUpdated: Boolean = false,
    @kotlinx.serialization.Transient
    var isLoaded: Boolean = false,
) {

    override fun equals(other: Any?): Boolean {
        return other is EditorSession && other.sessionId == sessionId
    }

    override fun hashCode(): Int =
        sessionId
}
