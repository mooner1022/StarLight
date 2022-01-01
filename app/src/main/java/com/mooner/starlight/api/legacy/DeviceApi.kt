package com.mooner.starlight.api.legacy

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import com.mooner.starlight.core.GlobalApplication
import com.mooner.starlight.plugincore.api.Api
import com.mooner.starlight.plugincore.api.ApiObject
import com.mooner.starlight.plugincore.api.InstanceType
import com.mooner.starlight.plugincore.project.Project


class DeviceApi: Api<DeviceApi.Device>() {

    class Device {

        companion object {

            @JvmStatic
            fun getBatteryIntent(): Intent? = GlobalApplication.requireContext().registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

            private fun getPluggedState(): Int = getBatteryIntent()!!.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)

            @JvmStatic
            fun getBuild(): Build = Build()

            @JvmStatic
            fun getAndroidVersionCode(): Int = Build.VERSION.SDK_INT

            @JvmStatic
            fun getAndroidVersionName(): String = Build.VERSION.RELEASE

            @JvmStatic
            fun getPhoneBrand(): String = Build.PRODUCT

            @JvmStatic
            fun getPhoneModel(): String = Build.MODEL

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
            fun getBatteryHealth(): Number = getBatteryIntent()!!.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)

            @JvmStatic
            fun getBatteryTemperature(): Number = getBatteryIntent()!!.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) * 10f

            @JvmStatic
            fun getBatteryVoltage(): Number = getBatteryIntent()!!.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)

            @JvmStatic
            fun getBatteryStatus(): Number = getBatteryIntent()!!.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
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