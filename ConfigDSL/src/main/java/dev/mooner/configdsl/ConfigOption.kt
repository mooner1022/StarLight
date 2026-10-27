package dev.mooner.configdsl

import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.CallSuper
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.size.Scale
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.json.JsonElement
import java.io.File

typealias ConfigStructure = List<RootConfigOption<*, *>>

fun ConfigStructure.findOption(rootId: String, id: String): ConfigOption<*, *>? =
    find { it.id == rootId }?.childOptions?.find { it.id == id }

abstract class BaseViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {

    abstract fun setEnabled(enabled: Boolean)
}

abstract class DefaultViewHolder(itemView: View): BaseViewHolder(itemView) {

    val context: Context = itemView.context

    open val icon        : ImageView = itemView.findViewById(R.id.icon)
    open val title       : TextView  = itemView.findViewById(R.id.title)
    open val description : TextView  = itemView.findViewById(R.id.description)

    @CallSuper
    override fun setEnabled(enabled: Boolean) {
        itemView.isEnabled    = enabled
        itemView.alpha        = if (enabled) 1f else DISABLED_ALPHA
        icon.isEnabled        = enabled
        title.isEnabled       = enabled
        description.isEnabled = enabled
    }

    private companion object {
        const val DISABLED_ALPHA = 0.38f
    }
}

data class IconInfo(
    val iconFile      : File?,
    val iconResId     : Int?,
    val iconTintColor : Int?,
) {

    fun loadTo(imageView: ImageView) {
        imageView.apply {
            when {
                iconResId != null -> {
                    if (iconResId == -1)
                        setImageDrawable(null)
                    else
                        load(iconResId)
                }
                iconFile != null ->
                    load(iconFile) { scale(Scale.FIT) }
            }
            imageTintList = iconTintColor?.let(ColorStateList::valueOf)
        }
    }

    companion object {
        fun auto(
            icon: Icon?,
            iconFile: File?,
            @DrawableRes
            iconResId: Int?,
            @ColorInt
            iconTintColor: Int?
        ): IconInfo {
            return if (icon != null)
                fromIcon(icon, iconTintColor)
            else if (iconFile != null)
                fromFile(iconFile, iconTintColor)
            else if (iconResId != null)
                fromResId(iconResId, iconTintColor)
            else
                none()
        }

        fun fromFile(iconFile: File, @ColorInt iconTintColor: Int?) =
            IconInfo(iconFile, null, iconTintColor)

        fun fromResId(@DrawableRes iconResId: Int, @ColorInt iconTintColor: Int?) =
            IconInfo(null, iconResId, iconTintColor)

        fun fromIcon(icon: Icon, @ColorInt iconTintColor: Int?) =
            fromResId(icon.drawableRes, iconTintColor)

        fun none() =
            fromIcon(Icon.NONE, null)
    }
}

abstract class RootConfigOption<VH: BaseViewHolder, T: Any>: ConfigOption<VH, T>() {

    abstract val childOptions: List<ConfigOption<*, *>>

    override val hasError: Boolean
        get() = childOptions.any(ConfigOption<*, *>::hasError)

    fun publishRootUpdate(childId: String, value: Any, jsonValue: JsonElement): Boolean =
        tryEmitData(RootUpdateData(
            rootId   = id,
            provider = childId,
            data     = value,
            jsonData = jsonValue
        ))
}

abstract class ConfigOption<VH: BaseViewHolder, T: Any> {
    abstract val id: String
    abstract val title: String
    abstract val description: String?

    abstract val icon: IconInfo

    abstract val default: T
    abstract val dependency: String?

    abstract val hasError: Boolean

    abstract fun onCreateViewHolder(parent: ViewGroup): VH

    abstract fun onDraw(holder: VH, data: T)

    open fun setEnabled(enabled: Boolean) {}

    open fun onDestroyed() {}

    abstract fun dataToJson(value: T): JsonElement

    abstract fun dataFromJson(jsonElement: JsonElement): T

    @Volatile
    private var textOverride: OptionText? = null

    val displayTitle: String
        get() = textOverride?.title ?: title

    val displayDescription: String?
        get() = textOverride.let { if (it == null) description else it.description }

    fun updateTitle(title: String): Boolean =
        updateText(title, displayDescription)

    fun updateDescription(description: String?): Boolean =
        updateText(displayTitle, description)

    fun updateText(title: String, description: String?): Boolean {
        textOverride = OptionText(title, description)
        return requestViewUpdate()
    }

    private var _eventFlow: MutableSharedFlow<EventData>? = null

    fun init(eventFlow: MutableSharedFlow<EventData>) {
        _eventFlow = eventFlow
    }

    protected fun tryEmitData(data: EventData): Boolean =
        _eventFlow?.tryEmit(data) ?: false

    protected fun notifyUpdated(value: T): Boolean =
        tryEmitData(ValueUpdateData(
            provider = id,
            data     = value,
            jsonData = dataToJson(value)
        ))

    protected fun notifyDependencyUpdated(value: Boolean): Boolean =
        tryEmitData(DependencyUpdateData(
            provider = id,
            data     = value,
        ))

    protected fun requestViewUpdate(): Boolean =
        tryEmitData(RedrawRequestData(
            provider = id
        ))

    private data class OptionText(val title: String, val description: String?)

    interface EventData {
        val provider: String
    }

    data class RootUpdateData(
        val rootId: String,
        override val provider: String,
        val data: Any,
        val jsonData: JsonElement
    ): EventData {
        override fun toString(): String {
            return "ValueUpdateData($rootId->$provider: $data($jsonData))"
        }
    }

    data class ValueUpdateData(
        override val provider: String,
        val data    : Any,
        val jsonData: JsonElement,
    ): EventData {
        override fun toString(): String {
            return "ValueUpdateData($provider: $data($jsonData))"
        }
    }

    data class DependencyUpdateData(
        override val provider: String,
        val data: Boolean,
    ): EventData {
        override fun toString(): String {
            return "DependencyUpdateData($provider: $data)"
        }
    }

    data class RedrawRequestData(
        override val provider: String,
    ): EventData {
        override fun toString(): String {
            return "RedrawRequestData($provider)"
        }
    }
}