package dev.mooner.starlight.agent

import android.content.Context
import androidx.core.content.edit
import dev.mooner.starlight.agent.auth.SecretStore
import dev.mooner.starlight.agent.model.AgentJson
import dev.mooner.starlight.agent.provider.ModelInfo
import dev.mooner.starlight.agent.provider.ProviderKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/** When the agent asks before running a tool. */
enum class ApprovalPolicy(val id: String) {
    /** Runs every tool without asking. */
    AUTO("auto"),
    /** Asks before deleting, and before turning projects on or off. */
    ASK_DESTRUCTIVE("destructive"),
    /** Asks before anything that changes a project. */
    ASK_WRITES("writes"),
    /**
     * Only looks and plans: tools that change something are refused until the user approves the plan. Set per
     * conversation; an older StarLight reads it as [ASK_DESTRUCTIVE].
     */
    PLAN("plan");

    companion object {
        fun of(id: String?): ApprovalPolicy =
            entries.firstOrNull { it.id == id } ?: ASK_DESTRUCTIVE
    }
}

/** The agent's preferences, and the secrets kept apart from them. */
class AgentSettings internal constructor(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("agent", Context.MODE_PRIVATE)
    internal val secrets = SecretStore(context)

    private val mVersion = MutableStateFlow(0)
    /** Changes whenever a setting does, for screens to refresh. */
    val version: StateFlow<Int> = mVersion.asStateFlow()

    private fun changed() {
        mVersion.value++
    }

    var provider: ProviderKind?
        get() = ProviderKind.of(prefs.getString(KEY_PROVIDER, null))
        set(value) {
            prefs.edit { putString(KEY_PROVIDER, value?.id) }
            changed()
        }

    fun model(kind: ProviderKind): String? =
        prefs.getString("model.${kind.id}", null) ?: kind.defaultModels.firstOrNull()?.id

    fun setModel(kind: ProviderKind, model: String) {
        prefs.edit { putString("model.${kind.id}", model) }
        changed()
    }

    fun effort(kind: ProviderKind): String? =
        prefs.getString("effort.${kind.id}", null)?.takeIf { it in kind.effortLevels }

    fun setEffort(kind: ProviderKind, effort: String?) {
        prefs.edit { putString("effort.${kind.id}", effort) }
        changed()
    }

    fun baseUrl(kind: ProviderKind): String =
        prefs.getString("base_url.${kind.id}", null)?.takeIf(String::isNotBlank) ?: kind.defaultBaseUrl

    fun setBaseUrl(kind: ProviderKind, url: String) {
        prefs.edit { putString("base_url.${kind.id}", url.trim().trimEnd('/')) }
        changed()
    }

    fun apiKey(kind: ProviderKind): String? =
        secrets.get("api_key.${kind.id}")

    fun setApiKey(kind: ProviderKind, key: String?) {
        secrets.put("api_key.${kind.id}", key?.trim())
        changed()
    }

    /** Whether [kind] has what it needs to send a request. */
    fun isConfigured(kind: ProviderKind): Boolean = when (kind) {
        ProviderKind.CHATGPT -> secrets.has(ChatGptKeys.REFRESH)
        ProviderKind.CUSTOM  -> prefs.getString("base_url.${kind.id}", null).isNullOrBlank().not()
        else                 -> secrets.has("api_key.${kind.id}")
    }

    fun configuredProviders(): List<ProviderKind> =
        ProviderKind.entries.filter(::isConfigured)

    /**
     * The provider requests go to: the one the user chose if it is set up, or the first one that is when none was
     * chosen. Null when a chosen one isn't set up any more, see [resolveProvider].
     */
    fun activeProvider(): ProviderKind? =
        resolveProvider(provider, configuredProviders())

    /** The provider the user chose, when it is no longer set up: its sign-in expired, or its key is gone. */
    val lostProvider: ProviderKind?
        get() = provider?.takeUnless(::isConfigured)

    /**
     * Forgets that [kind] was chosen, for when the user disconnects it themselves: another one that is set up is
     * used then, as before they chose.
     */
    fun unchoose(kind: ProviderKind) {
        if (provider == kind)
            provider = null
    }

    fun cachedModels(kind: ProviderKind): List<ModelInfo> {
        val json = prefs.getString("models.${kind.id}", null) ?: return kind.defaultModels
        return runCatching { AgentJson.decodeFromString<List<StoredModel>>(json) }.getOrNull()
            ?.map { ModelInfo(it.id, it.name, it.context) }
            ?.ifEmpty { null }
            ?: kind.defaultModels
    }

    fun setCachedModels(kind: ProviderKind, models: List<ModelInfo>) {
        val stored = models.map { StoredModel(it.id, it.name, it.contextWindow) }
        prefs.edit { putString("models.${kind.id}", AgentJson.encodeToString(stored)) }
        changed()
    }

    var approvalPolicy: ApprovalPolicy
        get() = ApprovalPolicy.of(prefs.getString(KEY_APPROVAL, null))
        set(value) {
            prefs.edit { putString(KEY_APPROVAL, value.id) }
            changed()
        }

    /** Model requests one run may make before it stops and asks to continue. */
    var maxSteps: Int
        get() = prefs.getInt(KEY_MAX_STEPS, DEFAULT_MAX_STEPS)
        set(value) {
            prefs.edit { putInt(KEY_MAX_STEPS, value.coerceIn(5, 200)) }
            changed()
        }

    /** The user's own instructions, sent with their first message of each conversation. */
    var customInstructions: String
        get() = prefs.getString(KEY_INSTRUCTIONS, "").orEmpty()
        set(value) {
            prefs.edit { putString(KEY_INSTRUCTIONS, value) }
            changed()
        }

    var notifyOnFinish: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY, true)
        set(value) {
            prefs.edit { putBoolean(KEY_NOTIFY, value) }
            changed()
        }

    /** Whether the model may use its provider's own web search (Anthropic and OpenRouter); it costs extra. */
    var webSearch: Boolean
        get() = prefs.getBoolean(KEY_WEB_SEARCH, false)
        set(value) {
            prefs.edit { putBoolean(KEY_WEB_SEARCH, value) }
            changed()
        }

    /** Whether the model names each conversation after its first answer, with a short request of its own. */
    var autoTitles: Boolean
        get() = prefs.getBoolean(KEY_AUTO_TITLES, true)
        set(value) {
            prefs.edit { putBoolean(KEY_AUTO_TITLES, value) }
            changed()
        }

    /** Whether a run's progress may show as a Live Update (Android 16 QPR2 and later) while its chat isn't open. */
    var liveUpdates: Boolean
        get() = prefs.getBoolean(KEY_LIVE_UPDATES, true)
        set(value) {
            prefs.edit { putBoolean(KEY_LIVE_UPDATES, value) }
            changed()
        }

    /** OpenAI asks apps to say once that requests use the user's ChatGPT plan. */
    var chatGptPlanNoticeShown: Boolean
        get() = prefs.getBoolean("chatgpt.plan_notice", false)
        set(value) = prefs.edit { putBoolean("chatgpt.plan_notice", value) }

    internal fun string(key: String): String? =
        prefs.getString(key, null)

    internal fun putString(key: String, value: String?) =
        prefs.edit { putString(key, value) }

    internal fun long(key: String): Long =
        prefs.getLong(key, 0)

    internal fun putLong(key: String, value: Long) =
        prefs.edit { putLong(key, value) }

    internal fun notifyChanged() =
        changed()

    @Serializable
    private data class StoredModel(val id: String, val name: String, val context: Int? = null)

    internal object ChatGptKeys {
        const val ACCESS = "chatgpt.access"
        const val REFRESH = "chatgpt.refresh"
        const val ID_TOKEN = "chatgpt.id_token"
        const val CLIENT_ID = "chatgpt.client_id"
        const val HOST_ID = "chatgpt.host_id"
        const val EXPIRES_AT = "chatgpt.expires_at"
        const val EARLIEST_REFRESH_AT = "chatgpt.earliest_refresh_at"
        const val EMAIL = "chatgpt.email"
    }

    private companion object {
        const val KEY_PROVIDER = "provider"
        const val KEY_APPROVAL = "approval"
        const val KEY_MAX_STEPS = "max_steps"
        const val KEY_INSTRUCTIONS = "instructions"
        const val KEY_NOTIFY = "notify_on_finish"
        const val KEY_LIVE_UPDATES = "live_updates"
        const val KEY_WEB_SEARCH = "web_search"
        const val KEY_AUTO_TITLES = "auto_titles"
        const val DEFAULT_MAX_STEPS = 60
    }
}

/**
 * The provider to use, given the one the user [chosen] and those that are [configured].
 *
 * A chosen one that lost its sign-in or its key is not replaced by another: a sign-in that expired in the middle of a
 * conversation would otherwise carry it on with another provider's key, at that key's cost, without a word.
 */
internal fun resolveProvider(chosen: ProviderKind?, configured: List<ProviderKind>): ProviderKind? =
    if (chosen != null)
        chosen.takeIf { it in configured }
    else
        configured.firstOrNull()
