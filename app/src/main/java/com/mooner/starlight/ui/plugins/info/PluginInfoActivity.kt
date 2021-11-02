package com.mooner.starlight.ui.plugins.info

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.mooner.starlight.databinding.ActivityPluginInfoBinding
import com.mooner.starlight.plugincore.config.config
import com.mooner.starlight.plugincore.core.Session
import com.mooner.starlight.plugincore.plugin.StarlightPlugin
import com.mooner.starlight.plugincore.utils.Icon
import com.mooner.starlight.ui.config.ParentAdapter
import com.mooner.starlight.utils.ViewUtils.Companion.bindFadeImage

class PluginInfoActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_PLUGIN_NAME = "pluginName"
        private const val EXTRA_PLUGIN_ID   = "pluginId"
    }

    private lateinit var binding: ActivityPluginInfoBinding
    private var recyclerAdapter: ParentAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPluginInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val pluginName = intent.getStringExtra(EXTRA_PLUGIN_NAME)!!
        val pluginId = intent.getStringExtra(EXTRA_PLUGIN_ID)!!
        val plugin = (Session.pluginManager.getPluginById(pluginId)?: error("Failed to get plugin [$pluginName]")) as StarlightPlugin
        recyclerAdapter = ParentAdapter(applicationContext) { _, _, _, _ -> }

        val info = plugin.info
        recyclerAdapter!!.data = config {
            category {
                id = "general"
                title = "기본"
                items = items {
                    button {
                        id = "name"
                        title = plugin.name
                        description = "id: ${info.id}"
                        icon = Icon.LAYERS
                        iconTintColor = color { "#6455A1" }
                        onClickListener = {}
                    }
                    button {
                        id = "version"
                        title = "버전"
                        icon = Icon.BRANCH
                        iconTintColor = color { "#C073A0" }
                        description = "v${info.version}"
                        onClickListener = {}
                    }
                }
            }
            category {
                id = "info"
                title = "등록 정보"
                items = items {
                    button {
                        id = "author"
                        title = "개발자"
                        icon = Icon.DEVELOPER_BOARD
                        iconTintColor = color { "#D47E97" }
                        description = info.authors.joinToString()
                        onClickListener = {}
                    }
                    button {
                        id = "desc"
                        title = "설명"
                        description = info.description
                        icon = Icon.LIST_BULLETED
                        iconTintColor = color { "#F59193" }
                        onClickListener = {}
                    }
                    button {
                        id = "mainClass"
                        title = "메인 클래스"
                        icon = Icon.EXIT_TO_APP
                        iconTintColor = color { "#F9AE91" }
                        description = info.mainClass
                        onClickListener = {}
                    }
                }
            }
            category {
                id = "file"
                title = "파일"
                items = items {
                    button {
                        id = "name"
                        title = plugin.fileName
                        icon = Icon.FOLDER
                        iconTintColor = color { "#BCFFB9" }
                        onClickListener = {}
                    }
                    button {
                        id = "size"
                        title = "크기"
                        icon = Icon.COMPRESS
                        iconTintColor = color { "#4568DC" }
                        description = "${plugin.fileSize}mb"
                        onClickListener = {}
                    }
                }
            }
            category {
                id = "library"
                title = "라이브러리"
                items = items {
                    button {
                        id = "pluginCoreVersion"
                        title = "PluginCore 버전"
                        description = info.apiVersion
                        icon = Icon.BRANCH
                        iconTintColor = color { "#3A1C71" }
                        onClickListener = {}
                    }
                    button {
                        id = "dependency"
                        title = "의존성(필수)"
                        icon = Icon.CHECK
                        iconTintColor = color { "#D76D77" }
                        description = if (info.depend.isEmpty()) "없음" else info.depend.joinToString("\n")
                        onClickListener = {}
                    }
                    button {
                        id = "softDependency"
                        title = "의존성(soft)"
                        icon = Icon.CHECK
                        iconTintColor = color { "#FFAF7B" }
                        description = if (info.softDepend.isEmpty()) "없음" else info.softDepend.joinToString("\n")
                        onClickListener = {}
                    }
                }
            }
        }
        recyclerAdapter!!.notifyDataSetChanged()

        binding.leave.setOnClickListener { finish() }

        binding.scroll.bindFadeImage(binding.imageViewLogo)

        val layoutManager = LinearLayoutManager(applicationContext)
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.adapter = recyclerAdapter

        binding.pluginName.text = pluginName
    }

    override fun onDestroy() {
        super.onDestroy()
        recyclerAdapter?.destroy()
        recyclerAdapter = null
    }
}