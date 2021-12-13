package com.mooner.starlight.ui.settings.info

import android.content.pm.PackageInfo
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.mooner.starlight.databinding.ActivityAppInfoBinding
import com.mooner.starlight.plugincore.Info
import com.mooner.starlight.plugincore.Session
import com.mooner.starlight.plugincore.config.config
import com.mooner.starlight.plugincore.utils.Icon
import com.mooner.starlight.ui.config.ParentAdapter
import com.mooner.starlight.utils.ViewUtils.Companion.bindFadeImage

class AppInfoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppInfoBinding
    private var recyclerAdapter: ParentAdapter? = null

    private var devModeClicks: Int = 0

    private val isDevMode get() = Session.globalConfig.getCategory("dev").getBoolean("dev_mode", false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val pInfo: PackageInfo = applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            pInfo.longVersionCode
        else
            pInfo.versionCode

        recyclerAdapter = ParentAdapter(applicationContext) { _, _, _, _ -> }
        recyclerAdapter!!.data = config {
            category {
                id = "general"
                items = items {
                    button {
                        id = "name"
                        title = "앱 버전"
                        description = "v${pInfo.versionName}(build ${versionCode})"
                        icon = Icon.STAR
                        iconTintColor = color { "#6455A1" }
                        onClickListener = {}
                    }
                    button {
                        id = "version"
                        title = "PluginCore 버전"
                        description = "v${Info.PLUGINCORE_VERSION}"
                        icon = Icon.LAYERS
                        iconTintColor = color { "#C073A0" }
                        onClickListener = {}
                    }
                }
            }
            category {
                id = "dev"
                items = items {
                    button {
                        id = "dev"
                        title = "무너"
                        icon = Icon.DEVELOPER_BOARD
                        description = "starlight@mooner.dev"
                        iconTintColor = color { "#3A1C71" }
                        onClickListener = {
                            if (isDevMode) {
                                Snackbar.make(binding.root, "이미 개발자 모드가 활성화되었습니다.", Snackbar.LENGTH_SHORT).show()
                            } else {
                                devModeClicks++
                                when(devModeClicks) {
                                    in 4..7 -> {
                                        Snackbar.make(binding.root, "개발자 모드 활성화까지 ${8 - devModeClicks} 남았습니다.", Snackbar.LENGTH_SHORT).show()
                                    }
                                    8 -> {
                                        Session.globalConfig.edit {
                                            getCategory("dev")["dev_mode"] = true
                                        }
                                        Snackbar.make(binding.root, "개발자 모드 활성화", Snackbar.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    }
                    button {
                        id = "opensource_license"
                        title = "오픈소스 라이센스"
                        icon = Icon.DEVELOPER_MODE
                        iconTintColor = color { "#D76D77" }
                        onClickListener = {}
                    }
                }
            }
        }

        recyclerAdapter!!.notifyAllItemInserted()

        binding.leave.setOnClickListener { finish() }

        binding.scroll.bindFadeImage(binding.imageViewLogo)

        val layoutManager = LinearLayoutManager(applicationContext)
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.adapter = recyclerAdapter
    }

    override fun onDestroy() {
        super.onDestroy()
        recyclerAdapter = null
    }
}