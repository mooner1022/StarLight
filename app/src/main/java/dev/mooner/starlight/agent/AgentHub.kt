package dev.mooner.starlight.agent

import android.annotation.SuppressLint
import android.content.Context
import dev.mooner.starlight.agent.docs.AgentDocs
import dev.mooner.starlight.agent.auth.ChatGptAuth
import dev.mooner.starlight.agent.auth.SignInRequiredException
import dev.mooner.starlight.agent.provider.AnthropicClient
import dev.mooner.starlight.agent.provider.ChatCompletionsClient
import dev.mooner.starlight.agent.provider.LlmClient
import dev.mooner.starlight.agent.provider.ProviderKind
import dev.mooner.starlight.agent.provider.ResponsesClient
import dev.mooner.starlight.agent.runtime.AgentRuntime
import dev.mooner.starlight.agent.store.AgentStorage
import dev.mooner.starlight.agent.store.AttachmentStore
import dev.mooner.starlight.agent.store.CheckpointStore
import dev.mooner.starlight.agent.store.ConversationStore
import dev.mooner.starlight.agent.tools.StarLightTools
import java.io.File

/** The agent's parts, made once per process from the application context. */
class AgentHub private constructor(val context: Context) {

    val settings = AgentSettings(context)
    val chatGpt = ChatGptAuth(settings)
    /** In the StarLight folder, so conversations outlive a reinstall where that folder is shared storage. */
    val dataDir: File = AgentStorage.root(context)
    val store = ConversationStore(dataDir).apply { markInterrupted() }
    val attachments = AttachmentStore(File(dataDir, AgentStorage.ATTACHMENTS))
    /** What the agent's tools changed in each turn, kept beside the attachments, for rewinding. */
    val checkpoints = CheckpointStore(File(dataDir, AgentStorage.ATTACHMENTS))
    val docs = AgentDocs(context)
    val tools = StarLightTools(docs)
    val runtime = AgentRuntime(this)

    /** A client for [kind] with the credentials saved for it. */
    fun client(kind: ProviderKind): LlmClient =
        client(kind, settings.apiKey(kind), settings.baseUrl(kind))

    /** A client for [kind] with [apiKey] and [baseUrl], such as ones the user is still checking. */
    fun client(kind: ProviderKind, apiKey: String?, baseUrl: String): LlmClient {
        fun key() = apiKey ?: throw SignInRequiredException("Enter an API key for ${kind.displayName} first.")
        return when (kind) {
            ProviderKind.ANTHROPIC  -> AnthropicClient(key(), baseUrl)
            ProviderKind.CHATGPT    -> ResponsesClient({ force -> chatGpt.accessToken(force) }, baseUrl, chatGpt = true)
            ProviderKind.OPENAI     -> ResponsesClient({ key() }, baseUrl)
            ProviderKind.GEMINI     -> ChatCompletionsClient(key(), baseUrl, ChatCompletionsClient.Flavor.GEMINI)
            ProviderKind.OPENROUTER -> ChatCompletionsClient(key(), baseUrl, ChatCompletionsClient.Flavor.OPENROUTER)
            ProviderKind.CUSTOM     -> ChatCompletionsClient(apiKey, baseUrl)
        }
    }

    companion object {

        @SuppressLint("StaticFieldLeak") // Holds the application context only.
        @Volatile
        private var instance: AgentHub? = null

        fun get(context: Context): AgentHub =
            instance ?: synchronized(this) {
                instance ?: AgentHub(context.applicationContext).also { instance = it }
            }
    }
}
