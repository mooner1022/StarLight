package dev.mooner.starlight.ui.config

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.widget.AdapterView.OnItemSelectedListener
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.size.Scale
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.callbacks.onDismiss
import com.afollestad.materialdialogs.color.colorChooser
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.input.getInputField
import com.afollestad.materialdialogs.input.input
import com.google.android.material.divider.MaterialDividerItemDecoration
import com.google.android.material.switchmaterial.SwitchMaterial
import dev.mooner.starlight.R
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.config.*
import dev.mooner.starlight.plugincore.config.data.PrimitiveTypedString
import dev.mooner.starlight.plugincore.utils.Icon
import dev.mooner.starlight.plugincore.utils.hasFlag
import dev.mooner.starlight.ui.config.list.ListRecyclerAdapter
import dev.mooner.starlight.utils.setCommonAttrs
import dev.mooner.starlight.utils.showConfirmDialog
import jp.wasabeef.recyclerview.animators.FadeInUpAnimator
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CategoryRecyclerAdapter(
    private val context: Context,
    private val onConfigChanged: (id: String, view: View?, data: Any) -> Unit
): RecyclerView.Adapter<CategoryRecyclerAdapter.ConfigViewHolder>() {

    var cells: List<ConfigObject> = mutableListOf()
    var saved: MutableMap<String, PrimitiveTypedString> = hashMapOf()

    private val toggleValues: MutableMap<String, Boolean> = hashMapOf()
    private val toggleListeners: MutableMap<String, MutableList<(isEnabled: Boolean) -> Unit>> = hashMapOf()

    private val dumpedObjects: MutableList<Any> = mutableListOf()

    var isHavingError = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConfigViewHolder {
        val layout = when(viewType) {
            ConfigObjectType.TOGGLE.viewType       -> R.layout.config_toggle
            ConfigObjectType.SEEKBAR.viewType      -> R.layout.config_slider
            ConfigObjectType.STRING.viewType       -> R.layout.config_string
            ConfigObjectType.SPINNER.viewType      -> R.layout.config_spinner
            ConfigObjectType.BUTTON_FLAT.viewType,
            ConfigObjectType.PASSWORD.viewType,
            ConfigObjectType.COLOR_PICKER.viewType -> R.layout.config_button_flat
            ConfigObjectType.BUTTON_CARD.viewType  -> R.layout.config_button_card
            ConfigObjectType.LIST.viewType         -> R.layout.config_list
            ConfigObjectType.CUSTOM.viewType       -> R.layout.config_custom
            else -> error("Unknown viewType: $viewType")
        }
        val view = LayoutInflater.from(context).inflate(layout, parent, false)
        return ConfigViewHolder(view, viewType)
    }

    override fun getItemCount(): Int =
        cells.size

    override fun getItemViewType(position: Int): Int =
        cells[position].viewType

    @SuppressLint("CheckResult", "SetTextI18n")
    override fun onBindViewHolder(holder: ConfigViewHolder, position: Int) {
        val viewData = cells[position]
        fun getDefault(): Any =
            saved[viewData.id]?.tryCast() ?: viewData.default

        if (viewData !is CustomConfigObject && viewData !is CategoryConfigObject) {
            holder.title.text = viewData.title

            holder.description.apply {
                if (viewData.description != null) {
                    visibility = View.VISIBLE
                    text = viewData.description
                } else {
                    visibility = View.GONE
                }
            }

            holder.icon.apply {
                when {
                    viewData.icon != null -> when(viewData.icon) {
                        Icon.NONE -> setImageDrawable(null)
                        else -> load(viewData.icon!!.drawableRes)
                    }
                    viewData.iconFile != null -> load(viewData.iconFile!!) {
                        scale(Scale.FIT)
                    }
                    viewData.iconResId != null -> load(viewData.iconResId!!)
                }

                imageTintList = viewData.iconTintColor?.let(ColorStateList::valueOf)
                //setColorFilter(viewData.iconTintColor, android.graphics.PorterDuff.Mode.SRC_IN)
            }
        }

        when(viewData.viewType) {
            ConfigObjectType.TOGGLE.viewType -> {
                fun callListeners(isChecked: Boolean) {
                    (viewData as ToggleConfigObject).onValueChangedListener?.invoke(holder.toggle, isChecked)
                    onConfigChanged(viewData.id, holder.toggle, isChecked)

                    if (viewData.id in toggleListeners) {
                        for (listener in toggleListeners[viewData.id]!!) {
                            listener(isChecked)
                        }
                    }
                }

                require(viewData is ToggleConfigObject)
                val toggleValue = getDefault() as Boolean
                toggleValues[viewData.id] = toggleValue
                holder.toggle.isChecked = toggleValue
                holder.toggle.setOnCheckedChangeListener { _, isChecked ->
                    if (viewData.enableWarnMsg != null || viewData.disableWarnMag != null) {
                        with(viewData) { if (isChecked) enableWarnMsg else disableWarnMag }?.let { lazyMessage ->
                            showConfirmDialog(context, title = "경고", message = lazyMessage()) { isConfirmed ->
                                if (isConfirmed) {
                                    callListeners(isChecked)
                                } else {
                                    holder.toggle.isChecked = !holder.toggle.isChecked
                                }
                            }
                        } ?: callListeners(isChecked)
                    } else {
                        callListeners(isChecked)
                    }
                }
            }
            ConfigObjectType.SEEKBAR.viewType -> {
                holder.seekBar.progress = getDefault() as Int
                val offset = (viewData as SeekbarConfigObject).min

                holder.seekBarIndex.text = (holder.seekBar.progress).toString()
                holder.seekBar.max = viewData.max - offset
                holder.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        holder.seekBarIndex.text = (progress + offset).toString()
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                    override fun onStopTrackingTouch(seekBar: SeekBar) {
                        onConfigChanged(viewData.id, holder.seekBar, seekBar.progress + offset)
                    }
                })
            }
            ConfigObjectType.STRING.viewType -> {
                val data = viewData as StringConfigObject
                holder.editTextString.hint = data.hint
                holder.editTextString.inputType = data.inputType
                holder.editTextString.setText(getDefault() as String)

                fun updateText(s: Editable) {
                    val require: String?
                    if (data.require(s.toString()).also { require = it } == null) {
                        if (isHavingError) isHavingError = false
                        onConfigChanged(viewData.id, holder.editTextString, s.toString())
                    } else {
                        if (!isHavingError) isHavingError = true
                        holder.editTextString.error = require!!
                    }
                }

                holder.editTextString.addTextChangedListener(object: TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                    override fun afterTextChanged(s: Editable) {
                        //if (holder.editTextString.hasFocus()) return
                        updateText(s)
                    }
                })
                holder.editTextString.setOnFocusChangeListener { view, hasFocus ->
                    if (!hasFocus) {
                        updateText((view as EditText).text)
                    }
                }
            }
            ConfigObjectType.PASSWORD.viewType -> {
                val data = viewData as PasswordConfigObject
                holder.layoutButton.setOnClickListener {
                    if (!holder.layoutButton.isEnabled) return@setOnClickListener
                    MaterialDialog(it.context, BottomSheet(LayoutMode.WRAP_CONTENT)).show {
                        setCommonAttrs()
                        title(text = data.title)
                        input(waitForPositiveButton = false) { dialog, text ->
                            val require = data.require(text.toString())
                            if (require != null) {
                                dialog.getInputField().error = require
                                return@input
                            }
                        }
                        positiveButton(text = "설정") { dialog ->
                            val password = dialog.getInputField().text.toString()
                            val encoded = data.hashCode(password)
                            //Logger.v("password= $password, encoded= $encoded")
                            onConfigChanged(viewData.id, null, encoded)
                        }
                        negativeButton(text = "취소", click = MaterialDialog::dismiss)
                    }
                }
            }
            ConfigObjectType.SPINNER.viewType -> {
                holder.spinner.apply {
                    val items = ArrayAdapter(context, android.R.layout.simple_spinner_item, (viewData as SpinnerConfigObject).items)
                    adapter = items
                    setBackgroundColor(context.getColor(R.color.transparent))
                    setSelection(getDefault() as Int, true)
                    onItemSelectedListener = object : OnItemSelectedListener {
                        override fun onItemSelected(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
                            viewData.onItemSelectedListener?.invoke(this@apply, position)
                            onConfigChanged(viewData.id, holder.spinner, position)
                        }

                        override fun onNothingSelected(p0: AdapterView<*>?) {}
                    }
                }
            }
            ConfigObjectType.BUTTON_FLAT.viewType -> {
                val data = viewData as ButtonConfigObject
                if (data.backgroundColor != null) {
                    holder.layoutButton.setBackgroundColor(data.backgroundColor!!)
                }
                if (data.onClickListener != null) {
                    holder.layoutButton.setOnClickListener {
                        if (holder.layoutButton.isEnabled)
                            data.onClickListener?.invoke(it)
                    }
                }
            }
            ConfigObjectType.BUTTON_CARD.viewType -> {
                val data = viewData as ButtonConfigObject
                if (data.backgroundColor != null) {
                    holder.cardViewButton.setCardBackgroundColor(data.backgroundColor!!)
                }
                if (data.onClickListener != null) {
                    holder.cardViewButton.setOnClickListener {
                        if (holder.cardViewButton.isEnabled) data.onClickListener?.invoke(it)
                    }
                }
            }
            ConfigObjectType.COLOR_PICKER.viewType -> {
                val data = viewData as ColorPickerConfigObject

                val colors = data.colors.keys.toIntArray()
                val subColors = data.colors.map { it.value.toIntArray() }.toTypedArray()

                val allowCustomArgb = data.flags hasFlag ColorPickerConfigObject.FLAG_CUSTOM_ARGB
                val showAlphaSelector = allowCustomArgb && data.flags hasFlag ColorPickerConfigObject.FLAG_ALPHA_SELECTOR

                val initialSelection = (getDefault() as Int).let { def -> if (def == 0x0) null else def }

                holder.icon.imageTintList = initialSelection?.let(ColorStateList::valueOf)
                holder.layoutButton.setOnClickListener { view ->
                    MaterialDialog(view.context, BottomSheet(LayoutMode.WRAP_CONTENT)).show {
                        var selected: Int? = null

                        setCommonAttrs()
                        title(text = data.title)
                        colorChooser(
                            colors = colors,
                            subColors = subColors,
                            allowCustomArgb = allowCustomArgb,
                            showAlphaSelector = showAlphaSelector,
                            initialSelection = initialSelection
                        ) { _, color ->
                            selected = color
                        }
                        positiveButton(R.string.ok) {
                            if (selected == null) {
                                Toast.makeText(context, "선택된 값이 없어 저장되지 않았어요.", Toast.LENGTH_SHORT).show()
                            } else {
                                holder.icon.imageTintList = selected?.let(ColorStateList::valueOf)
                                data.onColorSelectedListener?.invoke(holder.layoutButton, selected!!)
                                onConfigChanged(viewData.id, holder.layoutButton, selected!!)
                            }
                        }
                        negativeButton(R.string.cancel)
                    }
                }
            }
            ConfigObjectType.LIST.viewType -> {
                val data = viewData as ListConfigObject

                val list: MutableList<Map<String, PrimitiveTypedString>> =
                    Session.json.decodeFromString(getDefault() as String)

                val mappedList = list.map { it.mapValues { v -> v.value.cast() } }.toMutableList()
                val recyclerAdapter = let {
                    dumpedObjects += ListRecyclerAdapter(context, data.onDraw, data.onInflate, data.structure, mappedList) { cfgList ->
                        val encoded = Json.encodeToString(cfgList.map { it.mapValues { v -> PrimitiveTypedString.from(v.value) } })
                        onConfigChanged(viewData.id, holder.recyclerViewList, encoded)
                    }
                    dumpedObjects.last() as ListRecyclerAdapter
                }

                val itemTouchCallback = object : ItemTouchHelper.SimpleCallback (ItemTouchHelper.UP or ItemTouchHelper.DOWN, ItemTouchHelper.RIGHT){
                    override fun onMove(
                        recyclerView: RecyclerView,
                        viewHolder: RecyclerView.ViewHolder,
                        target: RecyclerView.ViewHolder
                    ): Boolean {
                        val fromPos: Int = viewHolder.bindingAdapterPosition
                        val toPos: Int = target.bindingAdapterPosition
                        recyclerAdapter.swapData(fromPos, toPos)
                        return true
                    }

                    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                        recyclerAdapter.removeData(viewHolder.layoutPosition)
                    }
                }

                ItemTouchHelper(itemTouchCallback).attachToRecyclerView(holder.recyclerViewList)

                holder.recyclerViewList.apply {
                    itemAnimator = FadeInUpAnimator()
                    layoutManager = LinearLayoutManager(context)
                    adapter = recyclerAdapter
                    addItemDecoration(MaterialDividerItemDecoration(context, MaterialDividerItemDecoration.VERTICAL).apply {
                        isLastItemDecorated = false
                    })
                }

                recyclerAdapter.notifyItemRangeInserted(0, list.size)

                holder.buttonAddList.setOnClickListener { view ->
                    val configData: MutableMap<String, Any> = mutableMapOf()
                    MaterialDialog(view.context, BottomSheet(LayoutMode.WRAP_CONTENT))
                        .show {
                            title(R.string.add)

                            view.findViewTreeLifecycleOwner()?.let {
                                setCommonAttrs()
                            } ?: setCommonAttrs()

                            customView(R.layout.dialog_logs)

                            val recycler: RecyclerView = findViewById(R.id.rvLog)

                            val configAdapter = ConfigAdapter.Builder(view.context) {
                                bind(recycler)
                                onConfigChanged { _, id, _, data ->
                                    configData[id] = data
                                }
                                structure {
                                    config {
                                        category {
                                            id = "list_structure"
                                            items = data.structure
                                        }
                                    }
                                }
                            }.build()

                            onDismiss {
                                configAdapter.destroy()
                            }

                            positiveButton(R.string.ok) {
                                if (configData.isNotEmpty()) {
                                    recyclerAdapter.data += configData
                                    recyclerAdapter.notifyItemInserted(recyclerAdapter.data.size)
                                    val encoded = Json.encodeToString(recyclerAdapter.data.map { it.mapValues { v -> PrimitiveTypedString.from(v.value) } })
                                    onConfigChanged(viewData.id, holder.recyclerViewList, encoded)
                                }
                            }
                            negativeButton(R.string.cancel)
                        }
                }
            }
            ConfigObjectType.CUSTOM.viewType -> {
                val data = viewData as CustomConfigObject
                data.onInflate(holder.customLayout)
            }
        }

        if (viewData.dependency != null) {
            val isReversed: Boolean
            val dependencyId = with(viewData.dependency!!) {
                if (startsWith("!")) {
                    isReversed = true
                    drop(1)
                } else {
                    isReversed = false
                    this
                }
            }

            val parent = cells.find { it.id == dependencyId }
                ?: throw IllegalArgumentException("Cannot find dependency id '${viewData.dependency}' for object id '${viewData.id}'")
            if (parent.type != ConfigObjectType.TOGGLE) throw IllegalArgumentException("Type of object with id '${parent.id}' does not extend ConfigObjectType.TOGGLE")

            fun setEnabled(enabled: Boolean) {
                val isEnabled = if (isReversed) !enabled else enabled

                if (viewData.viewType != ConfigObjectType.CUSTOM.viewType) {
                    holder.icon.isEnabled = isEnabled
                    holder.title.isEnabled = isEnabled
                    holder.description.isEnabled = isEnabled
                }

                when(viewData.viewType) {
                    ConfigObjectType.TOGGLE.viewType -> {
                        holder.toggle.isEnabled = isEnabled
                    }
                    ConfigObjectType.SEEKBAR.viewType -> {
                        holder.seekBar.isEnabled = isEnabled
                    }
                    ConfigObjectType.STRING.viewType -> {
                        holder.editTextString.isEnabled = isEnabled
                    }
                    ConfigObjectType.SPINNER.viewType -> {
                        holder.spinner.isEnabled = isEnabled
                    }
                    ConfigObjectType.BUTTON_FLAT.viewType,
                    ConfigObjectType.PASSWORD.viewType,
                    ConfigObjectType.COLOR_PICKER.viewType -> {
                        holder.layoutButton.isEnabled = isEnabled
                    }
                    ConfigObjectType.BUTTON_CARD.viewType -> {
                        holder.cardViewButton.isEnabled = isEnabled
                    }
                    ConfigObjectType.LIST.viewType -> {
                        holder.buttonAddList.isEnabled = isEnabled
                        holder.recyclerViewList.isEnabled = isEnabled
                    }
                }
            }

            if (dependencyId in toggleListeners)
                toggleListeners[dependencyId]!! += ::setEnabled
            else
                toggleListeners[dependencyId] = mutableListOf(::setEnabled)

            if (dependencyId in toggleValues) {
                setEnabled(toggleValues[dependencyId]!!)
            }
        }
    }

    fun destroy() {
        toggleValues.clear()
        toggleListeners.clear()
        dumpedObjects.clear()
    }

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    inner class ConfigViewHolder(itemView: View, viewType: Int) : RecyclerView.ViewHolder(itemView) {
        lateinit var icon: ImageView
        lateinit var title: TextView
        lateinit var description: TextView

        lateinit var toggle: SwitchMaterial

        lateinit var seekBarIndex: TextView
        lateinit var seekBar: SeekBar

        lateinit var editTextString: EditText

        lateinit var spinner: Spinner

        lateinit var cardViewButton: CardView
        lateinit var layoutButton: ConstraintLayout

        lateinit var buttonAddList: Button
        lateinit var recyclerViewList: RecyclerView

        lateinit var customLayout: FrameLayout

        init {
            if (viewType != ConfigObjectType.CUSTOM.viewType) {
                icon = itemView.findViewById(R.id.icon)
                title = itemView.findViewById(R.id.title)
                description = itemView.findViewById(R.id.description)
            }
            when(viewType) {
                ConfigObjectType.TOGGLE.viewType -> {
                    toggle = itemView.findViewById(R.id.toggle)
                }
                ConfigObjectType.SEEKBAR.viewType -> {
                    seekBarIndex = itemView.findViewById(R.id.index)
                    seekBar = itemView.findViewById(R.id.slider)
                }
                ConfigObjectType.STRING.viewType -> {
                    editTextString = itemView.findViewById(R.id.editText)
                }
                ConfigObjectType.SPINNER.viewType -> {
                    spinner = itemView.findViewById(R.id.spinner)
                }
                ConfigObjectType.BUTTON_FLAT.viewType,
                ConfigObjectType.PASSWORD.viewType,
                ConfigObjectType.COLOR_PICKER.viewType -> {
                    layoutButton = itemView.findViewById(R.id.layout_configButton)
                }
                ConfigObjectType.BUTTON_CARD.viewType -> {
                    cardViewButton = itemView.findViewById(R.id.cardView_configButton)
                }
                ConfigObjectType.LIST.viewType -> {
                    buttonAddList = itemView.findViewById(R.id.buttonAdd)
                    recyclerViewList = itemView.findViewById(R.id.recyclerView)
                }
                ConfigObjectType.CUSTOM.viewType -> {
                    customLayout = itemView.findViewById(R.id.container)
                }
            }
        }
    }
}