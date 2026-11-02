package dev.mooner.starlight.ui.agent

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import dev.mooner.starlight.R
import dev.mooner.starlight.agent.provider.ProviderKind

/** Each provider's logo and color, so the settings can be scanned at a glance. */
internal object ProviderBrand {

    @DrawableRes
    fun logo(kind: ProviderKind): Int = when (kind) {
        ProviderKind.ANTHROPIC  -> R.drawable.ic_brand_anthropic
        // ChatGPT is OpenAI's too; the two differ by color.
        ProviderKind.CHATGPT,
        ProviderKind.OPENAI     -> R.drawable.ic_brand_openai
        ProviderKind.GEMINI     -> R.drawable.ic_brand_gemini
        ProviderKind.OPENROUTER -> R.drawable.ic_brand_openrouter
        ProviderKind.CUSTOM     -> R.drawable.ic_brand_custom
    }

    /** How the provider connects: a sign-in, an API key, or a server's address. */
    @StringRes
    fun method(kind: ProviderKind): Int = when (kind) {
        ProviderKind.CHATGPT -> R.string.agent_provider_method_sign_in
        ProviderKind.CUSTOM  -> R.string.agent_provider_method_url
        else                 -> R.string.agent_api_key
    }

    /** What the provider is, in a line, so ChatGPT and an OpenAI key, say, can be told apart. */
    @StringRes
    fun description(kind: ProviderKind): Int = when (kind) {
        ProviderKind.ANTHROPIC  -> R.string.agent_provider_desc_anthropic
        ProviderKind.CHATGPT    -> R.string.agent_provider_desc_chatgpt
        ProviderKind.OPENAI     -> R.string.agent_provider_desc_openai
        ProviderKind.GEMINI     -> R.string.agent_provider_desc_gemini
        ProviderKind.OPENROUTER -> R.string.agent_provider_desc_openrouter
        ProviderKind.CUSTOM     -> R.string.agent_provider_desc_custom
    }

    @ColorInt
    fun color(context: Context, kind: ProviderKind): Int = ContextCompat.getColor(context, when (kind) {
        ProviderKind.ANTHROPIC  -> R.color.agent_brand_anthropic
        ProviderKind.CHATGPT    -> R.color.agent_brand_chatgpt
        ProviderKind.OPENAI     -> R.color.agent_brand_openai
        ProviderKind.GEMINI     -> R.color.agent_brand_gemini
        ProviderKind.OPENROUTER -> R.color.agent_brand_openrouter
        ProviderKind.CUSTOM     -> R.color.agent_brand_custom
    })

    /** A light wash of the provider's color for behind its logo, opaque so nothing under it shows through. */
    @ColorInt
    fun wash(context: Context, kind: ProviderKind): Int =
        ColorUtils.compositeColors(ColorUtils.setAlphaComponent(color(context, kind), 0x24),
            ContextCompat.getColor(context, R.color.agent_surface))

    /** A circle in the provider's [wash], behind its logo. */
    fun badge(context: Context, kind: ProviderKind): Drawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(wash(context, kind))
    }
}
