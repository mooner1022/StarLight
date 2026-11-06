/*
 * ButtonConfigOption.kt created by Minki Moon(mooner1022) on 2/17/24, 2:35 AM
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.configdsl.options

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.google.android.material.progressindicator.CircularProgressIndicator
import dev.mooner.configdsl.*
import dev.mooner.configdsl.utils.PeekType
import dev.mooner.configdsl.utils.showConfirmDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

enum class ButtonStyle { DEFAULT, PRIMARY, DANGER }

data class ButtonConfigOption(
    override val id              : String,
    override val title           : String,
    override val description     : String? = null,
             val onClickListener : ButtonOnClickListener?,
             @ColorInt
             val backgroundColor : Int? = null,
    override val icon            : IconInfo,
    override val dependency      : String? = null,
): ConfigOption<ButtonConfigOption.ButtonViewHolder, Boolean>() {

    override val hasError: Boolean = false

    override val default: Boolean = false

    private var isEnabled = true

    internal var style: ButtonStyle = ButtonStyle.DEFAULT
    internal var confirmMessage: LazyMessage? = null
    internal var action: ConfigAction? = null

    @Volatile
    private var isRunning = false

    override fun onCreateViewHolder(parent: ViewGroup): ButtonViewHolder {
        val view = LayoutInflater
            .from(parent.context)
            .inflate(R.layout.config_button, parent, false)
        return ButtonViewHolder(view)
    }

    override fun dataToJson(value: Boolean): JsonElement =
        JsonNull

    override fun dataFromJson(jsonElement: JsonElement): Boolean =
        false

    override fun onDraw(holder: ButtonViewHolder, data: Boolean) {
        holder.applyStyle(style)
        holder.setRunning(isRunning)
        if (onClickListener == null && action == null)
            return
        holder.layout.setOnClickListener { view ->
            if (!isEnabled || isRunning)
                return@setOnClickListener
            val message = confirmMessage
                ?: return@setOnClickListener perform(view)
            showConfirmDialog(view.context, displayTitle, message()) { confirmed ->
                if (confirmed)
                    perform(view)
            }
        }
    }

    private fun perform(view: View) {
        onClickListener?.invoke(view)
        val action = action ?: return
        val scope = optionScope ?: return
        val actionScope = ConfigActionScopeImpl(view, this)
        isRunning = true
        requestViewUpdate()
        scope.launch {
            try {
                actionScope.action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                actionScope.peek(e.message ?: e.toString(), PeekType.ERROR)
            } finally {
                isRunning = false
                requestViewUpdate()
            }
        }
    }

    override fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }

    class ButtonViewHolder(itemView: View): DefaultViewHolder(itemView) {

        val layout: ConstraintLayout = itemView.findViewById(R.id.layout_configButton)
        val progress: CircularProgressIndicator? = itemView.findViewById(R.id.progress)

        internal fun applyStyle(style: ButtonStyle) {
            val color = when (style) {
                ButtonStyle.DEFAULT -> null
                ButtonStyle.PRIMARY -> context.getColor(R.color.main_bright)
                ButtonStyle.DANGER  -> context.getColor(R.color.configdsl_status_error)
            }
            title.setTextColor(color ?: context.getColor(R.color.text))
            if (color != null)
                icon.imageTintList = ColorStateList.valueOf(color)
        }

        internal fun setRunning(running: Boolean) {
            icon.isInvisible = running
            progress?.isVisible = running
        }
    }
}

class ButtonConfigBuilder: ConfigBuilderBase<ButtonConfigOption.ButtonViewHolder, Boolean>() {

    private var mOnClickListener: ButtonOnClickListener? = null
    @Deprecated("Not rendered, use style instead")
    @ColorInt
    var backgroundColor: Int? = null

    var style: ButtonStyle = ButtonStyle.DEFAULT

    private var confirmMessage: LazyMessage? = null
    private var action: ConfigAction? = null

    fun confirm(message: String) = confirm { message }

    fun confirm(lazyMessage: () -> String) {
        confirmMessage = lazyMessage
    }

    fun action(block: ConfigAction) {
        action = block
    }

    fun setOnClickListener(callback: ButtonOnClickListener) {
        mOnClickListener = callback
    }

    fun setOnClickListener(callback: () -> Unit) {
        mOnClickListener = { callback() }
    }

    @Suppress("DEPRECATION")
    override fun build(): ButtonConfigOption {
        requiredField("title", title)

        return ButtonConfigOption(
            id = id,
            title = title!!,
            description = description,
            onClickListener = mOnClickListener,
            icon = IconInfo.auto(icon, iconFile, iconResId, iconTintColor),
            backgroundColor = backgroundColor,
            dependency = dependency
        ).also {
            it.style = style
            it.confirmMessage = confirmMessage
            it.action = action
        }
    }
}

fun ConfigItemBuilder.button(block: ButtonConfigBuilder.() -> Unit) {
    add(ButtonConfigBuilder().apply(block))
}