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
import dev.mooner.configdsl.utils.PeekType
import dev.mooner.peekalert.PeekAlert
import dev.mooner.starlight.PACKAGE_KAKAO_TALK
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.ActivityNotificationRulesBinding
import dev.mooner.starlight.databinding.CardSimpleListItemBinding
import dev.mooner.starlight.listener.NotificationListener
import dev.mooner.starlight.plugincore.chat.ParserSpecManager
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.utils.getStarLightDirectory
import dev.mooner.starlight.ui.common.confirmLeavingUnsaved
import dev.mooner.starlight.utils.createSuccessPeek
import dev.mooner.starlight.utils.bindPageHeader
import dev.mooner.starlight.utils.fitSkyPage
import dev.mooner.starlight.utils.peek
import dev.mooner.starlight.utils.restartIfNotReady
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

internal typealias Rules = MutableList<RuleData>

class NotificationRulesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationRulesBinding
    private val specs = ParserSpecManager.getAllSpecs().values.toTypedArray()
    private var rules: Rules = arrayListOf()
    private var isAutoRule: Boolean = true

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Brought back before the runtime started: start over from the splash, without restoring what needs it.
        if (restartIfNotReady()) {
            super.onCreate(null)
            finish()
            return
        }
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationRulesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        fitSkyPage(binding.root, binding.skyHeader, binding.scroll, binding.leave)
        bindPageHeader(binding.scroll, binding.skyHeader)


        val file = getStarLightDirectory().resolve(FILE_NAME)
        if (!file.exists() || !file.isFile || !file.canRead()) {
            file.parentFile?.mkdirs()
            file.writeText("[]")
        }
        rules = json.decodeFromString(file.readText())
        //config = FileConfig(file)

        isAutoRule = GlobalConfig.category("noti_rules").getBoolean("auto_rule", true)
        updateSubtitle()

        ConfigAdapter.Builder(this) {
            bind(binding.recyclerView)
            structure(::getStructure)
            configData(wrapData(rules))
            onValueUpdated { _, id, value, _ ->
                @Suppress("UNCHECKED_CAST")
                when (id) {
                    "rules"     -> rules = unwrapData(value as List<JsonObject>)
                    // Kept until the user saves, as the rules are. Written at once, leaving with "Don't save" would
                    // still switch which rules the listener takes the next time it starts.
                    "auto_rule" -> isAutoRule = value as Boolean
                }
                withContext(Dispatchers.Main) {
                    updateSubtitle()
                }

                if (binding.fabSaveConfig.isOrWillBeHidden) {
                    withContext(Dispatchers.Main) {
                        binding.fabSaveConfig.show()
                    }
                }
            }
        }.build()

        val save: () -> Boolean = save@{
            if (rules.any { it.packageName.isBlank() }) {
                peek(getString(R.string.rule_package_required), PeekType.ERROR)
                return@save false
            }
            // Without automatic rules and without one of its own, the app would take no message at all.
            if (!isAutoRule && rules.isEmpty()) {
                peek(getString(R.string.rule_none_without_auto), PeekType.ERROR)
                return@save false
            }
            rules.let(json::encodeToString)
                .let(file::writeText)
            GlobalConfig.edit {
                category("noti_rules")["auto_rule"] = isAutoRule
            }
            binding.fabSaveConfig.hide()
            createSuccessPeek(getString(R.string.config_saved), PeekAlert.Position.Bottom).peek()
            NotificationListener.updateRules()
            true
        }
        binding.fabSaveConfig.setOnClickListener { save() }
        confirmLeavingUnsaved(binding.leave, hasChanges = { binding.fabSaveConfig.isOrWillBeShown }, save = save)
    }

    /** Says which rules are in use: the saved ones don't count while rules are set automatically. */
    private fun updateSubtitle() {
        binding.subTitle.text = if (isAutoRule)
            getString(R.string.subtitle_notification_rules_auto)
        else
            resources.getQuantityString(R.plurals.subtitle_notification_rules, rules.size, rules.size)
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
                    title = getString(R.string.rules_auto_title)
                    description = getString(R.string.rules_auto_desc)
                    defaultValue = true
                    icon = Icon.LAUNCH
                    iconTintColor = color { "#7EAA92" }
                }
                list {
                    id = "rules"
                    title = getString(R.string.rules_list_title)
                    description = getString(R.string.rules_list_desc)
                    icon = Icon.DEVELOPER_BOARD
                    iconTintColor = color { "#7EAA92" }
                    dependency = "!auto_rule"
                    structure {
                        string {
                            id = "package_name"
                            title = getString(R.string.rules_package_title)
                            description = getString(R.string.rules_package_desc, PACKAGE_KAKAO_TALK)
                            icon = Icon.MARK_CHAT_READ
                            iconTintColor = color { "#82A284" }
                            require = { str ->
                                if (str.isBlank())
                                    getString(R.string.rules_package_empty)
                                else
                                    null
                            }
                        }
                        spinner {
                            id = "parser_spec"
                            title = getString(R.string.rules_spec_title)
                            //description = ""
                            icon = Icon.BRANCH
                            iconTintColor = color { "#ACBCFF" }
                            defaultIndex = 0
                            items = specs.mapIndexed { index, messageParserSpec -> "$index: ${messageParserSpec.name}" }
                        }
                        string {
                            id = "user_id"
                            title = getString(R.string.rules_user_id_title)
                            description = getString(R.string.rules_user_id_desc)
                            icon = Icon.ACCOUNT_BOX
                            iconTintColor = color { "#DEB6AB" }
                            defaultValue = "0"
                            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                            require = { str ->
                                if (str.isBlank())
                                    getString(R.string.rules_user_id_empty)
                                else
                                    null
                            }
                        }
                    }
                    onInflate { view ->
                        LayoutInflater
                            .from(view.context)
                            .inflate(R.layout.card_simple_list_item, view as FrameLayout, true)
                    }
                    onDraw { view, data ->
                        val binding = view
                            .findViewById<ConstraintLayout>(R.id.layout_simple_list_item)
                            .let(CardSimpleListItemBinding::bind)

                        binding.title.text = data["package_name"]!!.jsonPrimitive.content
                        binding.description.text = getString(R.string.rules_item_user_id, data["user_id"]?.jsonPrimitive?.int ?: 0)
                        binding.icon.visibility = View.GONE
                    }
                }
            }
        }
    }

    companion object {
        const val FILE_NAME = "notification_rules_v2.json"
    }
}