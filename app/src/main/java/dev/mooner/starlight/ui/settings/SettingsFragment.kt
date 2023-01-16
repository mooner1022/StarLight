package dev.mooner.starlight.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import dev.mooner.starlight.databinding.FragmentSettingsBinding
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.config.CategoryConfigObject
import dev.mooner.starlight.plugincore.config.ConfigStructure
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.config.config
import dev.mooner.starlight.plugincore.utils.Icon
import dev.mooner.starlight.ui.config.ConfigAdapter
import dev.mooner.starlight.ui.settings.dev.startDevModeActivity
import dev.mooner.starlight.ui.settings.info.AppInfoActivity
import dev.mooner.starlight.ui.settings.notifications.NotificationRulesActivity
import dev.mooner.starlight.utils.restartApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

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
            structure(::getSettingStruct)
            savedData(GlobalConfig.getAllConfigs())
            onConfigChanged { parentId, id, _, data ->
                GlobalConfig.edit {
                    category(parentId).setAny(id, data)
                }
            }
        }.build()

        return binding.root
    }

    private fun reloadConfigList() = configAdapter?.reload()

    private fun getSettingStruct(): ConfigStructure {
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
                        description = "모든 봇의 답장/처리 여부를 결정합니다."
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
                id = "project"
                title = "프로젝트"
                icon = Icon.PROJECTS
                iconTintColor = color { "#B4CFB0" }
                //textColor = color { "#706EB9" }
                flags = CategoryConfigObject.FLAG_NESTED
                items {
                    toggle {
                        id = "compile_animation"
                        title = "컴파일 애니메이션"
                        description = "컴파일 시 프로그레스 바의 애니메이션을 부드럽게 조정합니다."
                        icon = Icon.COMPRESS
                        iconTintColor = color { "#FEAC5E" }
                        defaultValue = true
                    }
                    toggle {
                        id = "load_global_libraries"
                        title = "전역 모듈 로드"
                        description = "global_modules 폴더 내의 모듈을 컴파일 시 적용합니다. 신뢰하지 않는 코드가 실행될 수 있습니다."
                        icon = Icon.FOLDER
                        iconTintColor = color { "#4BC0C8" }
                        defaultValue = false
                        warnOnEnable {
                            """
                                |이 기능은 신뢰되지 않는 코드를 기기에서 실행할 수 있으며, 이로 인해 발생하는 어떠한 상해나 손실도 개발자는 보상하거나 보장하지 않습니다.
                                |기능을 활성화할까요?
                            """.trimMargin()
                        }
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
                        setOnClickListener(requireContext()::restartApplication)
                        dependency = "safe_mode"
                    }
                }
            }
            category {
                id = "notifications"
                title = "알림, 이벤트"
                icon = Icon.NOTIFICATIONS
                iconTintColor = color { "#98BAE7" }
                //textColor = color { "#706EB9" }
                flags = CategoryConfigObject.FLAG_NESTED
                items {
                    toggle {
                        id = "use_legacy_event"
                        title = "레거시 이벤트 사용"
                        description = "메신저봇이나 채자봇과 호환되는 이벤트를 사용합니다."
                        icon = Icon.BOOKMARK
                        iconTintColor = color { "#98BAE7" }
                        defaultValue = false
                    }
                    button {
                        id = "set_package_rules"
                        title = "패키지 규칙 설정"
                        description = "패키지 별 알림을 수신할 규칙을 설정합니다."
                        icon = Icon.DEVELOPER_BOARD
                        iconTintColor = color { "#B4E197" }
                        setOnClickListener { _ ->
                            startActivity(Intent(activity, NotificationRulesActivity::class.java))
                        }
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
                        setOnClickListener { _ ->
                            startActivity(Intent(context, AppInfoActivity::class.java))
                        }
                    }
                    if (GlobalConfig.category("dev").getBoolean("dev_mode") == true) {
                        button {
                            id = "developer_mode"
                            title = "개발자 모드"
                            icon = Icon.DEVELOPER_MODE
                            iconTintColor = color { "#93B5C6" }
                            setOnClickListener(requireContext()::startDevModeActivity)
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