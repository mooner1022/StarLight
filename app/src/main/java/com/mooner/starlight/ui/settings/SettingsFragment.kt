package com.mooner.starlight.ui.settings

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.mooner.starlight.databinding.FragmentSettingsBinding
import com.mooner.starlight.plugincore.config.CategoryConfigObject
import com.mooner.starlight.plugincore.config.config
import com.mooner.starlight.plugincore.core.Session
import com.mooner.starlight.plugincore.utils.Icon
import com.mooner.starlight.ui.config.ParentAdapter
import com.mooner.starlight.ui.settings.dev.startDevModeActivity
import com.mooner.starlight.ui.settings.info.AppInfoActivity
import com.mooner.starlight.utils.Utils

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private var recyclerAdapter: ParentAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)

        recyclerAdapter = ParentAdapter(requireContext()) { parentId, id, _, data ->
            Session.globalConfig.edit {
                getCategory(parentId)[id] = data
            }
        }.apply {
            data = getConfigList()
            saved = Session.globalConfig.getAllConfigs()
            notifyDataSetChanged()
        }

        binding.configRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = recyclerAdapter
        }

        return binding.root
    }

    private fun reloadConfigList() {
        if (recyclerAdapter != null) {
            with(recyclerAdapter!!) {
                val preSize = data.size
                data = listOf()
                notifyItemRangeRemoved(0, preSize)
                data = getConfigList()
                notifyItemRangeRemoved(0, data.size)
            }
        }
    }

    private fun getConfigList(): List<CategoryConfigObject> {
        return config {
            category {
                id = "general"
                title = "일반"
                textColor = color { "#706EB9" }
                items = items {
                    toggle {
                        id = "global_power"
                        title = "전역 전원"
                        description = "모든 봇의 전원을 관리합니다."
                        icon = Icon.POWER
                        iconTintColor = color { "#62D2A2" }
                        defaultValue = false
                    }
                    button {
                        id = "reload_settings"
                        title = "설정 목록 새로고침"
                        icon = Icon.REFRESH
                        iconTintColor = color { "#FF8243" }
                        onClickListener = {
                            reloadConfigList()
                        }
                    }
                }
            }
            category {
                id = "plugin"
                title = "플러그인"
                textColor = color { "#706EB9" }
                items = items {
                    string {
                        id = "load_timeout"
                        title = "플러그인 로드 시간 제한(ms)"
                        description = "플러그인의 로드 시간을 제한합니다. 설정한 시간을 초과한 플러그인은 불러오지 않습니다."
                        icon = Icon.TIMER_OFF
                        iconTintColor = color { "#BE9FE1" }
                        inputType = InputType.TYPE_CLASS_NUMBER
                        require = { text ->
                            when(val intVal = text.toIntOrNull()) {
                                null -> "올바르지 않은 값입니다."
                                else -> {
                                    if (intVal < 500) {
                                        "설정할 수 있는 최솟값은 500ms 입니다."
                                    } else {
                                        null
                                    }
                                }
                            }
                        }
                    }
                    toggle {
                        id = "safe_mode"
                        title = "안전 모드 (재시작 필요)"
                        description = "플러그인 안전 모드를 활성화합니다. 모든 플러그인을 로드하지 않습니다."
                        icon = Icon.LAYERS_CLEAR
                        iconTintColor = color { "#62D2A2" }
                        defaultValue = false
                    }
                    button {
                        id = "restart_with_safe_mode"
                        title = "안전 모드로 재시작"
                        description = "안전모드 활성화 후 앱을 재시작합니다."
                        icon = Icon.REFRESH
                        iconTintColor = color { "#FF6F3C" }
                        onClickListener = {
                            Utils.restartApplication(it.context)
                        }
                        dependency = "safe_mode"
                    }
                }
            }
            category {
                id = "info"
                title = "정보"
                textColor = color { "#706EB9" }
                items = items {
                    button {
                        id = "check_update"
                        title = "업데이트 확인"
                        icon = Icon.CLOUD_DOWNLOAD
                        iconTintColor = color { "#A7D0CD" }
                        onClickListener = {

                        }
                    }
                    button {
                        id = "app_info"
                        title = "앱 정보"
                        icon = Icon.INFO
                        iconTintColor = color { "#F1CA89" }
                        onClickListener = {
                            startActivity(Intent(context, AppInfoActivity::class.java))
                        }
                    }
                    if (Session.globalConfig.getCategory("dev").getBoolean("dev_mode") == true) {
                        button {
                            id = "developer_mode"
                            title = "개발자 모드"
                            icon = Icon.DEVELOPER_MODE
                            iconTintColor = color { "#93B5C6" }
                            onClickListener = {
                                requireContext().startDevModeActivity()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        recyclerAdapter?.destroy()
        recyclerAdapter = null
    }
}