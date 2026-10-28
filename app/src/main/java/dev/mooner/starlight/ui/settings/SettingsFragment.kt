package dev.mooner.starlight.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import dev.mooner.configdsl.ConfigStructure
import dev.mooner.configdsl.adapters.ConfigAdapter
import dev.mooner.configdsl.options.ButtonConfigOption
import dev.mooner.starlight.databinding.FragmentSettingsBinding
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.plugincore.utils.onSaveConfigAdapter
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private var configAdapter: ConfigAdapter? = null
    private var structure: ConfigStructure = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)

        // Transfer legacy data
        val updatedData: MutableMap<String, MutableMap<String, JsonElement>> = hashMapOf()
        val category = GlobalConfig.category("notifications")
        for ((key, value) in category.data.toMap()) { // Clone to avoid Concurrent Modification
            if (value !is JsonObject) {
                val destKey = when(key) {
                    "read_noti_perm",
                    "use_legacy_event",
                    "log_received_message" ->
                        "event"
                    "set_package_rules",
                    "use_on_notification_posted",
                    "magic" ->
                        "noti"
                    else -> continue
                }
                updatedData.computeIfAbsent(destKey) { hashMapOf() }[key] = value
                category.remove(key)
            }
        }
        updatedData.mapValues { (_, v) -> JsonObject(v) }.forEach(category::setRaw)

        configAdapter = ConfigAdapter.Builder(requireActivity()) {
            bind(binding.configRecyclerView)
            structure(::getSettingStruct)
            configData(GlobalConfig.getMutableData())
            onValueUpdated(GlobalConfig::onSaveConfigAdapter)
        }.build()

        return binding.root
    }

    private fun getSettingStruct(): ConfigStructure =
        getSettingsStruct().also { structure = it }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    internal fun refreshStatus() {
        val context = context ?: return
        val descriptions = mapOf(
            OPTION_NOTIFICATION_ACCESS  to context.notificationAccessDescription(),
            OPTION_BATTERY_OPTIMIZATION to context.batteryOptimizationDescription(),
            OPTION_CLEAR_LOGS           to context.logFilesDescription(),
        )
        for ((id, description) in descriptions)
            (structure.findOptionById(id) as? ButtonConfigOption)?.updateDescription(description)
    }

    override fun onDestroyView() {
        configAdapter?.destroy()
        super.onDestroyView()
    }
}