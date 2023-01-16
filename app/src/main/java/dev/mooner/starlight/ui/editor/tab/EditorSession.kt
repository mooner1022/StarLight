package dev.mooner.starlight.ui.editor.tab

import dev.mooner.starlight.ui.editor.DefaultEditorActivity

@kotlinx.serialization.Serializable
data class EditorSession(
    val sessionId: Int,
    val fileName: String,
    val language: DefaultEditorActivity.Language,
    var code: String?,
    @kotlinx.serialization.Transient
    var isUpdated: Boolean = false
) {

    override fun equals(other: Any?): Boolean {
        return other is EditorSession && other.sessionId == sessionId
    }

    override fun hashCode(): Int {
        var result = sessionId
        result = 31 * result + fileName.hashCode()
        result = 31 * result + language.hashCode()
        result = 31 * result + (code?.hashCode() ?: 0)
        return result
    }
}
