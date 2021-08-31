package com.mooner.starlight.ui.projects.config

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.switchmaterial.SwitchMaterial
import com.mooner.starlight.R
import com.mooner.starlight.plugincore.TypedString
import com.mooner.starlight.plugincore.config.*
import org.angmarch.views.NiceSpinner

class ProjectConfigAdapter(
    private val context: Context,
    private val onConfigChanged: (id: String, view: View, data: Any) -> Unit
): RecyclerView.Adapter<ProjectConfigAdapter.ProjectConfigViewHolder>() {
    var data: List<ConfigObject> = emptyList()
    var saved: MutableMap<String, TypedString> = hashMapOf()
    var isHavingError = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProjectConfigViewHolder {
        val view = when(viewType) {
            ConfigObjectType.TOGGLE.viewType -> LayoutInflater.from(context).inflate(R.layout.config_toggle, parent, false)
            ConfigObjectType.SLIDER.viewType -> LayoutInflater.from(context).inflate(R.layout.config_slider, parent, false)
            ConfigObjectType.STRING.viewType -> LayoutInflater.from(context).inflate(R.layout.config_string, parent, false)
            ConfigObjectType.SPINNER.viewType -> LayoutInflater.from(context).inflate(R.layout.config_spinner, parent, false)
            ConfigObjectType.BUTTON.viewType -> LayoutInflater.from(context).inflate(R.layout.config_button, parent, false)
            ConfigObjectType.CUSTOM.viewType -> LayoutInflater.from(context).inflate(R.layout.config_custom, parent, false)
            else -> LayoutInflater.from(context).inflate(R.layout.config_toggle, parent, false)
        }
        return ProjectConfigViewHolder(view, viewType)
    }

    override fun getItemCount(): Int = data.size

    override fun getItemViewType(position: Int): Int {
        return data[position].viewType
    }

    override fun onBindViewHolder(holder: ProjectConfigViewHolder, position: Int) {
        val viewData = data[position]
        fun getDefault(): Any {
            return if (saved.containsKey(viewData.id)) saved[viewData.id]!!.cast()!! else viewData.default
        }

        when(viewData.viewType) {
            ConfigObjectType.TOGGLE.viewType -> {
                holder.textToggle.text = viewData.name
                holder.toggle.isChecked = getDefault() as Boolean
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
            }
            ConfigObjectType.BUTTON.viewType -> {
                holder.textButton.text = viewData.name
                val langConf = viewData as ButtonConfigObject
                holder.cardViewButton.setOnClickListener {
                    if (holder.cardViewButton.isEnabled) langConf.onClickListener(it)
                }
                holder.imageViewButton.load(langConf.icon.drawableRes)
                if (langConf.iconTintColor != null) {
                    holder.imageViewButton.imageTintList = ColorStateList.valueOf(langConf.iconTintColor!!)
                }
                if (langConf.backgroundColor != null) {
                    holder.cardViewButton.setCardBackgroundColor(langConf.backgroundColor!!)
                }
            }
            ConfigObjectType.CUSTOM.viewType -> {
                val data = viewData as CustomConfigObject
                data.onInflate(holder.customLayout)
            }
            ConfigObjectType.TITLE.viewType -> {
                val data = viewData as TitleConfigObject
                holder.titleText.text = data.title
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
                    ConfigObjectType.BUTTON.viewType -> {
                        holder.textButton.isEnabled = isEnabled
                        holder.cardViewButton.isEnabled = isEnabled
                    }
                }
            }
        }
    }

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    inner class ProjectConfigViewHolder(itemView: View, viewType: Int) : RecyclerView.ViewHolder(itemView) {
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
        lateinit var imageViewButton: ImageView

        lateinit var customLayout: LinearLayout

        lateinit var titleText: TextView

        var context: Context = itemView.context

        init {
            when(viewType) {
                ConfigObjectType.TOGGLE.viewType -> {
                    textToggle = itemView.findViewById(R.id.textView_configToggle)
                    toggle = itemView.findViewById(R.id.switch_configToggle)
                }
                ConfigObjectType.SLIDER.viewType -> {
                    textSlider = itemView.findViewById(R.id.textView_configSlider)
                    seekBarIndex = itemView.findViewById(R.id.index_configSlider)
                    seekBar = itemView.findViewById(R.id.seekBar_configSlider)
                }
                ConfigObjectType.STRING.viewType -> {
                    textString = itemView.findViewById(R.id.textView_configString)
                    editTextString = itemView.findViewById(R.id.editText_configString)
                }
                ConfigObjectType.SPINNER.viewType -> {
                    textSpinner = itemView.findViewById(R.id.textView_configSpinner)
                    spinner = itemView.findViewById(R.id.spinner_configSpinner)
                }
                ConfigObjectType.BUTTON.viewType -> {
                    textButton = itemView.findViewById(R.id.textView_configButton)
                    cardViewButton = itemView.findViewById(R.id.cardView_configButton)
                    imageViewButton = itemView.findViewById(R.id.imageView_configButton)
                }
                ConfigObjectType.CUSTOM.viewType -> {
                    customLayout = itemView.findViewById(R.id.layout_configCustom)
                }
                ConfigObjectType.TITLE.viewType -> {
                    titleText = itemView.findViewById(R.id.textView_configTitle)
                }
            }
        }
    }
}