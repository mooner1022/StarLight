package dev.mooner.starlight.ui.config

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import dev.mooner.configdsl.BaseViewHolder
import dev.mooner.configdsl.ConfigOption
import dev.mooner.configdsl.DefaultViewHolder
import dev.mooner.configdsl.options.ToggleConfigOption
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import kotlin.reflect.KClass

private typealias ConfOptionClass = KClass<out ConfigOption<*, *>>

class CategoryRecyclerAdapter(
    private val options    : List<ConfigOption<*, *>>,
    private val optionData : Map<String, JsonElement>,
    private val eventScope : CoroutineScope
): RecyclerView.Adapter<BaseViewHolder>() {
    private val eventPublisher = MutableSharedFlow<ConfigOption.EventData>(
        extraBufferCapacity = Channel.UNLIMITED)
    val eventFlow: SharedFlow<ConfigOption.EventData>
        get() = eventPublisher.asSharedFlow()

    private val viewTypes: MutableList<Pair<ConfOptionClass, ConfigOption<*, *>>> = arrayListOf()

    val hasError: Boolean
        get() = options.any(ConfigOption<*, *>::hasError)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        val instance = viewTypes[viewType].second

        try {
            return instance.onCreateViewHolder(parent)
        } catch (e: Exception) {
            e.printStackTrace()
            throw Exception("Failed to initialize viewholder: ${e.message}")
        }
    }

    override fun getItemCount(): Int =
        options.size

    override fun getItemViewType(position: Int): Int {
        val option = options[position]
        val optionClass = option::class
        val pair = optionClass to option
        if (pair !in viewTypes)
            viewTypes += pair

        return viewTypes.indexOf(pair)
    }

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        @Suppress("UNCHECKED_CAST")
        val option = options[position] as ConfigOption<BaseViewHolder, Any>

        if (holder is DefaultViewHolder) {
            holder.title.text = option.title

            option.description?.let {
                holder.description.visibility = View.VISIBLE
                holder.description.text = it
            } ?: let {
                holder.description.visibility = View.GONE
            }

            option.icon.loadTo(holder.icon)
        }

        val rawData = optionData[option.id]
        if (rawData == null)
            option.onDraw(holder, option.default)
        else
            option.onDraw(holder, option.dataFromJson(rawData))

        if (option.dependency != null) {
            val (actualDepID, isInverted) = option.dependency!!.let {
                if (it[0] == '!')
                    it.drop(1) to true
                else
                    it to false
            }
            val depData = optionData[actualDepID] ?: let {
                val depOption = options.find { it.id == actualDepID }
                if (depOption !is ToggleConfigOption)
                    return
                JsonPrimitive(depOption.default)
            }
            if (depData is JsonPrimitive) {
                val isEnabled = depData.jsonPrimitive.booleanOrNull
                if (isEnabled != null) {
                    holder.setEnabled(isEnabled xor isInverted)
                    option.setEnabled(isEnabled xor isInverted)
                }
            }
            eventPublisher
                .buffer()
                .filter { it.provider == "dep:${actualDepID}" }
                .onEach { event ->
                    if (event.data !is Boolean)
                        return@onEach
                    holder.setEnabled(event.data as Boolean xor isInverted)
                    option.setEnabled(event.data as Boolean xor isInverted)
                }
                .flowOn(Dispatchers.Main)
                .launchIn(eventScope)
        }
    }

    fun destroy() {
        //eventContext.cancel()
        for (option in options)
            option.runCatching(ConfigOption<*, *>::onDestroyed)
    }

    init {
        for (option in options) {
            if (':' in option.id)
                error("Malformed id: ${option.id}")
            option.init(eventPublisher)
        }
        eventPublisher
            .onEach(::println)
            .launchIn(eventScope)
    }
}