package dev.mooner.starlight.ui.settings.notifications

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.ActivityNotificationRulesBinding
import dev.mooner.starlight.databinding.ConfigButtonFlatBinding
import dev.mooner.starlight.listener.NotificationListener
import dev.mooner.starlight.plugincore.config.ConfigStructure
import dev.mooner.starlight.plugincore.config.FileConfig
import dev.mooner.starlight.plugincore.config.config
import dev.mooner.starlight.plugincore.utils.Icon
import dev.mooner.starlight.ui.config.ConfigAdapter
import dev.mooner.starlight.utils.PACKAGE_KAKAO_TALK
import dev.mooner.starlight.utils.getInternalDirectory

class NotificationRulesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationRulesBinding

    private lateinit var config: FileConfig

    //private var data: MutableMap<String, Map<String, >>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationRulesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.leave.setOnClickListener { finish() }

        val file = getInternalDirectory().resolve(FILE_NAME)
        config = FileConfig(file)

        binding.subTitle.text = getString(R.string.subtitle_notification_rules).format(config.getData().size)

        ConfigAdapter.Builder(this) {
            bind(binding.recyclerView)
            structure(::getStructure)
            savedData(config.getData())
            onConfigChanged { parentId, id, _, data ->
                config.category(parentId).setAny(id, data)
                if (binding.fabSaveConfig.isOrWillBeHidden)
                    binding.fabSaveConfig.show()
            }
        }.build()

        binding.fabSaveConfig.setOnClickListener {
            config.push()
            binding.fabSaveConfig.hide()
            NotificationListener.updateRules()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun getStructure(): ConfigStructure = config {
        category {
            id = "notification_rules"
            title = null
            items {
                list { 
                    id = "rules"
                    title = "규칙 목록"
                    structure {
                        string {
                            id = "package_name"
                            title = "패키지명"
                            description = "이 규칙이 적용될 패키지 명이에요. (ex) ${PACKAGE_KAKAO_TALK})"
                            icon = Icon.MARK_CHAT_READ
                            iconTintColor = color { "#82A284" }
                        }
                        string {
                            id = "user_id"
                            title = "유저 ID"
                            description = """
                                |이 규칙이 적용될 유저 ID 에요.
                                |0: 기본 유저
                                |10: 직장 프로필
                                |11: 직장 프로필(샤오미)
                                |999: XSpace(샤오미)
                            """.trimMargin()
                            icon = Icon.ACCOUNT_BOX
                            iconTintColor = color { "#DEB6AB" }
                            defaultValue = "0"
                            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                        }
                        list {
                            id = "params"
                            title = "변수 정의"
                            structure {
                                string {
                                    id = "name"
                                    title = "이름"
                                    hint = "변수명을 입력하세요.."
                                    icon = Icon.BOOKMARK
                                    iconTintColor = color { "#82A284" }
                                }
                                string {
                                    id = "value"
                                    title = "값"
                                    hint = "변수의 값을 입력하세요.."
                                    icon = Icon.LAYERS
                                    iconTintColor = color { "#DEB6AB" }
                                }
                            }
                            onInflate { view ->
                                LayoutInflater.from(view.context).inflate(R.layout.config_button_card, view as FrameLayout, true)
                            }
                            onDraw { view, data ->
                                val binding = ConfigButtonFlatBinding.bind(view.findViewById<ConstraintLayout>(R.id.layout_configButton))

                                binding.title.text = data["name"] as String
                                binding.description.text = data["value"] as String
                                binding.icon.visibility = View.INVISIBLE
                            }
                        }
                    }
                    onInflate { view ->
                        LayoutInflater.from(view.context).inflate(R.layout.config_button_card, view as FrameLayout, true)
                    }
                    onDraw { view, data ->
                        val binding = ConfigButtonFlatBinding.bind(view.findViewById<ConstraintLayout>(R.id.layout_configButton))

                        binding.title.text = data["package_name"] as String
                        binding.description.text = "유저 ID: ${data["user_id"] ?: 0}"
                        binding.icon.visibility = View.INVISIBLE
                    }
                }
            }
        }
    }

    companion object {
        const val FILE_NAME = "notification_rules.json"
    }
}