package com.mooner.starlight.ui.settings.dev

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.mooner.starlight.databinding.ActivityDevModeBinding
import com.mooner.starlight.plugincore.config.config
import com.mooner.starlight.plugincore.core.Session
import com.mooner.starlight.plugincore.utils.Icon
import com.mooner.starlight.ui.config.ParentAdapter

class DevModeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDevModeBinding
    private var recyclerAdapter: ParentAdapter? = null
    private val settings = config {
        category {
            id = "dev_mode_config"
            title = "개발자 모드"
            textColor = color { "#706EB9" }
            items = items {
                toggle {
                    id = "show_internal_log"
                    title = "내부 로그 표시"
                    description = "앱 내부의 디버깅용 로그를 표시합니다."
                    icon = Icon.MARK_CHAT_UNREAD
                    iconTintColor = color { "#87AAAA" }
                    defaultValue = false
                }
                button {
                    id = "make_error"
                    title = "에러 발생"
                    description = "고의적으로 에러를 발생시킵니다."
                    icon = Icon.ERROR
                    iconTintColor = color { "#FF5C58" }
                    onClickListener = {
                        throw Exception("Expected error created from dev mode")
                    }
                }
                button {
                    id = "disable_dev_mode"
                    title = "개발자 모드 비활성화"
                    icon = Icon.DEVELOPER_BOARD_OFF
                    iconTintColor = color { "#FF8243" }
                    onClickListener = {
                        Session.globalConfig.edit {
                            getCategory("dev")["dev_mode"] = false
                        }
                        finish()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDevModeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        recyclerAdapter = ParentAdapter(this) { parentId, id, _, data ->
            Session.globalConfig.edit {
                getCategory(parentId)[id] = data
            }
        }.apply {
            data = settings
            saved = Session.globalConfig.getAllConfigs()
            notifyDataSetChanged()
        }

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@DevModeActivity)
            adapter = recyclerAdapter
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        recyclerAdapter?.destroy()
        recyclerAdapter = null
    }
}