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
    /**
     * The file's size and time as they were when the tab last read or wrote it, to tell when something else changed
     * it since: StarLight AI, a script, another app. [NO_STAMP] before the tab has read it.
     */
    @kotlinx.serialization.Transient
    var diskStamp: Long = NO_STAMP,
) {

    override fun equals(other: Any?): Boolean {
        return other is EditorSession && other.sessionId == sessionId
    }

    override fun hashCode(): Int =
        sessionId

    companion object {
        const val NO_STAMP = -1L
    }
}
