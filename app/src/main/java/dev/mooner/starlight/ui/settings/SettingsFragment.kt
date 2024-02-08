package dev.mooner.starlight.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import dev.mooner.starlight.databinding.FragmentSettingsBinding
import dev.mooner.starlight.plugincore.config.ConfigStructure
import dev.mooner.starlight.plugincore.config.GlobalConfig
import dev.mooner.starlight.ui.config.ConfigAdapter
import dev.mooner.starlight.utils.isNoobMode
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import dev.mooner.starlight.ui.settings.getSettingsStruct as actualSettingStruct

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private var configAdapter: ConfigAdapter? = null

    private val permListener = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        permCallback?.invoke(result)
    }
    private var permCallback: ((result: ActivityResult) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)

        configAdapter = ConfigAdapter.Builder(requireActivity()) {
            bind(binding.configRecyclerView)
            structure(::getSettingStruct)
            savedData(GlobalConfig.getDataMap())
            onConfigChanged { parentId, id, _, data ->
                GlobalConfig.edit {
                    category(parentId).setAny(id, data)
                }
            }
        }.build()

        return binding.root
    }

    context(Fragment)
    internal suspend fun requestAppInstallPermission() = suspendCoroutine { cont ->
        permCallback = { result ->
            cont.resume(result.resultCode == Activity.RESULT_OK)
            permCallback = null
        }
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(Uri.parse("package:${requireContext().packageName}"))
        permListener.launch(intent)
    }

    private fun getSettingStruct(): ConfigStructure =
        if (isNoobMode) getNoobSettingStruct() else actualSettingStruct()

    override fun onDestroyView() {
        configAdapter?.destroy()
        super.onDestroyView()
    }
}