package com.mooner.starlight.ui.config

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
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.size.Scale
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.afollestad.materialdialogs.input.getInputField
import com.afollestad.materialdialogs.input.input
import com.google.android.material.switchmaterial.SwitchMaterial
import com.mooner.starlight.R
import com.mooner.starlight.plugincore.config.*
import com.mooner.starlight.plugincore.logger.Logger
import com.mooner.starlight.plugincore.utils.Icon
import org.angmarch.views.NiceSpinner

class ConfigAdapter(
    private val context: Context,
    private val onConfigChanged: (id: String, view: View?, data: Any) -> Unit
): RecyclerView.Adapter<ConfigAdapter.ConfigViewHolder>() {
    var data: List<ConfigObject> = mutableListOf()
    var saved: MutableMap<String, TypedString> = hashMapOf()
    private val toggleValues: MutableMap<String, Boolean> = hashMapOf()
    var isHavingError = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConfigViewHolder {
        val layout = when(viewType) {
            ConfigObjectType.TOGGLE.viewType -> R.layout.config_toggle
            ConfigObjectType.SEEKBAR.viewType -> R.layout.config_slider
            ConfigObjectType.STRING.viewType -> R.layout.config_string
            ConfigObjectType.SPINNER.viewType -> R.layout.config_spinner
            ConfigObjectType.BUTTON_FLAT.viewType,
            ConfigObjectType.PASSWORD.viewType -> R.layout.config_button_flat
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

    @SuppressLint("CheckResult")
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

                if (viewData.iconTintColor != null)
                    ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(viewData.iconTintColor!!))
                else
                    ImageViewCompat.setImageTintList(this, null)
                //setColorFilter(viewData.iconTintColor, android.graphics.PorterDuff.Mode.SRC_IN)
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
            ConfigObjectType.SEEKBAR.viewType -> {
                holder.seekBar.progress = getDefault() as Int
                val offset = (viewData as SeekbarConfigObject).min

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

                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        onConfigChanged(viewData.id, holder.seekBar, seekBar!!.progress + offset)
                    }
                })
                holder.seekBarIndex.text = (holder.seekBar.progress + offset).toString()
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
                        if (holder.editTextString.hasFocus()) return
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
                        cornerRadius(context.resources.getDimension(R.dimen.card_radius))
                        maxWidth(res = R.dimen.dialog_width)
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
                            Logger.v("password= $password, encoded= $encoded")
                            onConfigChanged(viewData.id, null, encoded)
                        }
                        negativeButton(text = "취소") { dialog ->
                            dialog.dismiss()
                        }
                    }
                }
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
            ConfigObjectType.BUTTON_FLAT.viewType,
            ConfigObjectType.PASSWORD.viewType -> {
                val data = viewData as ButtonConfigObject
                holder.layoutButton.setOnClickListener {
                    if (holder.layoutButton.isEnabled) data.onClickListener(it)
                }
                if (data.backgroundColor != null) {
                    holder.layoutButton.setBackgroundColor(data.backgroundColor!!)
                }
            }
            ConfigObjectType.BUTTON_CARD.viewType -> {
                val data = viewData as ButtonConfigObject
                holder.cardViewButton.setOnClickListener {
                    if (holder.cardViewButton.isEnabled) data.onClickListener(it)
                }
                if (data.backgroundColor != null) {
                    holder.cardViewButton.setCardBackgroundColor(data.backgroundColor!!)
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

    fun destroy() {
        toggleValues.clear()
        for (view in data) {
            //Logger.v(view.id)
            if (view is ToggleConfigObject) {
                Logger.v("Released listeners from ${view.id}")
                view.listeners.clear()
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
                ConfigObjectType.PASSWORD.viewType -> {
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