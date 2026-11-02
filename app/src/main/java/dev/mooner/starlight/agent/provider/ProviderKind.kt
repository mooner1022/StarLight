package dev.mooner.starlight.agent.provider

/** The services the agent can talk to. */
enum class ProviderKind(
    val id: String,
    val displayName: String,
    val defaultBaseUrl: String,
    /** Where the user gets a key, or null when there is no key to enter. */
    val keyUrl: String?,
    val defaultModels: List<ModelInfo>,
    /** Reasoning effort levels the provider takes, best effort; empty when it has none. */
    val effortLevels: List<String>,
) {
    ANTHROPIC(
        id = "anthropic",
        displayName = "Anthropic",
        defaultBaseUrl = AnthropicClient.DEFAULT_BASE_URL,
        keyUrl = "https://platform.claude.com/settings/keys",
        defaultModels = AnthropicClient.DEFAULT_MODELS,
        effortLevels = listOf("low", "medium", "high", "xhigh", "max"),
    ),
    CHATGPT(
        id = "chatgpt",
        displayName = "ChatGPT",
        defaultBaseUrl = ResponsesClient.DEFAULT_BASE_URL,
        keyUrl = null,
        defaultModels = ResponsesClient.CHATGPT_MODELS,
        effortLevels = listOf("low", "medium", "high", "xhigh"),
    ),
    OPENAI(
        id = "openai",
        displayName = "OpenAI",
        defaultBaseUrl = ResponsesClient.DEFAULT_BASE_URL,
        keyUrl = "https://platform.openai.com/api-keys",
        defaultModels = ResponsesClient.CHATGPT_MODELS,
        effortLevels = listOf("low", "medium", "high", "xhigh"),
    ),
    GEMINI(
        id = "gemini",
        displayName = "Google Gemini",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
        keyUrl = "https://aistudio.google.com/apikey",
        defaultModels = emptyList(),
        effortLevels = listOf("low", "medium", "high"),
    ),
    OPENROUTER(
        id = "openrouter",
        displayName = "OpenRouter",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        keyUrl = "https://openrouter.ai/settings/keys",
        defaultModels = emptyList(),
        effortLevels = listOf("low", "medium", "high"),
    ),
    CUSTOM(
        id = "custom",
        displayName = "OpenAI-compatible",
        defaultBaseUrl = "",
        keyUrl = null,
        defaultModels = emptyList(),
        effortLevels = emptyList(),
    );

    /** Whether the user enters a key; ChatGPT signs in instead, and a local server may need none. */
    val usesApiKey: Boolean
        get() = this != CHATGPT

    companion object {
        fun of(id: String?): ProviderKind? =
            entries.firstOrNull { it.id == id }
    }
}
