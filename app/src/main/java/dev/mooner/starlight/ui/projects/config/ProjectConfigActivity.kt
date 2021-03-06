/*
 * Created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.projects.config

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import coil.load
import com.afollestad.materialdialogs.LayoutMode
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.bottomsheets.BottomSheet
import com.google.android.material.snackbar.Snackbar
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.ActivityProjectConfigBinding
import dev.mooner.starlight.databinding.ConfigButtonFlatBinding
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.Session.globalConfig
import dev.mooner.starlight.plugincore.config.ButtonConfigObject
import dev.mooner.starlight.plugincore.config.ConfigStructure
import dev.mooner.starlight.plugincore.config.config
import dev.mooner.starlight.plugincore.project.Project
import dev.mooner.starlight.plugincore.utils.Icon
import dev.mooner.starlight.ui.config.ConfigAdapter
import dev.mooner.starlight.utils.bindFadeImage
import java.io.File

class ProjectConfigActivity: AppCompatActivity() {
    private val changedData: MutableMap<String, MutableMap<String, Any>> = hashMapOf()
    private lateinit var binding: ActivityProjectConfigBinding
    private lateinit var project: Project
    private var configAdapter: ConfigAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProjectConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val fabProjectConfig = binding.fabProjectConfig

        val projectName = intent.getStringExtra("projectName")!!
        project = Session.projectManager.getProject(projectName)?: throw IllegalStateException("Cannot find project $projectName")

        configAdapter = ConfigAdapter.Builder(this) {
            bind(binding.configRecyclerView)
            onConfigChanged { parentId, id, view, data ->
                if (parentId in changedData)
                    changedData[parentId]!![id] = data
                else
                    changedData[parentId] = hashMapOf(id to data)

                if (!fabProjectConfig.isShown)
                    fabProjectConfig.show()
                project.getLanguage().onConfigChanged(id, view, data)
            }
            structure {
                getConfigs(project)
            }
            savedData(project.config.getData())
            lifecycleOwner(this@ProjectConfigActivity)
        }.build()

        fabProjectConfig.setOnClickListener { view ->
            if (configAdapter?.hasError == true) {
                Snackbar.make(view, "???????????? ?????? ????????? ????????????. ?????? ??? ?????? ??????????????????.", Snackbar.LENGTH_SHORT).show()
                fabProjectConfig.hide()
                return@setOnClickListener
            }

            project.config.edit {
                for ((catId, data) in changedData) {
                    category(catId).apply {
                        data.forEach(::setAny)
                    }
                }
            }

            val langConfIds = project.getLanguage().configStructure.map { it.id }
            val filtered = changedData.filter { it.key in langConfIds }
            if (filtered.isNotEmpty()) project.getLanguage().onConfigUpdated(filtered)
            Snackbar.make(view, "?????? ?????? ??????", Snackbar.LENGTH_SHORT).show()
            fabProjectConfig.hide()
        }

        binding.scroll.bindFadeImage(binding.imageViewLogo)

        binding.leave.setOnClickListener { finish() }

        val textViewConfigProjectName: TextView = findViewById(R.id.textViewConfigProjectName)
        textViewConfigProjectName.text = projectName
    }

    private fun getConfigs(project: Project): ConfigStructure {
        val configs = config {
            category {
                id = "general"
                title = "??????"
                textColor = color { "#706EB9" }
                items {
                    button {
                        id = "open_folder"
                        title = "?????? ??????"
                        type = ButtonConfigObject.Type.FLAT
                        setOnClickListener { _ ->
                            openFolderInExplorer(this@ProjectConfigActivity, project.directory)
                        }
                        icon = Icon.FOLDER
                        //backgroundColor = Color.parseColor("#B8DFD8")
                        iconTintColor = color { "#93B5C6" }
                    }
                    toggle {
                        id = "shutdown_on_error"
                        title = "?????? ????????? ????????????"
                        defaultValue = true
                        icon = Icon.ERROR
                        iconTintColor = color { "#FF5C58" }
                    }
                }
            }
        } +
        project.getLanguage().configStructure +
        config {
            val betaFeatureCategory = globalConfig.category("beta_features")
            val changeThreadPoolSize = betaFeatureCategory.getBoolean("change_thread_pool_size", false)
            val addCustomButtons = betaFeatureCategory.getBoolean("add_custom_buttons", true)

            if (changeThreadPoolSize || addCustomButtons) {
                category {
                    id = "beta_features"
                    title = "????????? ??????"
                    textColor = color("#706EB9")
                    items {
                        if (changeThreadPoolSize) {
                            seekbar {
                                id = "thread_pool_size"
                                title = "Thread pool ??????"
                                min = 1
                                max = 10
                                defaultValue = 3
                                icon = Icon.COMPRESS
                                iconTintColor = color { "#57837B" }
                            }
                        }
                        if (addCustomButtons) {
                            list {
                                id = "custom_buttons"
                                title = "????????? ?????? ??????"
                                icon = Icon.SETTINGS
                                iconTintColor = color { "#57837B" }
                                structure {
                                    string {
                                        id = "button_id"
                                        title = "id"
                                        icon = null
                                        require = { string -> if (string.isBlank()) "id??? ???????????????" else null }
                                    }
                                    string {
                                        id = "button_icon"
                                        title = "?????????"
                                        icon = null
                                        require = { string -> if (string.isBlank()) "????????? id??? ???????????????" else null }
                                    }
                                }
                                onInflate { view ->
                                    LayoutInflater.from(view.context).inflate(R.layout.config_button_card, view as FrameLayout, true)
                                }
                                onDraw { view, data ->
                                    val binding = ConfigButtonFlatBinding.bind(view.findViewById(R.id.layout_configButton))

                                    binding.title.text = data["button_id"] as String
                                    binding.description.visibility = View.GONE

                                    val icon = Icon.valueOf(data["button_icon"] as String)
                                    binding.icon.load(icon.drawableRes)
                                }
                            }
                        }
                    }
                }
            }
            category {
                id = "cautious"
                title = "??????"
                textColor = color { "#FF865E" }
                items {
                    button {
                        id = "interrupt_thread"
                        title = "???????????? ????????? ?????? ??????"
                        description = "${project.activeJobs()}?????? ????????? ??????????????????."
                        setOnClickListener { view ->
                            val active = project.activeJobs()
                            project.stopAllJobs()
                            Snackbar.make(view, "${active}?????? ????????? ?????? ???????????? ?????? ???????????????.", Snackbar.LENGTH_SHORT).show()
                        }
                        icon = Icon.LAYERS_CLEAR
                        //backgroundColor = Color.parseColor("#B8DFD8")
                        iconTintColor = color { "#FF5C58" }
                    }
                    button {
                        id = "delete_project"
                        title = "???????????? ??????"
                        setOnClickListener { _ ->
                            MaterialDialog(binding.root.context, BottomSheet(LayoutMode.WRAP_CONTENT)).show {
                                cornerRadius(25f)
                                cancelOnTouchOutside(true)
                                noAutoDismiss()
                                //icon(res = R.drawable.ic_round_delete_forever_24)
                                title(text = "??????????????? ????????? ????????????????")
                                message(text = "??????: ???????????? ????????? ????????? ???????????????.")
                                positiveButton(text = context.getString(R.string.delete)) {
                                    Session.projectManager.removeProject(project, removeFiles = true)
                                    Snackbar.make(binding.root, "??????????????? ???????????????.", Snackbar.LENGTH_SHORT).show()
                                    dismiss()
                                    finish()
                                }
                                negativeButton(text = context.getString(R.string.close)) {
                                    dismiss()
                                }
                            }
                        }
                        icon = Icon.DELETE_SWEEP
                        //backgroundColor = Color.parseColor("#B8DFD8")
                        iconTintColor = color { "#FF5C58" }
                    }
                }
            }
        }
        return configs
    }

    private fun openFolderInExplorer(context: Context, file: File) {
        val intent = Intent(Intent.ACTION_VIEW)
        val uri = FileProvider.getUriForFile(context, "$packageName.provider", file)
        intent.setDataAndType(uri, "*/*")
        startActivity(Intent.createChooser(intent, "?????? ??????"))
    }
}