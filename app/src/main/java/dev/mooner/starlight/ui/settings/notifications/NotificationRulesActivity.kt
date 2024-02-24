package dev.mooner.starlight.ui.settings.notifications

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import dev.mooner.configdsl.ConfigStructure
import dev.mooner.configdsl.Icon
import dev.mooner.configdsl.MutableDataMap
import dev.mooner.configdsl.adapters.ConfigAdapter
import dev.mooner.configdsl.config
import dev.mooner.configdsl.options.list
import dev.mooner.configdsl.options.spinner
import dev.mooner.configdsl.options.string
import dev.mooner.configdsl.options.toggle
import dev.mooner.peekalert.PeekAlert
import dev.mooner.starlight.PACKAGE_KAKAO_TALK
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.ActivityNotificationRulesBinding
import dev.mooner.starlight.databinding.ConfigButtonFlatBinding
import dev.mooner.starlight.listener.NotificationListener
import dev.mooner.starlight.plugincore.chat.ParserSpecManager
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.utils.getStarLightDirectory
import dev.mooner.starlight.utils.createSimplePeek
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

internal typealias Rules = MutableList<RuleData>

class NotificationRulesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationRulesBinding
    private val specs = ParserSpecManager.getAllSpecs().values.toTypedArray()
    private var rules: Rules = arrayListOf()

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationRulesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.leave.setOnClickListener { finish() }

        val file = getStarLightDirectory().resolve(FILE_NAME)
        if (!file.exists() || !file.isFile || !file.canRead()) {
            file.parentFile?.mkdirs()
            file.writeText("[]")
        }
        rules = json.decodeFromString(file.readText())
        //config = FileConfig(file)

        binding.subTitle.text = getString(R.string.subtitle_notification_rules).format(rules.size)

        ConfigAdapter.Builder(this) {
            bind(binding.recyclerView)
            structure(::getStructure)
            configData(wrapData(rules))
            onValueUpdated { _, id, value, jsonValue ->
                if (id == "rules")
                    rules = unwrapData(value as List<JsonObject>)
                else
                    GlobalConfig.edit {
                        category("noti_rules").setRaw(id, jsonValue)
                    }

                if (binding.fabSaveConfig.isOrWillBeHidden) {
                    withContext(Dispatchers.Main) {
                        binding.fabSaveConfig.show()
                    }
                }
            }
        }.build()

        binding.fabSaveConfig.setOnClickListener {
            //config.push()
            rules.let(json::encodeToString)
                .let(file::writeText)
            binding.fabSaveConfig.hide()
            createSimplePeek(
                text = "정보 저장 성공!"
            ) {
                position = PeekAlert.Position.Bottom
                iconRes = R.drawable.ic_round_check_24
                iconTint(res = R.color.noctis_green)
                backgroundColor(res = R.color.background_popup)
            }.peek()
            NotificationListener.updateRules()
        }
    }

    private fun wrapData(data: Rules): MutableDataMap {
        val encoded = JsonArray(data.map(json::encodeToJsonElement).map { elem ->
            val mutable = elem.jsonObject.toMutableMap()
            val specID  = mutable["parser_spec_id"]?.jsonPrimitive?.content
            mutable["parser_spec"] = JsonPrimitive(specs.indexOfFirst { it.id == specID })
            JsonObject(mutable)
        })
        return mutableMapOf(
            "notification_rules" to mutableMapOf(
                "rules" to encoded,
                "auto_rule" to GlobalConfig
                    .category("noti_rules")
                    .getBoolean("auto_rule", true)
                    .let(::JsonPrimitive)
            )
        )
    }

    private fun unwrapData(data: List<JsonObject>): Rules {
        return data.map { entry ->
            val mutable = entry.toMutableMap()
            mutable["parser_spec"]?.jsonPrimitive?.int?.let { index ->
                mutable["parser_spec_id"] = JsonPrimitive(specs[index].id)
                mutable -= "parser_spec"
            }
            json.decodeFromJsonElement<RuleData>(JsonObject(mutable))
        }.toMutableList()
    }

    @SuppressLint("SetTextI18n")
    private fun getStructure(): ConfigStructure = config {
        category {
            id = "notification_rules"
            title = null
            items {
                toggle {
                    id = "auto_rule"
                    title = "규칙 자동 설정"
                    description = "규칙을 실행 환경에 따라 자동으로 설정합니다. 실제 환경에 따라 올바르게 동작하지 않을 수 있습니다."
                    defaultValue = true
                    icon = Icon.LAUNCH
                    iconTintColor = color { "#7EAA92" }
                }
                list {
                    id = "rules"
                    title = "규칙 목록"
                    description = "맨 위에 위치한 규칙이 레거시 API 로직을 위한 기본 규칙으로 설정되며, 다른 규칙들은 레거시 API의 설정을 업데이트 하지 않습니다."
                    icon = Icon.DEVELOPER_BOARD
                    iconTintColor = color { "#7EAA92" }
                    dependency = "!auto_rule"
                    structure {
                        string {
                            id = "package_name"
                            title = "패키지명"
                            description = "이 규칙이 적용될 패키지 명이에요. (ex) ${PACKAGE_KAKAO_TALK})"
                            icon = Icon.MARK_CHAT_READ
                            iconTintColor = color { "#82A284" }
                            require = { str ->
                                if (str.isBlank())
                                    "패키지명이 비어있으면 안되겠죠?"
                                else
                                    null
                            }
                        }
                        spinner {
                            id = "parser_spec"
                            title = "메세지 변환/분석 스펙"
                            //description = ""
                            icon = Icon.BRANCH
                            iconTintColor = color { "#ACBCFF" }
                            defaultIndex = 0
                            items = specs.mapIndexed { index, messageParserSpec -> "$index: ${messageParserSpec.name}" }
                        }
                        string {
                            id = "user_id"
                            title = "유저 ID"
                            description = """
                                |이 규칙이 적용될 유저 ID 에요.
                                |0: 기본 유저
                                |10: 직장 프로필
                                |11: 직장 프로필(샤오미)
                                |95: 듀얼 메신저(삼성)
                                |999: XSpace(샤오미)
                            """.trimMargin()
                            icon = Icon.ACCOUNT_BOX
                            iconTintColor = color { "#DEB6AB" }
                            defaultValue = "0"
                            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                            require = { str ->
                                if (str.isBlank())
                                    "유저 ID도 비어있으면 안되겠죠?"
                                else
                                    null
                            }
                        }
                    }
                    onInflate { view ->
                        LayoutInflater
                            .from(view.context)
                            .inflate(R.layout.config_button_card, view as FrameLayout, true)
                    }
                    onDraw { view, data ->
                        val binding = view
                            .findViewById<ConstraintLayout>(R.id.layout_configButton)
                            .let(ConfigButtonFlatBinding::bind)

                        binding.title.text = data["package_name"]!!.jsonPrimitive.content
                        binding.description.text = "유저 ID: ${data["user_id"]?.jsonPrimitive?.int ?: 0}"
                        binding.icon.visibility = View.INVISIBLE
                    }
                }
            }
        }
    }

    companion object {
        const val FILE_NAME = "notification_rules_v2.json"
    }
}