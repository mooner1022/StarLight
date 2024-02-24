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
import kotlinx.serialization.json.JsonNull
import java.io.File

typealias ConfigStructure = List<RootConfigOption<*, *>>

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
        icon.isEnabled        = enabled
        title.isEnabled       = enabled
        description.isEnabled = enabled
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

    fun publishRootUpdate(childId: String, value: Any, jsonValue: JsonElement): Boolean =
        tryEmitData(EventData(
            provider = "$id:$childId",
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

    abstract fun onCreateViewHolder(parent: ViewGroup): VH

    abstract fun onDraw(holder: VH, data: T)

    open fun setEnabled(enabled: Boolean) {}

    open fun onDestroyed() {}

    abstract fun dataToJson(value: T): JsonElement

    abstract fun dataFromJson(jsonElement: JsonElement): T

    var hasError: Boolean = false

    private var _eventFlow: MutableSharedFlow<EventData>? = null

    fun init(eventFlow: MutableSharedFlow<EventData>) {
        _eventFlow = eventFlow
    }

    protected fun tryEmitData(data: EventData): Boolean =
        _eventFlow?.tryEmit(data) ?: false

    protected fun notifyUpdated(value: T): Boolean =
        tryEmitData(EventData(
            provider = id,
            data     = value,
            jsonData = dataToJson(value)
        ))

    protected fun notifyDependencyUpdated(value: Boolean): Boolean =
        tryEmitData(EventData(
            provider = "dep:${id}",
            data     = value,
            jsonData = JsonNull
        ))

    class EventData(
        val provider: String,
        val data    : Any,
        val jsonData: JsonElement,
    ) {
        override fun toString(): String {
            return "EventData($provider: $data($jsonData))"
        }
    }
}