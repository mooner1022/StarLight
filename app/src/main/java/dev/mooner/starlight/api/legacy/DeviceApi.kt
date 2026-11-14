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
import dev.mooner.starlight.api.msgbot.ScriptArgs
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
     * The `Device` global: the device's Android version, battery, memory, storage and power state, and a wake lock; a
     * global in legacy and API2 projects.
     *
     * Its members are static: use them on `Device` itself. The battery members give `-1` (and `getPlugType()` gives
     * `"unknown"`) when the system has no battery information.
     */
    class Device {

        companion object {

            private const val DEFAULT_TAG = "dev.mooner.starlight:api_wakelock_tag"

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

            private fun batteryExtra(name: String): Int =
                getBatteryIntent()?.getIntExtra(name, -1) ?: -1

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
             * The device's brand, `android.os.Build.BRAND`, such as `"samsung"`.
             */
            @JvmStatic
            fun getPhoneBrand(): String =
                Build.BRAND

            /**
             * The device's code name, `android.os.Build.DEVICE`, such as `"dm1q"`.
             *
             * It isn't the market name of the model, which `android.os.Build.MODEL` holds.
             */
            @JvmStatic
            fun getPhoneModel(): String =
                Build.DEVICE

            /**
             * Whether the device is plugged into an AC charger, USB or a wireless charger.
             *
             * It is `false` when the system has no battery information.
             */
            @JvmStatic
            fun isCharging(): Boolean {
                val plugged = batteryExtra(BatteryManager.EXTRA_PLUGGED)
                return plugged == BatteryManager.BATTERY_PLUGGED_AC ||
                    plugged == BatteryManager.BATTERY_PLUGGED_USB ||
                    plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS
            }

            /**
             * What the device is plugged into: `"ac"` (an AC charger), `"usb"`, `"wireless"` or `"unknown"`.
             *
             * `"unknown"` also covers not being plugged in, and docks.
             */
            @JvmStatic
            fun getPlugType(): String = when (batteryExtra(BatteryManager.EXTRA_PLUGGED)) {
                BatteryManager.BATTERY_PLUGGED_AC       -> "ac"
                BatteryManager.BATTERY_PLUGGED_USB      -> "usb"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
                else                                    -> "unknown"
            }

            /**
             * The battery's level, as the system reports it: a whole number that is a percentage when the battery's
             * scale is 100, as it is on nearly every device.
             *
             * @return `-1` if the system doesn't say.
             */
            @JvmStatic
            fun getBatteryLevel(): Int =
                batteryExtra(BatteryManager.EXTRA_LEVEL)

            /**
             * The battery's health, as a `BatteryManager.BATTERY_HEALTH_*` constant.
             *
             * `1` unknown, `2` good, `3` overheat, `4` dead, `5` over voltage, `6` unspecified failure, `7` cold;
             * `-1` if the system doesn't say.
             */
            @JvmStatic
            fun getBatteryHealth(): Int =
                batteryExtra(BatteryManager.EXTRA_HEALTH)

            /**
             * The battery's temperature in tenths of a degree Celsius, so `315` is 31.5 °C.
             *
             * @return `-1` if the system doesn't say.
             */
            @JvmStatic
            fun getBatteryTemperature(): Int =
                batteryExtra(BatteryManager.EXTRA_TEMPERATURE)

            /**
             * The battery's voltage in millivolts, or `-1` if the system doesn't say.
             */
            @JvmStatic
            fun getBatteryVoltage(): Int =
                batteryExtra(BatteryManager.EXTRA_VOLTAGE)

            /**
             * The battery's charging status, as a `BatteryManager.BATTERY_STATUS_*` constant.
             *
             * `1` unknown, `2` charging, `3` discharging, `4` not charging, `5` full; `-1` if the system doesn't say.
             */
            @JvmStatic
            fun getBatteryStatus(): Int =
                batteryExtra(BatteryManager.EXTRA_STATUS)

            private fun getPowerManager() =
                GlobalApplication.requireContext()
                    .getSystemService(Context.POWER_SERVICE) as PowerManager

            /**
             * Acquires a wake lock, which keeps the device awake as [levelAndFlags] asks.
             *
             * StarLight holds one wake lock for all projects: a new call first releases the one acquired before, if it
             * is still held. Without a [timeout], or with one of `0` or less, the wake lock is held until
             * [releaseWakeLock]; holding one for long drains the battery.
             *
             * @param levelAndFlags A `PowerManager` wake lock level, such as `1` (`PARTIAL_WAKE_LOCK`, the default) to
             * keep the CPU running, optionally combined with its flags using `|`. Anything but a number is `1`.
             * @param tag The wake lock's name, shown in the system's battery statistics. Anything but text is
             * StarLight's default name.
             * @param timeout How long to hold it, in milliseconds; held until released when left out. Anything but a
             * number is no timeout.
             */
            @JvmStatic
            @JvmOverloads
            @SuppressLint("WakelockTimeout")
            @Synchronized
            fun acquireWakeLock(levelAndFlags: Any? = null, tag: Any? = null, timeout: Any? = null) {
                val level = ScriptArgs.int(levelAndFlags, "levelAndFlags", PowerManager.PARTIAL_WAKE_LOCK)
                val name = if (tag is CharSequence) tag.toString() else DEFAULT_TAG
                val millis = ScriptArgs.long(timeout, "timeout", -1L)
                wakeLock?.takeIf { it.isHeld }?.release()
                // Not reference-counted, so that releasing one whose timeout just ended does nothing instead of throwing.
                val lock = getPowerManager().newWakeLock(level, name).apply { setReferenceCounted(false) }
                wakeLock = lock
                if (millis > 0) lock.acquire(millis) else lock.acquire()
            }

            /**
             * Releases the wake lock that [acquireWakeLock] acquired.
             *
             * Nothing happens when no wake lock is held: none was acquired, it was released already, or its timeout
             * ended.
             *
             * @param flags `0` (the default), or `1` (`PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY`). Anything but
             * a number is `0`.
             */
            @JvmStatic
            @JvmOverloads
            @Synchronized
            fun releaseWakeLock(flags: Any? = null) {
                wakeLock?.takeIf { it.isHeld }?.release(ScriptArgs.int(flags, "flags", 0))
            }

            /**
             * The free memory, in bytes, of the heap StarLight's runtime has reserved so far.
             *
             * It is `Runtime.freeMemory()`: not the device's free memory, and the heap can still grow.
             */
            @JvmStatic
            fun getFreeMemory(): Long =
                Runtime.getRuntime().freeMemory()

            /**
             * The size, in bytes, of the heap StarLight's runtime has reserved so far.
             *
             * It is `Runtime.totalMemory()`: not the device's memory, and the heap can still grow.
             */
            @JvmStatic
            fun getTotalMemory(): Long =
                Runtime.getRuntime().totalMemory()

            /**
             * The unallocated space, in bytes, of the storage that holds [path].
             *
             * @param path An absolute path on the storage to measure.
             * @return `0` if [path] doesn't exist.
             * @throws IllegalArgumentException `"path must be a string"` when [path] is `null` or `undefined`.
             */
            @JvmStatic
            fun getFreeStorageSpace(path: Any?): Long =
                File(ScriptArgs.string(path, "path")).freeSpace

            /**
             * The size, in bytes, of the storage that holds [path].
             *
             * @param path An absolute path on the storage to measure.
             * @return `0` if [path] doesn't exist.
             * @throws IllegalArgumentException `"path must be a string"` when [path] is `null` or `undefined`.
             */
            @JvmStatic
            fun getTotalStorageSpace(path: Any?): Long =
                File(ScriptArgs.string(path, "path")).totalSpace

            /**
             * The name (SSID) of the Wi-Fi network the device is connected to, with the double quotes Android puts
             * around it.
             *
             * Android gives `"<unknown ssid>"` when the device isn't connected, and also when the app lacks the
             * location permission, which StarLight doesn't ask for.
             *
             * @return `null` if the device has no Wi-Fi information.
             */
            @JvmStatic
            @SuppressLint("MissingPermission")
            fun getWifiName(): String? {
                val manager = GlobalApplication.requireContext().applicationContext
                    .getSystemService(Context.WIFI_SERVICE) as WifiManager?
                @Suppress("DEPRECATION")
                return manager?.connectionInfo?.ssid
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
