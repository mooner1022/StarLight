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

    override val name: String = "Device"

    override val objects: List<ApiObject> = getApiObjects<Device>()

    override val instanceClass: Class<Device> = Device::class.java

    override val instanceType: InstanceType = InstanceType.CLASS

    override fun getInstance(project: Project): Any = Device::class.java

    /**
     * The `Device` global: the device's Android version, battery, memory and power state, and a wake lock; a global in
     * legacy and API2 projects.
     *
     * Its members are static: use them on `Device` itself. The battery members read `getBatteryIntent()` and throw if the
     * system has none.
     *
     * MessengerBot's `getApiLevel`, `getBatteryTemp`, `getConnectedNetworkType`, `getMaxMemory`, `getModelName`,
     * `getTotalMemory` and `getTotalStorageSpace` are missing.
     */
    class Device {

        companion object {

            private var wakeLock: WakeLock? = null

            /**
             * The last battery broadcast, `Intent.ACTION_BATTERY_CHANGED`, with the battery's state in its extras.
             *
             * @return The `android.content.Intent`, or `null` if the system has none.
             */
            @JvmStatic
            fun getBatteryIntent(): Intent? =
                GlobalApplication.requireContext()
                    .registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

            private fun getPluggedState(): Int =
                getBatteryIntent()!!.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)

            /**
             * A new `android.os.Build` object.
             *
             * The device's details are static fields of `android.os.Build`, such as `MODEL` and `BRAND`.
             */
            @JvmStatic
            fun getBuild(): Build =
                Build()

            /**
             * The device's Android API level, such as `34` for Android 14.
             */
            @JvmStatic
            fun getAndroidVersionCode(): Int =
                Build.VERSION.SDK_INT

            /**
             * The device's Android version as users see it, such as `"14"`.
             */
            @JvmStatic
            fun getAndroidVersionName(): String =
                Build.VERSION.RELEASE

            /**
             * The device's product name, `android.os.Build.PRODUCT`.
             *
             * Despite its name, it isn't the brand that MessengerBot returns: `android.os.Build.BRAND` holds that.
             */
            @JvmStatic
            fun getPhoneBrand(): String =
                Build.PRODUCT

            /**
             * The device's model name, `android.os.Build.MODEL`.
             */
            @JvmStatic
            fun getPhoneModel(): String =
                Build.MODEL

            /**
             * Whether the device is plugged into an AC charger or USB.
             *
             * Wireless charging counts as not charging.
             */
            @JvmStatic
            fun isCharging(): Boolean {
                val plugged = getPluggedState()
                return plugged == BatteryManager.BATTERY_PLUGGED_AC || plugged == BatteryManager.BATTERY_PLUGGED_USB
            }

            /**
             * What the device is plugged into: `"acc"` (an AC charger), `"usb"`, `"wireless"` or `"unknown"`.
             *
             * An AC charger gives `"acc"`, where MessengerBot gives `"ac"`. `"unknown"` also covers not being plugged
             * in, and docks.
             */
            @JvmStatic
            fun getPlugType(): String = when(getPluggedState()) {
                BatteryManager.BATTERY_PLUGGED_AC -> "acc"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
                BatteryManager.BATTERY_PLUGGED_USB -> "usb"
                else -> "unknown"
            }

            /**
             * The battery's charge, as a percentage from `0` to `100`.
             *
             * It can have a fraction, as it is computed from the battery's level and scale.
             */
            @JvmStatic
            fun getBatteryLevel(): Number {
                val intent = getBatteryIntent()!!
                with(intent) {
                    val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = getIntExtra(BatteryManager.EXTRA_SCALE, -1)

                    return (level * 100) / scale.toFloat()
                }
            }

            /**
             * The battery's health, as a `BatteryManager.BATTERY_HEALTH_*` constant.
             *
             * `1` unknown, `2` good, `3` overheat, `4` dead, `5` over voltage, `6` unspecified failure, `7` cold;
             * `-1` if the system doesn't say.
             */
            @JvmStatic
            fun getBatteryHealth(): Number =
                getBatteryIntent()!!.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)

            /**
             * The battery's temperature in hundredths of a degree Celsius, so `3150` is 31.5 °C.
             *
             * This is ten times MessengerBot's value, which is in tenths of a degree (`315` for 31.5 °C). It is `-10`
             * if the system doesn't say.
             */
            @JvmStatic
            fun getBatteryTemperature(): Number =
                getBatteryIntent()!!.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) * 10f

            /**
             * The battery's voltage in millivolts, or `-1` if the system doesn't say.
             */
            @JvmStatic
            fun getBatteryVoltage(): Number =
                getBatteryIntent()!!.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)

            /**
             * The battery's charging status, as a `BatteryManager.BATTERY_STATUS_*` constant.
             *
             * `1` unknown, `2` charging, `3` discharging, `4` not charging, `5` full; `-1` if the system doesn't say.
             */
            @JvmStatic
            fun getBatteryStatus(): Number =
                getBatteryIntent()!!.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

            /*
             * Undocumented APIs.. fuck you
             */

            private fun getPowerManager() =
                GlobalApplication.requireContext()
                    .getSystemService(Context.POWER_SERVICE) as PowerManager

            /**
             * Acquires a wake lock, which keeps the device awake as [levelAndFlags] asks.
             *
             * StarLight holds one wake lock for all projects: a new call first releases the one acquired before, and
             * throws `RuntimeException` if [releaseWakeLock] already released it. Without [timeout] the wake lock is
             * held until [releaseWakeLock]; holding one for long drains the battery.
             *
             * @param levelAndFlags A `PowerManager` wake lock level, such as `1` (`PARTIAL_WAKE_LOCK`) to keep the CPU
             * running, optionally combined with its flags using `|`.
             * @param tag The wake lock's name, shown in the system's battery statistics.
             * @param timeout How long to hold it, in milliseconds; held until released when left out.
             */
            @JvmStatic
            @JvmOverloads
            @SuppressLint("WakelockTimeout")
            fun acquireWakeLock(levelAndFlags: Int, tag: String = "dev.mooner.starlight:api_wakelock_tag", timeout: Long? = null) {
                wakeLock?.release()
                wakeLock = getPowerManager().newWakeLock(levelAndFlags, tag)

                timeout?.let(wakeLock!!::acquire)
                    ?: wakeLock!!.acquire()
            }

            /**
             * Releases the wake lock that [acquireWakeLock] acquired.
             *
             * Unlike MessengerBot, [flags] is required: pass `0`. Nothing happens if no wake lock was ever acquired,
             * but releasing the same wake lock twice throws `RuntimeException`.
             *
             * @param flags `0`, or `1` (`PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY`).
             */
            @JvmStatic
            fun releaseWakeLock(flags: Int = 0) {
                wakeLock?.release(flags)
            }

            /**
             * The free memory, in bytes, of the heap StarLight's runtime has reserved so far.
             *
             * It is `Runtime.freeMemory()`: not the device's free memory, and the heap can still grow.
             */
            @JvmStatic
            fun getFreeMemory(): Long {
                return Runtime.getRuntime().freeMemory()
            }

            /**
             * The unallocated space, in bytes, of the storage that holds [path].
             *
             * @param path An absolute path on the storage to measure.
             * @return `0` if [path] doesn't exist.
             */
            @JvmStatic
            fun getFreeStorageSpace(path: String): Long {
                return File(path).freeSpace
            }

            /**
             * The name (SSID) of the Wi-Fi network the device is connected to.
             *
             * Don't rely on it: StarLight doesn't declare the Wi-Fi state permission Android requires for this. Even
             * with it, Android hides the name from apps without the location permission, which StarLight lacks too.
             *
             * @throws SecurityException As StarLight lacks the `ACCESS_WIFI_STATE` permission.
             */
            @JvmStatic
            fun getWifiName(): String {
                val manager = GlobalApplication.requireContext().getSystemService(Context.WIFI_SERVICE) as WifiManager
                val connInfo = manager.connectionInfo
                return connInfo.ssid
            }

            /**
             * Whether the device's battery saver is on.
             */
            @JvmStatic
            fun isPowerSaveMode(): Boolean =
                getPowerManager().isPowerSaveMode

            /**
             * Whether the device is interactive, which in practice means its screen is on.
             */
            @JvmStatic
            fun isScreenOn(): Boolean =
                getPowerManager().isInteractive
        }
    }
}