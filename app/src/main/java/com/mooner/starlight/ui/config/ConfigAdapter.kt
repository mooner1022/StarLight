package com.mooner.starlight.ui.config

import android.annotation.SuppressLint
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
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
    private val toggleValues: MutableMap<String, Boolean> = hashMapOf()
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

        if (viewData !is CustomConfigObject && viewData !is CategoryConfigObject) {
            holder.title.text = viewData.title
            if (viewData.description != null) {
                holder.description.visibility = View.VISIBLE
                holder.description.text = viewData.description
            } else {
                holder.description.visibility = View.GONE
            }
            holder.icon.apply {
                load(viewData.icon.drawableRes)
                setColorFilter(viewData.iconTintColor)
            }
        }
        when(viewData.viewType) {
            ConfigObjectType.TOGGLE.viewType -> {
                val toggleValue = getDefault() as Boolean
                toggleValues[viewData.id] = toggleValue
                holder.toggle.isChecked = toggleValue
                holder.toggle.setOnCheckedChangeListener { _, isChecked ->
                    onConfigChanged(viewData.id, holder.toggle, isChecked)
                    for (listener in (viewData as ToggleConfigObject).listeners) {
                        listener(isChecked)
                    }
                }
            }
            ConfigObjectType.SLIDER.viewType -> {
                holder.seekBar.progress = getDefault() as Int
                holder.seekBar.max = (viewData as SliderConfigObject).max
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
                holder.editTextString.hint = data.hint
                holder.editTextString.inputType = data.inputType
                holder.editTextString.setText(getDefault() as String)
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
                holder.spinner.apply {
                    setBackgroundColor(context.getColor(R.color.transparent))
                    attachDataSource((viewData as SpinnerConfigObject).items)
                    setOnSpinnerItemSelectedListener { _, _, position, _ ->
                        onConfigChanged(viewData.id, holder.spinner, position)
                    }
                    selectedIndex = getDefault() as Int
                }
            }
            ConfigObjectType.BUTTON_FLAT.viewType -> {
                val langConf = viewData as ButtonConfigObject
                holder.layoutButton.setOnClickListener {
                    if (holder.layoutButton.isEnabled) langConf.onClickListener(it)
                }
                if (langConf.backgroundColor != null) {
                    holder.layoutButton.setBackgroundColor(langConf.backgroundColor!!)
                }
            }
            ConfigObjectType.BUTTON_CARD.viewType -> {
                val langConf = viewData as ButtonConfigObject
                holder.cardViewButton.setOnClickListener {
                    if (holder.cardViewButton.isEnabled) langConf.onClickListener(it)
                }
                if (langConf.backgroundColor != null) {
                    holder.cardViewButton.setCardBackgroundColor(langConf.backgroundColor!!)
                }
            }
            ConfigObjectType.CUSTOM.viewType -> {
                val data = viewData as CustomConfigObject
                data.onInflate(holder.customLayout)
            }
        }

        if (viewData.dependency != null) {
            val parent = data.find { it.id == viewData.dependency }
                ?: throw IllegalArgumentException("Cannot find dependency [${viewData.dependency}] for object [${viewData.id}]")
            if (parent.type != ConfigObjectType.TOGGLE) throw IllegalArgumentException("Type of object [${parent.id}] does not extend ConfigObjectType.TOGGLE")

            fun setEnabled(isEnabled: Boolean) {
                if (viewData.viewType != ConfigObjectType.CUSTOM.viewType) {
                    holder.icon.isEnabled = isEnabled
                    holder.title.isEnabled = isEnabled
                }

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
                    ConfigObjectType.BUTTON_FLAT.viewType -> {
                        holder.layoutButton.isEnabled = isEnabled
                    }
                    ConfigObjectType.BUTTON_CARD.viewType -> {
                        holder.cardViewButton.isEnabled = isEnabled
                    }
                }
            }

            (parent as ToggleConfigObject).listeners.add { isEnabled ->
                setEnabled(isEnabled)
            }

            if (toggleValues.containsKey(viewData.dependency)) {
                setEnabled(toggleValues[viewData.dependency]!!)
            }
        }
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

        lateinit var spinner: NiceSpinner

        lateinit var cardViewButton: CardView
        lateinit var layoutButton: ConstraintLayout

        lateinit var customLayout: LinearLayout

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
                ConfigObjectType.SLIDER.viewType -> {
                    seekBarIndex = itemView.findViewById(R.id.index)
                    seekBar = itemView.findViewById(R.id.slider)
                }
                ConfigObjectType.STRING.viewType -> {
                    editTextString = itemView.findViewById(R.id.editText)
                }
                ConfigObjectType.SPINNER.viewType -> {
                    spinner = itemView.findViewById(R.id.spinner)
                }
                ConfigObjectType.BUTTON_FLAT.viewType -> {
                    layoutButton = itemView.findViewById(R.id.layout_configButton)
                }
                ConfigObjectType.BUTTON_CARD.viewType -> {
                    cardViewButton = itemView.findViewById(R.id.cardView_configButton)
                }
                ConfigObjectType.CUSTOM.viewType -> {
                    customLayout = itemView.findViewById(R.id.container)
                }
            }
        }
    }
}