package com.mooner.starlight.ui.config

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PorterDuff
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.switchmaterial.SwitchMaterial
import com.mooner.starlight.R
import com.mooner.starlight.plugincore.config.*
import com.mooner.starlight.plugincore.models.TypedString
import org.angmarch.views.NiceSpinner

class ConfigAdapter(
    private val context: Context,
    private val onConfigChanged: (id: String, view: View, data: Any) -> Unit
): RecyclerView.Adapter<ConfigAdapter.ConfigViewHolder>() {
    var data: List<ConfigObject> = mutableListOf()
    var saved: MutableMap<String, TypedString> = hashMapOf()
    var isHavingError = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConfigViewHolder {
        val layout = when(viewType) {
            ConfigObjectType.TOGGLE.viewType -> R.layout.config_toggle
            ConfigObjectType.SLIDER.viewType -> R.layout.config_slider
            ConfigObjectType.STRING.viewType -> R.layout.config_string
            ConfigObjectType.SPINNER.viewType -> R.layout.config_spinner
            ConfigObjectType.BUTTON_FLAT.viewType -> R.layout.config_button_flat
            ConfigObjectType.BUTTON_CARD.viewType -> R.layout.config_button_card
            ConfigObjectType.CUSTOM.viewType -> R.layout.config_custom
            ConfigObjectType.CATEGORY.viewType -> R.layout.config_category
            ConfigObjectType.CHIP_GROUP.viewType -> R.layout.config_chip_group
            else -> 0
        }
        val view = LayoutInflater.from(context).inflate(layout, parent, false)
        return ConfigViewHolder(view, viewType)
    }

    override fun getItemCount(): Int = data.size

    override fun getItemViewType(position: Int): Int {
        return data[position].viewType
    }

    override fun onBindViewHolder(holder: ConfigViewHolder, position: Int) {
        val viewData = data[position]
        fun getDefault(): Any {
            return if (saved.containsKey(viewData.id)) saved[viewData.id]!!.cast()!! else viewData.default
        }

        when(viewData.viewType) {
            ConfigObjectType.TOGGLE.viewType -> {
                holder.textToggle.text = viewData.name
                holder.toggle.isChecked = getDefault() as Boolean
                holder.icon.apply {
                    load((viewData as ToggleConfigObject).icon.drawableRes)
                    setColorFilter(viewData.iconTintColor)
                }
                holder.toggle.setOnCheckedChangeListener { _, isChecked ->
                    onConfigChanged(viewData.id, holder.toggle, isChecked)
                    for (listener in (viewData as ToggleConfigObject).listeners) {
                        listener(isChecked)
                    }
                }
            }
            ConfigObjectType.SLIDER.viewType -> {
                holder.textSlider.text = viewData.name
                holder.seekBar.progress = getDefault() as Int
                holder.seekBar.max = (viewData as SliderConfigObject).max
                holder.icon.apply {
                    load(viewData.icon.drawableRes)
                    setColorFilter(viewData.iconTintColor)
                }
                holder.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        holder.seekBarIndex.text = progress.toString()
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        onConfigChanged(viewData.id, holder.seekBar, seekBar!!.progress)
                    }
                })
                holder.seekBarIndex.text = holder.seekBar.progress.toString()
            }
            ConfigObjectType.STRING.viewType -> {
                val data = viewData as StringConfigObject
                holder.textString.text = data.name
                holder.editTextString.hint = data.hint
                holder.editTextString.inputType = data.inputType
                holder.editTextString.setText(getDefault() as String)
                holder.icon.apply {
                    load(data.icon.drawableRes)
                    setColorFilter(viewData.iconTintColor)
                }
                holder.editTextString.addTextChangedListener(object: TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                    override fun afterTextChanged(s: Editable) {
                        val require: String?
                        if (data.require(s.toString()).also { require = it } == null) {
                            if (isHavingError) isHavingError = false
                            onConfigChanged(viewData.id, holder.editTextString, s.toString())
                        } else {
                            if (!isHavingError) isHavingError = true
                            holder.editTextString.error = require!!
                        }
                    }
                })
            }
            ConfigObjectType.SPINNER.viewType -> {
                holder.textSpinner.text = viewData.name
                holder.spinner.apply {
                    setBackgroundColor(context.getColor(R.color.transparent))
                    attachDataSource((viewData as SpinnerConfigObject).items)
                    setOnSpinnerItemSelectedListener { _, _, position, _ ->
                        onConfigChanged(viewData.id, holder.spinner, position)
                    }
                    selectedIndex = getDefault() as Int
                }
                holder.icon.apply {
                    load((viewData as SpinnerConfigObject).icon.drawableRes)
                    setColorFilter(viewData.iconTintColor)
                }
            }
            ConfigObjectType.BUTTON_FLAT.viewType -> {
                holder.textButton.text = viewData.name
                val langConf = viewData as ButtonConfigObject
                holder.layoutButton.setOnClickListener {
                    if (holder.layoutButton.isEnabled) langConf.onClickListener(it)
                }
                holder.icon.apply {
                    load(langConf.icon.drawableRes)
                    setColorFilter(viewData.iconTintColor, PorterDuff.Mode.MULTIPLY)
                }
                if (langConf.backgroundColor != null) {
                    holder.layoutButton.setBackgroundColor(langConf.backgroundColor!!)
                }
            }
            ConfigObjectType.BUTTON_CARD.viewType -> {
                holder.textButton.text = viewData.name
                val langConf = viewData as ButtonConfigObject
                holder.cardViewButton.setOnClickListener {
                    if (holder.cardViewButton.isEnabled) langConf.onClickListener(it)
                }
                holder.icon.apply {
                    load(langConf.icon.drawableRes)
                    setColorFilter(viewData.iconTintColor)
                }
                if (langConf.backgroundColor != null) {
                    holder.cardViewButton.setCardBackgroundColor(langConf.backgroundColor!!)
                }
            }
            ConfigObjectType.CUSTOM.viewType -> {
                val data = viewData as CustomConfigObject
                data.onInflate(holder.customLayout)
            }
            ConfigObjectType.CATEGORY.viewType -> {
                val data = viewData as CategoryConfigObject
                holder.categoryTitle.text = data.title
                holder.categoryTitle.apply {
                    text = data.title
                    setTextColor(data.textColor)
                }

                val children = data.items
                val ids = children.map { it.id }
                val childData = saved.filter { it.key in ids }
                val recyclerAdapter = ConfigAdapter(context, onConfigChanged).apply {
                    this.data = children
                    saved = childData.toMutableMap()
                    notifyDataSetChanged()
                }
                val mLayoutManager = LinearLayoutManager(context)
                holder.categoryItems.apply {
                    adapter = recyclerAdapter
                    layoutManager = mLayoutManager
                }
            }
        }

        if (viewData.dependency != null) {
            val parent = data.find { it.id == viewData.dependency }
                ?: throw IllegalArgumentException("Cannot find dependency [${viewData.dependency}] for object [${viewData.id}]")
            if (parent.type != ConfigObjectType.TOGGLE) throw IllegalArgumentException("Type of object [${parent.id}] does not extend ConfigObjectType.TOGGLE")

            (parent as ToggleConfigObject).listeners.add { isEnabled ->
                when(viewData.viewType) {
                    ConfigObjectType.TOGGLE.viewType -> {
                        holder.toggle.isEnabled = isEnabled
                    }
                    ConfigObjectType.SLIDER.viewType -> {
                        holder.seekBar.isEnabled = isEnabled
                    }
                    ConfigObjectType.STRING.viewType -> {
                        holder.editTextString.isEnabled = isEnabled
                    }
                    ConfigObjectType.SPINNER.viewType -> {
                        holder.spinner.isEnabled = isEnabled
                    }
                    ConfigObjectType.BUTTON_CARD.viewType -> {
                        holder.textButton.isEnabled = isEnabled
                        holder.layoutButton.isEnabled = isEnabled
                    }
                    ConfigObjectType.BUTTON_CARD.viewType -> {
                        holder.textButton.isEnabled = isEnabled
                        holder.cardViewButton.isEnabled = isEnabled
                    }
                }
            }
        }
    }

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    inner class ConfigViewHolder(itemView: View, viewType: Int) : RecyclerView.ViewHolder(itemView) {
        lateinit var icon: ImageView

        lateinit var textToggle: TextView
        lateinit var toggle: SwitchMaterial

        lateinit var textSlider: TextView
        lateinit var seekBarIndex: TextView
        lateinit var seekBar: SeekBar

        lateinit var textString: TextView
        lateinit var editTextString: EditText

        lateinit var textSpinner: TextView
        lateinit var spinner: NiceSpinner

        lateinit var textButton: TextView
        lateinit var cardViewButton: CardView
        lateinit var layoutButton: ConstraintLayout

        lateinit var customLayout: LinearLayout

        lateinit var categoryTitle: TextView
        lateinit var categoryItems: RecyclerView

        var context: Context = itemView.context

        init {
            when(viewType) {
                ConfigObjectType.TOGGLE.viewType -> {
                    icon = itemView.findViewById(R.id.icon)
                    textToggle = itemView.findViewById(R.id.textView_configToggle)
                    toggle = itemView.findViewById(R.id.switch_configToggle)
                }
                ConfigObjectType.SLIDER.viewType -> {
                    icon = itemView.findViewById(R.id.icon)
                    textSlider = itemView.findViewById(R.id.textView_configSlider)
                    seekBarIndex = itemView.findViewById(R.id.index_configSlider)
                    seekBar = itemView.findViewById(R.id.seekBar_configSlider)
                }
                ConfigObjectType.STRING.viewType -> {
                    icon = itemView.findViewById(R.id.icon)
                    textString = itemView.findViewById(R.id.textView_configString)
                    editTextString = itemView.findViewById(R.id.editText_configString)
                }
                ConfigObjectType.SPINNER.viewType -> {
                    icon = itemView.findViewById(R.id.icon)
                    textSpinner = itemView.findViewById(R.id.textView_configSpinner)
                    spinner = itemView.findViewById(R.id.spinner_configSpinner)
                }
                ConfigObjectType.BUTTON_FLAT.viewType -> {
                    textButton = itemView.findViewById(R.id.textView_configButton)
                    layoutButton = itemView.findViewById(R.id.layout_configButton)
                    icon = itemView.findViewById(R.id.icon)
                }
                ConfigObjectType.BUTTON_CARD.viewType -> {
                    textButton = itemView.findViewById(R.id.textView_configButton)
                    cardViewButton = itemView.findViewById(R.id.cardView_configButton)
                    icon = itemView.findViewById(R.id.icon)
                }
                ConfigObjectType.CUSTOM.viewType -> {
                    customLayout = itemView.findViewById(R.id.layout_configCustom)
                }
                ConfigObjectType.CATEGORY.viewType -> {
                    categoryTitle = itemView.findViewById(R.id.textView_configTitle)
                    categoryItems = itemView.findViewById(R.id.recyclerViewCategory)
                }
            }
        }
    }
}