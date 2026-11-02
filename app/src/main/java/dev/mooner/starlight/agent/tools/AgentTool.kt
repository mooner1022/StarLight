package dev.mooner.starlight.agent.tools

import dev.mooner.starlight.agent.model.Card
import dev.mooner.starlight.agent.model.Part
import dev.mooner.starlight.agent.provider.ToolSpec
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File

/** How much a tool can change; decides whether it asks first under the user's approval policy. */
enum class ToolRisk {
    READ,
    WRITE,
    /** Deletes something, or changes what runs on real chats. */
    DESTRUCTIVE,
}

/** What a tool call returns to the model, and the line the chat shows for it. */
class ToolOutput(
    val text: String,
    val isError: Boolean = false,
    val summary: String? = null,
    /** Something to put in front of the user, such as a file or project card. */
    val card: Card? = null,
    /** Images for the model to see with the result, added with [ToolContext.addAttachment]. */
    val images: List<Part.Attachment> = emptyList(),
) {
    companion object {
        fun error(message: String) = ToolOutput(message, isError = true, summary = message)
    }
}

/** A failure a tool reports to the model as its result, such as a missing file. */
class ToolException(message: String) : Exception(message)

/** What a running tool can reach besides its arguments. */
interface ToolContext {
    val conversationId: String

    /** The file of an attachment of this conversation, or null if there is no such attachment. */
    fun attachmentFile(attachmentId: String): File?

    /** Where files the agent makes for the user, outside any project, are kept. */
    fun outbox(): File

    /** Adds a file to this conversation's attachments, as an attached one is kept; large images are scaled down. */
    fun addAttachment(name: String, mimeType: String, bytes: ByteArray): Part.Attachment
}

class AgentTool(
    val name: String,
    val description: String,
    val risk: ToolRisk,
    val parameters: JsonObject,
    /** What the approval prompt says the call will do. */
    val describe: (JsonObject) -> String,
    val run: suspend ToolContext.(JsonObject) -> ToolOutput,
) {
    val spec: ToolSpec
        get() = ToolSpec(name, description, parameters)
}

/** Builds a tool's JSON Schema. */
class ParamsBuilder {

    private val properties = mutableListOf<Pair<String, JsonObjectBuilder.() -> Unit>>()
    private val required = mutableListOf<String>()

    fun project() =
        string("project", "Name (or id) of the project.")

    fun string(name: String, description: String, optional: Boolean = false) =
        add(name, "string", description, optional)

    fun integer(name: String, description: String, optional: Boolean = false) =
        add(name, "integer", description, optional)

    fun boolean(name: String, description: String, optional: Boolean = false) =
        add(name, "boolean", description, optional)

    fun stringArray(name: String, description: String, optional: Boolean = false) =
        add(name, "array", description, optional) { putJsonObject("items") { put("type", "string") } }

    private fun add(name: String, type: String, description: String, optional: Boolean, extra: JsonObjectBuilder.() -> Unit = {}) {
        properties += name to {
            put("type", type)
            put("description", description)
            extra()
        }
        if (!optional)
            required += name
    }

    fun build(): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            for ((name, schema) in properties)
                putJsonObject(name, schema)
        }
        // No additionalProperties: Gemini's schema subset rejects it.
        if (required.isNotEmpty())
            putJsonArray("required") { required.forEach { add(JsonPrimitive(it)) } }
    }
}

fun JsonObject.stringArg(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

fun JsonObject.requireString(name: String): String =
    stringArg(name) ?: throw ToolException("Missing argument: $name (a string)")

fun JsonObject.intArg(name: String): Int? =
    (this[name] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }

fun JsonObject.booleanArg(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.let { it.booleanOrNull ?: it.contentOrNull?.toBooleanStrictOrNull() }

fun JsonObject.stringSet(name: String): Set<String> =
    (this[name] as? JsonArray).orEmpty()
        .mapNotNullTo(linkedSetOf()) { (it as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.contentOrNull }
