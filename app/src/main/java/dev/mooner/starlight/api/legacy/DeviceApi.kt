package dev.mooner.starlight.api.legacy

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import dev.mooner.starlight.core.GlobalApplication
import dev.mooner.starlight.plugincore.api.Api
import dev.mooner.starlight.plugincore.api.ApiObject
import dev.mooner.starlight.plugincore.api.InstanceType
import dev.mooner.starlight.plugincore.project.Project
import java.io.File

@Suppress("unused")
class DeviceApi: Api<DeviceApi.Device>() {

    class Device {

        companion object {

            private var wakeLock: WakeLock? = null

            @JvmStatic
            fun getBatteryIntent(): Intent? =
                GlobalApplication.requireContext()
                    .registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

            private fun getPluggedState(): Int =
                getBatteryIntent()!!.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)

            @JvmStatic
            fun getBuild(): Build =
                Build()

            @JvmStatic
            fun getAndroidVersionCode(): Int =
                Build.VERSION.SDK_INT

            @JvmStatic
            fun getAndroidVersionName(): String =
                Build.VERSION.RELEASE

            @JvmStatic
            fun getPhoneBrand(): String =
                Build.PRODUCT

            @JvmStatic
            fun getPhoneModel(): String =
                Build.MODEL

            @JvmStatic
            fun isCharging(): Boolean {
                val plugged = getPluggedState()
                return plugged == BatteryManager.BATTERY_PLUGGED_AC || plugged == BatteryManager.BATTERY_PLUGGED_USB
            }

            @JvmStatic
            fun getPlugType(): String = when(getPluggedState()) {
                BatteryManager.BATTERY_PLUGGED_AC -> "acc"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
                BatteryManager.BATTERY_PLUGGED_USB -> "usb"
                else -> "unknown"
            }

            @JvmStatic
            fun getBatteryLevel(): Number {
                val intent = getBatteryIntent()!!
                with(intent) {
                    val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = getIntExtra(BatteryManager.EXTRA_SCALE, -1)

                    return (level * 100) / scale.toFloat()
                }
            }

            @JvmStatic
            fun getBatteryHealth(): Number =
                getBatteryIntent()!!.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)

            @JvmStatic
            fun getBatteryTemperature(): Number =
                getBatteryIntent()!!.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) * 10f

            @JvmStatic
            fun getBatteryVoltage(): Number =
                getBatteryIntent()!!.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)

            @JvmStatic
            fun getBatteryStatus(): Number =
                getBatteryIntent()!!.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

            /*
             * Undocumented APIs.. fuck you
             */

            private fun getPowerManager() =
                GlobalApplication.requireContext()
                    .getSystemService(Context.POWER_SERVICE) as PowerManager

            @JvmStatic
            @JvmOverloads
            @SuppressLint("WakelockTimeout")
            fun acquireWakeLock(levelAndFlags: Int, tag: String = "dev.mooner.starlight:api_wakelock_tag", timeout: Long? = null) {
                wakeLock?.release()
                wakeLock = getPowerManager().newWakeLock(levelAndFlags, tag)

                timeout?.let(wakeLock!!::acquire)
                    ?: wakeLock!!.acquire()
            }

            @JvmStatic
            fun releaseWakeLock(flags: Int = 0) {
                wakeLock?.release(flags)
            }

            @JvmStatic
            fun getFreeMemory(): Long {
                return Runtime.getRuntime().freeMemory()
            }

            @JvmStatic
            fun getFreeStorageSpace(path: String): Long {
                return File(path).freeSpace
            }

            @JvmStatic
            fun getWifiName(): String {
                val manager = GlobalApplication.requireContext().getSystemService(Context.WIFI_SERVICE) as WifiManager
                val connInfo = manager.connectionInfo
                return connInfo.ssid
            }

            fun isPowerSaveMode(): Boolean =
                getPowerManager().isPowerSaveMode

            fun isScreenOn(): Boolean =
                getPowerManager().isInteractive
        }
    }

    override val name: String = "Device"

    override val objects: List<ApiObject> = listOf(
        function {
            name = "getAndroidVersionCode"
            returns = Int::class.java
        },
        function {
            name = "getAndroidVersionName"
            returns = String::class.java
        },
        function {
            name = "getPhoneBrand"
            returns = String::class.java
        },
        function {
            name = "getPhoneModel"
            returns = String::class.java
        },
        function {
            name = "getBatteryIntent"
            returns = Intent::class.java
        },
        function {
            name = "isCharging"
            returns = Boolean::class.java
        },
        function {
            name = "getPlugType"
            returns = String::class.java
        },
        function {
            name = "getBatteryLevel"
            returns = Number::class.java
        },
        function {
            name = "getBatteryHealth"
            returns = Number::class.java
        },
        function {
            name = "getBatteryTemperature"
            returns = Number::class.java
        },
        function {
            name = "getBatteryVoltage"
            returns = Number::class.java
        },
        function {
            name = "getBatteryStatus"
            returns = Number::class.java
        },
        function {
            name = "getBatteryIntent"
            returns = Intent::class.java
        },
    )

    override val instanceClass: Class<Device> = Device::class.java

    override val instanceType: InstanceType = InstanceType.CLASS

    override fun getInstance(project: Project): Any = Device::class.java
}