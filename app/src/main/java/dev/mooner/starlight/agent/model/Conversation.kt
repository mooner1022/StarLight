package dev.mooner.starlight.agent.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray

/** The JSON used for conversations on disk and for provider requests. */
internal val AgentJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    classDiscriminator = "type"
}

/**
 * One conversation with the agent, saved as a file. History only grows: turns are appended, never edited, so the
 * prompt prefix each provider caches stays the same from request to request.
 */
@Serializable
data class Conversation(
    val version: Int = VERSION,
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** [dev.mooner.starlight.agent.provider.ProviderKind.id] the conversation last ran on. */
    val provider: String? = null,
    val model: String? = null,
    /** The project the conversation is about, if any. */
    val project: String? = null,
    /** [dev.mooner.starlight.agent.ApprovalPolicy.id] for this conversation, or null to follow the settings. */
    val approval: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val state: RunState = RunState.IDLE,
    val error: String? = null,
    val usage: TurnUsage = TurnUsage(),
    /** Something the agent should know with the user's next message, such as files the user rewound meanwhile. */
    val pendingNote: String? = null,
    /** Who set [title]; the model only names a conversation whose title is still its first line. Left out when so. */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val titleSource: TitleSource = TitleSource.FIRST_LINE,
) {
    companion object {
        const val VERSION = 1
    }
}

@Serializable
enum class TitleSource {
    /** The first line of the user's first message, until the model names the conversation. */
    @SerialName("first_line") FIRST_LINE,
    @SerialName("model") MODEL,
    /** The user renamed it; nothing renames it again. */
    @SerialName("user") USER,
}

@Serializable
enum class RunState {
    @SerialName("idle") IDLE,
    @SerialName("running") RUNNING,
    /** Stopped on a tool call the user has to allow or deny. */
    @SerialName("approval") WAITING_APPROVAL,
    /** The app was closed in the middle of a run. */
    @SerialName("interrupted") INTERRUPTED,
    @SerialName("error") ERROR;

    val isActive: Boolean
        get() = this == RUNNING || this == WAITING_APPROVAL
}

@Serializable
enum class Role {
    @SerialName("user") USER,
    @SerialName("assistant") ASSISTANT,
    /** Results of the tool calls of the assistant message before it. */
    @SerialName("tool") TOOL,
}

@Serializable
data class ChatMessage(
    val id: String,
    val role: Role,
    val parts: List<Part>,
    val createdAt: Long,
    /** What the provider returned for an assistant turn, replayed as is to the same kind of API. */
    val raw: RawTurn? = null,
    val model: String? = null,
    val usage: TurnUsage? = null,
    /** The user rewrote an earlier message into this one; the chat marks it. Left out of the file when false. */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val edited: Boolean = false,
    /** This message summarizes the conversation before it; see [Compaction]. */
    val compaction: Compaction? = null,
) {
    val text: String
        get() = parts.filterIsInstance<Part.Text>().joinToString("\n\n") { it.text }

    val toolCalls: List<Part.ToolCall>
        get() = parts.filterIsInstance<Part.ToolCall>()

    /** A message the user wrote, as opposed to one the app sends on its own; each starts a run of the agent. */
    val isUserTurn: Boolean
        get() = role == Role.USER && parts.any { it !is Part.Context || it.label !in Part.Context.HIDDEN }

    /** The files this message refers to: the user's attachments and the images tools showed. */
    val attachments: List<Part.Attachment>
        get() = parts.flatMap { part ->
            when (part) {
                is Part.Attachment -> listOf(part)
                is Part.ToolResult -> part.images
                else -> emptyList()
            }
        }
}

/**
 * An assistant turn exactly as one kind of API returned it: Anthropic content blocks with thinking signatures,
 * Responses output items with encrypted reasoning, or a Chat Completions message.
 */
@Serializable
data class RawTurn(
    val protocol: String,
    val items: JsonArray,
)

@Serializable
sealed class Part {

    @Serializable
    @SerialName("text")
    data class Text(val text: String) : Part()

    /**
     * What the app tells the model about a conversation in its first message, such as the project it was opened
     * from; sent like text but shown as a chip.
     */
    @Serializable
    @SerialName("context")
    data class Context(val label: String, val text: String) : Part() {
        companion object {
            /** The user's standing instructions; not shown as a chip. */
            const val INSTRUCTIONS = "instructions"
            /** The conversation was detached from its project. */
            const val NO_PROJECT = "no_project"
            /** Asks the model for the reply it left out; not shown. */
            const val NUDGE = "nudge"

            /** Labels the chat doesn't show as chips. */
            val HIDDEN = setOf(INSTRUCTIONS, NUDGE)
        }
    }

