package com.mooner.starlight.ui.projects.config

import android.annotation.SuppressLint
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mooner.starlight.R
import com.mooner.starlight.plugincore.TypedString
import com.mooner.starlight.plugincore.language.LanguageConfig
import com.mooner.starlight.plugincore.language.LanguageConfigType
import com.mooner.starlight.plugincore.language.SliderLanguageConfig

class ProjectConfigAdapter(
    private val context: Context,
    private val onConfigChanged: (id: String, data: Any) -> Unit
): RecyclerView.Adapter<ProjectConfigAdapter.ProjectConfigViewHolder>() {
    var data = mutableListOf<LanguageConfig>()
    var saved: MutableMap<String, TypedString> = mutableMapOf()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProjectConfigViewHolder {
        val view = when(viewType) {
            LanguageConfigType.TOGGLE.viewType -> LayoutInflater.from(context).inflate(R.layout.config_toggle, parent, false)
            LanguageConfigType.SLIDER.viewType -> LayoutInflater.from(context).inflate(R.layout.config_slider, parent, false)
            LanguageConfigType.STRING.viewType -> LayoutInflater.from(context).inflate(R.layout.config_string, parent, false)
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
            LanguageConfigType.TOGGLE.viewType -> {
                holder.textToggle.text = viewData.name
                holder.toggle.isChecked = getDefault() as Boolean
                holder.toggle.setOnCheckedChangeListener { _, isChecked ->
                    onConfigChanged(viewData.id, isChecked)
                }
            }
            LanguageConfigType.SLIDER.viewType -> {
                holder.textSlider.text = viewData.name
                holder.seekBar.progress = getDefault() as Int
                holder.seekBar.max = (viewData as SliderLanguageConfig).max
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
                        onConfigChanged(viewData.id, seekBar!!.progress)
                    }
                })
                holder.seekBarIndex.text = holder.seekBar.progress.toString()
            }
            LanguageConfigType.STRING.viewType -> {
                holder.textString.text = viewData.name
                holder.editTextString.hint = viewData.default as String
                holder.editTextString.setText(getDefault() as String)
                holder.editTextString.addTextChangedListener(object: TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                    override fun afterTextChanged(s: Editable?) {
                        onConfigChanged(viewData.id, s!!.toString())
                    }
                })
            }
        }

    }

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    inner class ProjectConfigViewHolder(itemView: View, viewType: Int) : RecyclerView.ViewHolder(itemView) {
        lateinit var textToggle: TextView
        lateinit var toggle: Switch

        lateinit var textSlider: TextView
        lateinit var seekBarIndex: TextView
        lateinit var seekBar: SeekBar

        lateinit var textString: TextView
        lateinit var editTextString: EditText

        init {
            when(viewType) {
                LanguageConfigType.TOGGLE.viewType -> {
                    textToggle = itemView.findViewById(R.id.textView_configToggle)
                    toggle = itemView.findViewById(R.id.switch_configToggle)
                }
                LanguageConfigType.SLIDER.viewType -> {
                    textSlider = itemView.findViewById(R.id.textView_configSlider)
                    seekBarIndex = itemView.findViewById(R.id.index_configSlider)
                    seekBar = itemView.findViewById(R.id.seekBar_configSlider)
                }
                LanguageConfigType.STRING.viewType -> {
                    textString = itemView.findViewById(R.id.textView_configString)
                    editTextString = itemView.findViewById(R.id.editText_configString)
                }
            }
        }
    }
}