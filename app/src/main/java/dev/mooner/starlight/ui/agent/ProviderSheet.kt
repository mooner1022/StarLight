package dev.mooner.starlight.ui.agent

import android.content.Context
import android.content.Intent
import android.text.Html
import android.view.LayoutInflater
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import dev.mooner.starlight.R
import dev.mooner.starlight.agent.AgentHub
import dev.mooner.starlight.agent.auth.ChatGptAuth
import dev.mooner.starlight.agent.provider.ProviderKind
import dev.mooner.starlight.databinding.SheetAgentProviderBinding
import dev.mooner.starlight.ui.common.Sheet
import dev.mooner.starlight.ui.common.showSheet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Connects one provider: an API key (and server for custom ones), or ChatGPT sign-in. */
internal object ProviderSheet {

    fun <T> show(host: T, kind: ProviderKind) where T : Context, T : LifecycleOwner {
        val hub = AgentHub.get(host)
        val binding = SheetAgentProviderBinding.inflate(LayoutInflater.from(host))
        val isChatGpt = kind == ProviderKind.CHATGPT
        binding.keyGroup.isVisible = !isChatGpt
        binding.chatGptGroup.isVisible = isChatGpt
        var signIn: Job? = null
        lateinit var sheet: Sheet

        if (!isChatGpt) {
            val custom = kind == ProviderKind.CUSTOM
            binding.baseUrlLabel.isVisible = custom
            binding.baseUrl.isVisible = custom
            binding.baseUrl.setText(hub.settings.baseUrl(kind).takeIf { custom })
            binding.keyLabel.setText(if (custom) R.string.agent_api_key_optional else R.string.agent_api_key)
            binding.apiKey.setText(hub.settings.apiKey(kind))
            binding.getKey.isVisible = kind.keyUrl != null
            binding.getKey.setOnClickListener { open(host, kind.keyUrl!!) }
        } else {
            renderChatGpt(host, hub, binding)
            binding.manageUsage.setOnClickListener { open(host, ChatGptAuth.MANAGE_USAGE_URL) }
            binding.chatGptSignIn.setOnClickListener {
                if (hub.chatGpt.isSignedIn) {
                    hub.chatGpt.signOut()
                    renderChatGpt(host, hub, binding)
                    return@setOnClickListener
                }
                signIn?.cancel()
                signIn = host.lifecycleScope.launch {
                    val attempt = runCatching { hub.chatGpt.begin("StarLight") }.getOrElse {
                        sheet.setNote(host.getString(R.string.agent_chatgpt_failed, hub.runtime.describe(it)), isError = true)
                        return@launch
                    }
                    try {
                        sheet.setNote(host.getString(R.string.agent_chatgpt_waiting))
                        open(host, attempt.url)
                        attempt.await { success, message -> callbackPage(host, success, message) }
                        hub.settings.provider = ProviderKind.CHATGPT
                        sheet.setNote(null)
                        renderChatGpt(host, hub, binding)
                        // Back from the browser; bring the app forward if Android lets it.
                        runCatching {
                            host.startActivity(Intent(host, host.javaClass).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                        }
                        refreshModels(host, hub, kind, sheet)
                        showPlanNotice(host, hub)
                    } catch (e: CancellationException) {
                        attempt.close()
                        throw e
                    } catch (e: Exception) {
                        sheet.setNote(host.getString(R.string.agent_chatgpt_failed, hub.runtime.describe(e)), isError = true)
                    }
                }
            }
        }

        sheet = host.showSheet {
            title = kind.displayName
            iconLoader = {
                val padding = AgentUi.dp(host, 8)
                setPadding(padding, padding, padding, padding)
                // The frame clips to its own shape, so fill all of it.
                setBackgroundColor(ProviderBrand.wash(host, kind))
                setImageResource(ProviderBrand.logo(kind))
            }
            message = if (isChatGpt) host.getString(R.string.agent_chatgpt_body) else null
            lifecycleOwner = host
            content(binding.root)
            if (!isChatGpt) {
                positive(R.string.agent_save_and_check, autoDismiss = false) { current ->
                    save(host, hub, kind, binding, current)
                }
                if (hub.settings.isConfigured(kind))
                    negative(R.string.agent_disconnect) {
                        hub.settings.setApiKey(kind, null)
                        if (kind == ProviderKind.CUSTOM)
                            hub.settings.setBaseUrl(kind, "")
                    }
            } else {
                positive(R.string.agent_done)
            }
            onDismiss { signIn?.cancel() }
        }
    }

    private fun <T> save(host: T, hub: AgentHub, kind: ProviderKind, binding: SheetAgentProviderBinding, sheet: Sheet)
        where T : Context, T : LifecycleOwner {
        val key = binding.apiKey.text.toString().trim()
        if (kind == ProviderKind.CUSTOM) {
            val url = binding.baseUrl.text.toString().trim()
            if (url.isEmpty()) {
                sheet.setNote(host.getString(R.string.agent_base_url_hint), isError = true)
                return
            }
            hub.settings.setBaseUrl(kind, url)
        } else if (key.isEmpty()) {
            sheet.setNote(host.getString(R.string.agent_api_key), isError = true)
            return
        }
        hub.settings.setApiKey(kind, key.ifEmpty { null })
        sheet.setPositiveEnabled(false)
        sheet.setNote(host.getString(R.string.agent_checking))
        host.lifecycleScope.launch {
            val ok = refreshModels(host, hub, kind, sheet)
            sheet.setPositiveEnabled(true)
            if (ok) {
                hub.settings.provider = kind
                sheet.dismiss()
            }
        }
    }

    /** Loads the account's models; also how a key is checked. */
    private suspend fun refreshModels(context: Context, hub: AgentHub, kind: ProviderKind, sheet: Sheet): Boolean =
        runCatching { hub.client(kind).listModels() }
            .onSuccess { models ->
                if (models.isNotEmpty()) {
                    hub.settings.setCachedModels(kind, models)
                    val current = hub.settings.model(kind)
                    if (current == null || models.none { it.id == current })
                        hub.settings.setModel(kind, (kind.defaultModels.firstOrNull { d -> models.any { it.id == d.id } } ?: models.first()).id)
                }
                sheet.setNote(context.getString(R.string.agent_check_ok, models.size))
            }
            .onFailure { sheet.setNote(context.getString(R.string.agent_check_failed, hub.runtime.describe(it)), isError = true) }
            .isSuccess

    private fun renderChatGpt(context: Context, hub: AgentHub, binding: SheetAgentProviderBinding) {
        val signedIn = hub.chatGpt.isSignedIn
        binding.chatGptStatus.isVisible = signedIn
        binding.chatGptStatus.text = hub.chatGpt.email?.let { context.getString(R.string.agent_chatgpt_signed_in, it) }
            ?: context.getString(R.string.agent_chatgpt_signed_in_plain)
        binding.chatGptSignIn.setText(if (signedIn) R.string.agent_chatgpt_sign_out else R.string.agent_chatgpt_continue)
        binding.chatGptSignIn.setBackgroundResource(if (signedIn) R.drawable.bg_agent_button_plain else R.drawable.bg_agent_button_gold)
        binding.chatGptSignIn.setTextColor(context.getColor(if (signedIn) R.color.agent_text else R.color.agent_on_gold))
        binding.manageUsage.isVisible = signedIn
    }

    /** OpenAI asks apps to say once that requests use the ChatGPT plan, with a way to manage usage. */
    private fun showPlanNotice(context: Context, hub: AgentHub) {
        if (hub.settings.chatGptPlanNoticeShown)
            return
        hub.settings.chatGptPlanNoticeShown = true
        context.showSheet {
            title = context.getString(R.string.agent_chatgpt_plan_title)
            icon = R.drawable.ic_agent_sparkle
            message = context.getString(R.string.agent_chatgpt_plan_body)
            neutral(context.getString(R.string.agent_chatgpt_manage_usage)) { open(context, ChatGptAuth.MANAGE_USAGE_URL) }
            positive(R.string.agent_chatgpt_ok)
        }
    }

    /** The page the browser shows after signing in, in the agent's night colors. */
    private fun callbackPage(context: Context, success: Boolean, message: String?): String {
        val text = if (success) context.getString(R.string.agent_chatgpt_page_done)
            else context.getString(R.string.agent_chatgpt_page_failed, message.orEmpty())
        val back = context.getString(R.string.agent_chatgpt_page_back)
        return """
            <!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>StarLight</title><style>
            body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;font-family:system-ui,sans-serif;
            color:#F1EEE8;background:radial-gradient(120% 80% at 85% -10%,rgba(112,119,161,.35),transparent 60%),
            linear-gradient(180deg,#0B0E1D,#151A33 55%,#1D2142)}
            main{max-width:420px;padding:32px;text-align:center}.star{font-size:40px;color:#DFC199}
            p{line-height:1.6;color:#B3B7CB}a{display:inline-block;margin-top:20px;padding:14px 24px;border-radius:999px;
            background:#DFC199;color:#1E1709;text-decoration:none;font-weight:700}
            </style></head><body><main><div class="star">✦</div><p>${Html.escapeHtml(text)}</p>
            <a href="$RETURN_URI">${Html.escapeHtml(back)}</a></main></body></html>
        """.trimIndent()
    }

    private fun open(context: Context, url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** Opens [AgentReturnActivity], which brings StarLight back from the browser. */
    const val RETURN_URI = "starlight://agent/return"
}
