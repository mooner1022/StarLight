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
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.ActivityAppInfoBinding
import dev.mooner.starlight.plugincore.Info
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.config.config
import dev.mooner.starlight.plugincore.utils.Icon
import dev.mooner.starlight.ui.config.ConfigAdapter
import dev.mooner.starlight.utils.bindFadeImage
import dev.mooner.starlight.utils.openWebUrl

class AppInfoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppInfoBinding
    private var configAdapter: ConfigAdapter? = null

    private var devModeClicks: Int = 0

    private val isDevMode get() = GlobalConfig.category("dev").getBoolean("dev_mode", false)

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
            structure { getConfig(pInfo.versionName, versionCode) }
            savedData(emptyMap())
            lifecycleOwner(this@AppInfoActivity)
        }.build()

        binding.leave.setOnClickListener { finish() }

        binding.scroll.bindFadeImage(binding.imageViewLogo)
    }

    private fun getConfig(versionName: String, versionCode: Number) = config {
        val defaultColor = getColor(R.color.main_bright)
        category {
            id = "general"
            items {
                button {
                    id = "name"
                    title = "앱 버전"
                    description = "v${versionName}(build ${versionCode})"
                    icon = Icon.STAR
                    iconTintColor = defaultColor
                }
                button {
                    id = "version"
                    title = "PluginCore 버전"
                    description = "v${Info.PLUGINCORE_VERSION}"
                    icon = Icon.LAYERS
                    iconTintColor = defaultColor
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
                    title = "무너"
                    icon = Icon.DEVELOPER_BOARD
                    description = "siwol@mooner.dev"
                    iconTintColor = defaultColor
                    setOnClickListener { _ ->
                        if (isDevMode) {
                            Snackbar.make(binding.root, "이미 개발자 모드가 활성화되었습니다.", Snackbar.LENGTH_SHORT).show()
                        } else {
                            devModeClicks++
                            when(devModeClicks) {
                                in 4..7 -> {
                                    Snackbar.make(binding.root, "개발자 모드 활성화까지 ${8 - devModeClicks} 남았습니다.", Snackbar.LENGTH_SHORT).show()
                                }
                                8 -> {
                                    GlobalConfig.edit {
                                        category("dev")["dev_mode"] = true
                                    }
                                    Snackbar.make(binding.root, "개발자 모드 활성화", Snackbar.LENGTH_SHORT).show()
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
                    title = "오픈소스 라이센스"
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