/*
 * AppInfoActivity.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.settings.info

import android.content.pm.PackageInfo
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.PackageInfoCompat
import com.mikepenz.aboutlibraries.LibsBuilder
import dev.mooner.configdsl.Icon
import dev.mooner.configdsl.adapters.ConfigAdapter
import dev.mooner.configdsl.config
import dev.mooner.configdsl.options.button
import dev.mooner.configdsl.options.info
import dev.mooner.configdsl.utils.PeekType
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.ActivityAppInfoBinding
import dev.mooner.starlight.plugincore.Info
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.utils.bindPageHeader
import dev.mooner.starlight.utils.fitSkyPage
import dev.mooner.starlight.utils.getPackageInfo
import dev.mooner.starlight.utils.openWebUrl
import dev.mooner.starlight.utils.peek
import dev.mooner.starlight.utils.showEmblemCentered

class AppInfoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppInfoBinding
    private var configAdapter: ConfigAdapter? = null

    private var devModeClicks: Int = 0

    private val isDevMode get() = GlobalConfig.category("dev").getBoolean("dev_mode", false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        fitSkyPage(binding.root, binding.skyHeader, binding.scroll, binding.leave)
        // The page keeps its own composition: the emblem in the middle with the name under it. The emblem
        // is the sky's, in the place the layout leaves for it above the name.
        binding.skyHeader.showEmblemCentered(sizeDp = 100f, centerFromTopDp = 74f)
        bindPageHeader(binding.scroll, binding.skyHeader)

        val pInfo: PackageInfo = getPackageInfo()
        val versionCode = PackageInfoCompat.getLongVersionCode(pInfo)

        configAdapter = ConfigAdapter.Builder(this) {
            bind(binding.recyclerView)
            structure { getConfig(pInfo.versionName!!, versionCode) }
            configData(hashMapOf())
            lifecycleOwner(this@AppInfoActivity)
        }.build()

        binding.leave.setOnClickListener { finish() }
    }

    private fun getConfig(versionName: String, versionCode: Number) = config {
        val defaultColor = getColor(R.color.app_info_icon)
        category {
            id = "general"
            items {
                info {
                    id = "name"
                    title = getString(R.string.app_info_version)
                    value = "v$versionName"
                    description = "build $versionCode"
                    copyable = true
                    icon = Icon.STAR
                    iconTintColor = defaultColor
                }
                info {
                    id = "version"
                    title = getString(R.string.app_info_plugincore_version)
                    value = "v${Info.PLUGINCORE_VERSION}"
                    copyable = true
                    icon = Icon.LAYERS
                    iconTintColor = defaultColor
                }
                button {
                    id = "github"
                    title = "GitHub"
                    description = "mooner1022/StarLight"
                    icon = Icon.GITHUB
                    setOnClickListener { _ ->
                        openWebUrl("https://github.com/mooner1022/StarLight")
                    }
                }
            }
        }
        category {
            id = "dev"
            items {
                button {
                    id = "dev"
                    title = "무너"
                    icon = Icon.DEVELOPER_BOARD
                    description = "siwol@mooner.dev"
                    iconTintColor = defaultColor
                    setOnClickListener { _ ->
                        if (isDevMode) {
                            peek(getString(R.string.dev_mode_already_on))
                        } else {
                            devModeClicks++
                            when(devModeClicks) {
                                in 4..7 -> {
                                    peek(getString(R.string.dev_mode_remaining, 8 - devModeClicks))
                                }
                                8 -> {
                                    GlobalConfig.edit {
                                        category("dev")["dev_mode"] = true
                                    }
                                    peek(getString(R.string.dev_mode_turned_on), PeekType.SUCCESS)
                                }
                            }
                        }
                    }
                }
                button {
                    id = "github"
                    title = "GitHub"
                    description = "@mooner1022"
                    icon = Icon.GITHUB
                    setOnClickListener { _ ->
                        openWebUrl("https://github.com/mooner1022")
                    }
                }
                button {
                    id = "opensource_license"
                    title = getString(R.string.app_info_licenses)
                    icon = Icon.DEVELOPER_MODE
                    iconTintColor = defaultColor
                    setOnClickListener { _ ->
                        LibsBuilder()
                            .withShowLoadingProgress(true)
                            .withAboutIconShown(true)
                            .withAboutVersionShown(true)
                            .withAboutAppName("Project ✦ StarLight")
                            .withEdgeToEdge(true)
                            .start(this@AppInfoActivity)
                    }
                }
            }
        }
    }
}