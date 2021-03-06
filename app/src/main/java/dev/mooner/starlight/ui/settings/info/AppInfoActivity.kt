/*
 * AppInfoActivity.kt created by Minki Moon(mooner1022)
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.settings.info

import android.content.pm.PackageInfo
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.mikepenz.aboutlibraries.LibsBuilder
import dev.mooner.starlight.databinding.ActivityAppInfoBinding
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.config.config
import dev.mooner.starlight.plugincore.utils.Icon
import dev.mooner.starlight.ui.config.ConfigAdapter
import dev.mooner.starlight.utils.bindFadeImage
import dev.mooner.starlight.utils.openWebUrl

class AppInfoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppInfoBinding
    private var configAdapter: ConfigAdapter? = null

    private var devModeClicks: Int = 0

    private val isDevMode get() = Session.globalConfig.category("dev").getBoolean("dev_mode", false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val pInfo: PackageInfo = applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            pInfo.longVersionCode
        else
            pInfo.versionCode

        configAdapter = ConfigAdapter.Builder(this) {
            bind(binding.recyclerView)
            structure { getConfig(pInfo, versionCode) }
            savedData(emptyMap())
            lifecycleOwner(this@AppInfoActivity)
        }.build()

        binding.leave.setOnClickListener { finish() }

        binding.scroll.bindFadeImage(binding.imageViewLogo)
    }

    private fun getConfig(pInfo: PackageInfo, versionCode: Number) = config {
        category {
            id = "general"
            items {
                button {
                    id = "name"
                    title = "??? ??????"
                    description = "v${pInfo.versionName}(build ${versionCode})"
                    icon = Icon.STAR
                    iconTintColor = color { "#6455A1" }
                }
                button {
                    id = "version"
                    title = "PluginCore ??????"
                    description = "v${dev.mooner.starlight.plugincore.Info.PLUGINCORE_VERSION}"
                    icon = Icon.LAYERS
                    iconTintColor = color { "#C073A0" }
                }
                button {
                    id = "github"
                    title = "GitHub"
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
                    title = "??????"
                    icon = Icon.DEVELOPER_BOARD
                    description = "ariel@mooner.dev"
                    iconTintColor = color { "#3A1C71" }
                    setOnClickListener { _ ->
                        if (isDevMode) {
                            Snackbar.make(binding.root, "?????? ????????? ????????? ????????????????????????.", Snackbar.LENGTH_SHORT).show()
                        } else {
                            devModeClicks++
                            when(devModeClicks) {
                                in 4..7 -> {
                                    Snackbar.make(binding.root, "????????? ?????? ??????????????? ${8 - devModeClicks} ???????????????.", Snackbar.LENGTH_SHORT).show()
                                }
                                8 -> {
                                    Session.globalConfig.edit {
                                        category("dev")["dev_mode"] = true
                                    }
                                    Snackbar.make(binding.root, "????????? ?????? ?????????", Snackbar.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
                button {
                    id = "github"
                    title = "GitHub"
                    icon = Icon.GITHUB
                    setOnClickListener { _ ->
                        openWebUrl("https://github.com/mooner1022")
                    }
                }
                button {
                    id = "opensource_license"
                    title = "???????????? ????????????"
                    icon = Icon.DEVELOPER_MODE
                    iconTintColor = color { "#D76D77" }
                    setOnClickListener { _ ->
                        LibsBuilder().apply {
                            withShowLoadingProgress(true)
                            withAboutIconShown(true)
                            withAboutVersionShown(true)
                            withAboutAppName("Project StarLight")
                            withEdgeToEdge(true)
                        }.start(this@AppInfoActivity)
                    }
                }
            }
        }
    }
}