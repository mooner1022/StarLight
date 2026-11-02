package dev.mooner.starlight.agent.provider

import dev.mooner.starlight.agent.model.AgentJson
import dev.mooner.starlight.agent.model.AttachmentKind
import dev.mooner.starlight.agent.model.ChatMessage
import dev.mooner.starlight.agent.model.Part
import dev.mooner.starlight.agent.model.Role
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

/** Helpers the request builders of all protocols share. */
internal object RequestContent {

    /** Largest text attachment put into a prompt; the rest is cut with a note. */
    private const val MAX_TEXT_ATTACHMENT_CHARS = 200_000

    fun base64(bytes: ByteArray): String =
        java.util.Base64.getEncoder().encodeToString(bytes)

    fun dataUrl(mimeType: String, bytes: ByteArray): String =
        "data:$mimeType;base64,${base64(bytes)}"

    /** A text attachment as prompt text, with its name. */
    fun textAttachment(attachment: Part.Attachment, source: AttachmentSource): String {
        val text = source.bytes(attachment).decodeToString()
        val body = if (text.length > MAX_TEXT_ATTACHMENT_CHARS)
            text.take(MAX_TEXT_ATTACHMENT_CHARS) + "\n... (cut at $MAX_TEXT_ATTACHMENT_CHARS characters)"
        else
            text
        return "<attachment id=\"${attachment.id}\" name=\"${attachment.name}\">\n$body\n</attachment>"
    }

    /** Names a binary attachment the model can't read itself but can hand to a tool. */
    fun fileAttachmentNote(attachment: Part.Attachment): String =
        "<attachment id=\"${attachment.id}\" name=\"${attachment.name}\" type=\"${attachment.mimeType}\" " +
            "size=\"${attachment.size}\">(binary file; save_attachment can copy it into a project)</attachment>"

    /** Tells the model the id of an image or PDF it sees, so that it can save it with a tool. */
    fun attachmentLabel(attachment: Part.Attachment): String =
        "<attachment id=\"${attachment.id}\" name=\"${attachment.name}\" type=\"${attachment.mimeType}\"/>"

    /** Tool call arguments as a JSON object, or null if the model wrote something that isn't one. */
    fun parseArguments(arguments: String): JsonObject? =
        runCatching { AgentJson.parseToJsonElement(arguments.ifBlank { "{}" }).jsonObject }.getOrNull()

    /** The tool result for arguments that weren't valid JSON, built with the JSON library so that quotes stay escaped. */
    fun invalidJsonResult(arguments: String): String =
        buildJsonObject { put("INVALID_JSON", JsonPrimitive(arguments)) }.toString()

    /** Messages the model sees, without app-only notices and empty turns. */
    fun visibleMessages(messages: List<ChatMessage>): List<ChatMessage> =
        messages.mapNotNull { message ->
            val parts = message.parts.filter { it !is Part.Notice }
            when {
                parts.isEmpty() && message.raw == null -> null
                message.role == Role.ASSISTANT && message.raw == null && parts.none { it is Part.Text || it is Part.ToolCall } -> null
                else -> message.copy(parts = parts)
            }
        }

    fun isImage(attachment: Part.Attachment): Boolean =
        attachment.kind == AttachmentKind.IMAGE
}
