/*
 * AllowNotificationFragment.kt created by Minki Moon(mooner1022) on 22. 2. 5. 오후 3:35
 * Copyright (c) mooner1022. all rights reserved.
 * This code is licensed under the GNU General Public License v3.0.
 */

package dev.mooner.starlight.ui.splash.quickstart.steps

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import dev.mooner.starlight.R
import dev.mooner.starlight.databinding.FragmentAllowNotificationBinding
import dev.mooner.starlight.plugincore.config.CategoryConfigObject
import dev.mooner.starlight.plugincore.config.config
import dev.mooner.starlight.plugincore.utils.Icon
import dev.mooner.starlight.ui.config.ConfigAdapter
import dev.mooner.starlight.ui.splash.quickstart.QuickStartActivity

class AllowNotificationFragment : Fragment() {

    private var _binding: FragmentAllowNotificationBinding? = null
    private val binding get() = _binding!!
    private var adapter: ConfigAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAllowNotificationBinding.inflate(inflater, container, false)
        val activity = (activity as QuickStartActivity)

        adapter = ConfigAdapter.Builder(activity) {
            bind(binding.recyclerView)
            configs {
                getConfig(activity)
            }
            lifecycleOwner(this@AllowNotificationFragment)
        }.build()

        activity.hideButton(QuickStartActivity.Buttons.Next)

        return binding.root
    }

    private fun getConfig(activity: QuickStartActivity): List<CategoryConfigObject> {
        return config {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                category {
                    id = "allow_battery"
                    items {
                        button {
                            id = "ignore_battery_optimization"
                            title = "배터리 최적화 무시"
                            description = "앱이 꺼지거나 절전모드가 되지 않고 계속해서 실행될 수 있어요. (선택)"
                            icon = Icon.BATTERY_SAVER
                            iconTintColor = color("#7ACA8A")
                            setOnClickListener {
                                requestIgnoreBatteryOptimization(activity)
                            }
                        }
                    }
                }
            }
            category {
                id = "permissions"
                title = "추가 권한"
                textColor = activity.getColor(R.color.main_purple)
                items {
                    button {
                        id = "allow_notification_read"
                        title = "알림 읽기 권한 허용"
                        description = "앱이 메신저의 알림을 읽을 수 있어요. (필수)"
                        icon = Icon.NOTIFICATIONS_ACTIVE
                        iconTintColor = color("#ffd866")
                        setOnClickListener {
                            if (isNotificationGranted(activity))
                                Snackbar.make(it, "이미 권한이 승인되었어요!", Snackbar.LENGTH_SHORT).show()
                            else
                                requestNotificationPermission(activity)
                        }
                    }
                    button {
                        id = "check_allowed"
                        title = "권한 허용 확인"
                        description = "위 권한을 허용하신 후 눌러주세요"
                        icon = Icon.CHECK
                        iconTintColor = color("#7ACA8A")
                        setOnClickListener {
                            if (isNotificationGranted(activity))
                                activity.showButton(QuickStartActivity.Buttons.Next)
                            else {
                                Snackbar.make(it, "아직 권한이 허용되지 않았어요.", Snackbar.LENGTH_SHORT).show()
                                activity.hideButton(QuickStartActivity.Buttons.Next)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun requestIgnoreBatteryOptimization(context: Context) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val packageName = context.packageName

        Intent().apply {
            if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
                action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
            } else {
                data = Uri.parse("package:$packageName")
                action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            }
        }.let(context::startActivity)
    }

    private fun isNotificationGranted(context: Context): Boolean {
        val packages = NotificationManagerCompat.getEnabledListenerPackages(context)
        return context.packageName in packages
    }

    private fun requestNotificationPermission(context: Context) =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).let(context::startActivity)
}