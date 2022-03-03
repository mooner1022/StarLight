package dev.mooner.starlight.ui.settings

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import dev.mooner.starlight.databinding.FragmentSettingsBinding
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.config.CategoryConfigObject
import dev.mooner.starlight.plugincore.config.config
import dev.mooner.starlight.plugincore.utils.Icon
import dev.mooner.starlight.ui.config.ConfigAdapter
import dev.mooner.starlight.ui.settings.dev.startDevModeActivity
import dev.mooner.starlight.ui.settings.info.AppInfoActivity
import dev.mooner.starlight.utils.restartApplication

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private var configAdapter: ConfigAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)

        configAdapter = ConfigAdapter.Builder(requireActivity()) {
            bind(binding.configRecyclerView)
            configs { getConfigList() }
            savedData(Session.globalConfig.getAllConfigs())
            onConfigChanged { parentId, id, _, data ->
                Session.globalConfig.edit {
                    category(parentId).setAny(id, data)
                }
            }
        }.build()

        return binding.root
    }

    private fun reloadConfigList(view: View) = configAdapter?.reload()

    private fun getConfigList(): List<CategoryConfigObject> {
        return config {
            category {
                id = "general"
                title = "일반"
                icon = Icon.SETTINGS
                iconTintColor = color { "#5584AC" }
                //textColor = color { "#706EB9" }
                flags = CategoryConfigObject.FLAG_NESTED
                items {
                    toggle {
                        id = "global_power"
                        title = "전역 전원"
                        description = "모든 봇의 전원을 관리합니다."
                        icon = Icon.POWER
                        iconTintColor = color { "#5584AC" }
                        defaultValue = false
                    }
                    button {
                        id = "reload_settings"
                        title = "설정 목록 새로고침"
                        icon = Icon.REFRESH
                        iconTintColor = color { "#FF8243" }
                        setOnClickListener(::reloadConfigList)
                    }
                }
            }
            category {
                id = "plugin"
                title = "플러그인"
                icon = Icon.ARCHIVE
                iconTintColor = color { "#95D1CC" }
                //textColor = color { "#706EB9" }
                flags = CategoryConfigObject.FLAG_NESTED
                items {
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
                        iconTintColor = color { "#95D1CC" }
                        defaultValue = false
                    }
                    button {
                        id = "restart_with_safe_mode"
                        title = "안전 모드로 재시작"
                        description = "안전모드 활성화 후 앱을 재시작합니다."
                        icon = Icon.REFRESH
                        iconTintColor = color { "#FF6F3C" }
                        setOnClickListener {
                            requireContext().restartApplication()
                        }
                        dependency = "safe_mode"
                    }
                }
            }
            category {
                id = "legacy"
                title = "레거시"
                icon = Icon.BOOKMARK
                iconTintColor = color { "#98BAE7" }
                //textColor = color { "#706EB9" }
                flags = CategoryConfigObject.FLAG_NESTED
                items {
                    toggle {
                        id = "use_legacy_event"
                        title = "레거시 이벤트 사용"
                        description = "메신저봇이나 채자봇과 호환되는 이벤트를 사용합니다."
                        icon = Icon.BOOKMARK
                        iconTintColor = color("#98BAE7")
                        defaultValue = false
                    }
                }
            }
            category {
                id = "info"
                title = "정보"
                textColor = color { "#706EB9" }
                items {
                    button {
                        id = "check_update"
                        title = "업데이트 확인"
                        icon = Icon.CLOUD_DOWNLOAD
                        iconTintColor = color { "#A7D0CD" }
                    }
                    button {
                        id = "app_info"
                        title = "앱 정보"
                        icon = Icon.INFO
                        iconTintColor = color { "#F1CA89" }
                        setOnClickListener {
                            startActivity(Intent(context, AppInfoActivity::class.java))
                        }
                    }
                    if (Session.globalConfig.getCategory("dev").getBoolean("dev_mode") == true) {
                        button {
                            id = "developer_mode"
                            title = "개발자 모드"
                            icon = Icon.DEVELOPER_MODE
                            iconTintColor = color { "#93B5C6" }
                            setOnClickListener {
                                requireContext().startDevModeActivity()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        configAdapter?.destroy()
        super.onDestroyView()
    }
}