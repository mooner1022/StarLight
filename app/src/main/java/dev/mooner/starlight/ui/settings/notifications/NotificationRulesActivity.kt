package dev.mooner.starlight.ui.settings.notifications

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import dev.mooner.starlight.PACKAGE_KAKAO_TALK
import dev.mooner.starlight.R
import dev.mooner.starlight.core.ApplicationSession
import dev.mooner.starlight.databinding.ActivityNotificationRulesBinding
import dev.mooner.starlight.databinding.ConfigButtonFlatBinding
import dev.mooner.starlight.listener.NotificationListener
import dev.mooner.starlight.plugincore.Session
import dev.mooner.starlight.plugincore.chat.ParserSpecManager
import dev.mooner.starlight.plugincore.config.ConfigStructure
import dev.mooner.starlight.plugincore.config.config
import dev.mooner.starlight.plugincore.config.data.DataMap
import dev.mooner.starlight.plugincore.config.data.PrimitiveTypedString
import dev.mooner.starlight.plugincore.config.data.typedAs
import dev.mooner.starlight.plugincore.utils.Icon
import dev.mooner.starlight.plugincore.utils.getStarLightDirectory
import dev.mooner.starlight.plugincore.version.Version
import dev.mooner.starlight.ui.config.ConfigAdapter
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

internal typealias Rules = MutableList<RuleData>

class NotificationRulesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationRulesBinding
    private val specs = ParserSpecManager.getAllSpecs().values.toTypedArray()
    //private lateinit var config: FileConfig
    private var data: Rules = arrayListOf()

    //private var data: MutableMap<String, Map<String, >>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationRulesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.leave.setOnClickListener { finish() }

        val file = getStarLightDirectory().resolve(FILE_NAME)

        if (!file.exists() || !file.isFile || !file.canRead()) {
            file.parentFile?.mkdirs()
            file.writeText(getDefaultFileContent())
        }
        data = Session.json.decodeFromString(file.readText())
        //config = FileConfig(file)

        binding.subTitle.text = getString(R.string.subtitle_notification_rules).format(data.size)

        ConfigAdapter.Builder(this) {
            bind(binding.recyclerView)
            structure(::getStructure)
            savedData(wrapData(data))
            onConfigChanged { _, _, _, value ->
                data = unwrapData(value as String)
                //config.category(parentId).setAny(id, value)
                if (binding.fabSaveConfig.isOrWillBeHidden)
                    binding.fabSaveConfig.show()
            }
        }.build()

        binding.fabSaveConfig.setOnClickListener {
            //config.push()
            data.let(Session.json::encodeToString)
                .let(file::writeText)
            binding.fabSaveConfig.hide()
            NotificationListener.updateRules()
        }
    }

    private fun wrapData(data: Rules): DataMap {
        val encoded = data
            .mapNotNull { it.convert(specs) }
            .let(Session.json::encodeToString)
        return mapOf("notification_rules" to mapOf("rules" to (encoded typedAs "String")))
    }

    private fun unwrapData(data: String): Rules {
        //val actualData = data["notification_rules"]!!["rules"]!!.value
        return data.let<_, List<MutableMap<String, PrimitiveTypedString>>>(Session.json::decodeFromString)
            .apply {
                for (map in this) {
                    val specIdx: Int = map["parser_spec"]?.castAs() ?: 0
                    map["parser_spec_id"] = specs[specIdx].id typedAs "String"
                    if ("user_id" !in map)
                        map["user_id"] = "0" typedAs "String"
                }
            }
            .map(RuleData::from)
            .toMutableList()
    }

    private fun getDefaultFileContent(): String {
        val specId = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || ApplicationSession.kakaoTalkVersion?.newerThan(
                Version.fromString("9.7.0")) == false)
            "default"
        else
            "android_r"

        val data = arrayOf(
            RuleData(
                packageName = "com.kakao.talk",
                userId = 0,
                parserSpecId = specId,
            )
        )
        return Session.json.encodeToString(data)
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
                    description = "맨 위에 위치한 규칙이 레거시 API 로직을 위한 기본 규칙으로 설정되며, 다른 규칙들은 레거시 API의 설정을 업데이트 하지 않습니다."
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

                        binding.title.text = data["package_name"] as String
                        binding.description.text = "유저 ID: ${data["user_id"] ?: 0}"
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