    /** A summary of the model's reasoning, only shown. */
    @Serializable
    @SerialName("thinking")
    data class Thinking(val text: String) : Part()

    @Serializable
    @SerialName("attachment")
    data class Attachment(
        val id: String,
        val name: String,
        val mimeType: String,
        val size: Long,
        val kind: AttachmentKind,
    ) : Part()

    @Serializable
    @SerialName("tool_call")
    data class ToolCall(
        val id: String,
        val name: String,
        /** The arguments as a JSON object. */
        val arguments: String,
        val approval: Approval = Approval.AUTO,
    ) : Part()

    @Serializable
    @SerialName("tool_result")
    data class ToolResult(
        val callId: String,
        val name: String,
        val output: String,
        val isError: Boolean = false,
        /** One line for the chat, such as "Compiled hello-bot". */
        val summary: String? = null,
        /** Something the tool hands to the user, shown as a card under the call. */
        val card: Card? = null,
        /** Images the tool showed the model, kept as attachments of the conversation. Left out of the file when empty. */
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val images: List<Attachment> = emptyList(),
        /** What the tool changed in a file, as unified-diff text, for the chat; the model doesn't get it. */
        val diff: String? = null,
    ) : Part()

    /** Something the app tells the user that the model never sees, such as a stopped run. */
    @Serializable
    @SerialName("notice")
    data class Notice(val kind: NoticeKind, val text: String) : Part()
}

/** A file or a project the agent puts in front of the user, with buttons to act on it. */
@Serializable
data class Card(
    val kind: CardKind,
    val name: String,
    /** The project the file or card belongs to; files without one are in the conversation's outbox. */
    val project: String? = null,
    /** File path relative to the project directory or the outbox. */
    val path: String? = null,
    val size: Long = 0,
    val mimeType: String? = null,
    val caption: String? = null,
)

@Serializable
enum class CardKind {
    @SerialName("file") FILE,
    @SerialName("project") PROJECT,
}

@Serializable
enum class AttachmentKind {
    @SerialName("image") IMAGE,
    @SerialName("pdf") PDF,
    @SerialName("text") TEXT,
    @SerialName("file") FILE,
}

@Serializable
enum class Approval {
    /** Ran without asking. */
    @SerialName("auto") AUTO,
    @SerialName("pending") PENDING,
    @SerialName("allowed") ALLOWED,
    @SerialName("denied") DENIED,
}

@Serializable
enum class NoticeKind {
    @SerialName("info") INFO,
    @SerialName("stopped") STOPPED,
    @SerialName("error") ERROR,
    @SerialName("refusal") REFUSAL,
    /** The run reached the step limit. */
    @SerialName("paused") PAUSED,
    /** The model ended its turn without a reply, even when asked for one. */
    @SerialName("no_reply") NO_REPLY,
    /** Android ended the service that kept the run going, after its daily limit for background work. */
    @SerialName("time_limit") TIME_LIMIT,
}

/** Tokens of one turn, or the sum over a conversation. */
@Serializable
data class TurnUsage(
    /** Input tokens processed at the full price. */
    val input: Long = 0,
    /** Input tokens read from the prompt cache. */
    val cacheRead: Long = 0,
    /** Input tokens written to the prompt cache. */
    val cacheWrite: Long = 0,
    val output: Long = 0,
) {
    val totalInput: Long
        get() = input + cacheRead + cacheWrite

    operator fun plus(other: TurnUsage): TurnUsage =
        TurnUsage(input + other.input, cacheRead + other.cacheRead, cacheWrite + other.cacheWrite, output + other.output)
}

/** Markdown as one line of plain text, for previews and notifications. */
fun String.plainPreview(): String =
    replace(Regex("```[\\s\\S]*?```"), " ")
        // Underscores stay: they are part of names like agent_test far more often than emphasis.
        .replace(Regex("\\*+|`+"), "")
        .replace(Regex("^\\s*(#+|>|[-+])\\s+", RegexOption.MULTILINE), "")
        .replace(Regex("\\s+"), " ")
        .trim()
