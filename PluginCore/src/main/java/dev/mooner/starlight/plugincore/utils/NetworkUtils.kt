package dev.mooner.starlight.plugincore.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dev.mooner.starlight.plugincore.logger.internal.Logger

object NetworkUtil {
    const val NETWORK_STATUS_NOT_CONNECTED = 0
    const val NETWORK_STATUS_WIFI = 1
    const val NETWORK_STATUS_MOBILE = 2
    const val NETWORK_STATUS_ETHERNET = 3

    private val listeners: ArrayList<(status: Int) -> Unit> = arrayListOf()

    private var connectivityManager: ConnectivityManager? = null
    private var callback: ConnectivityManager.NetworkCallback? = null

    fun registerNetworkStatusListener(context: Context) {
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                dispatch(getConnectivityStatus(connectivityManager!!))
            }

            override fun onLost(network: Network) {
                dispatch(NETWORK_STATUS_NOT_CONNECTED)
            }

            // On the system's connectivity thread: a listener that throws would end the process, and the ones
            // after it wouldn't hear of the change.
            private fun dispatch(status: Int) {
                for (listener in listeners.toList())
                    try {
                        listener(status)
                    } catch (e: Exception) {
                        Logger.e("NetworkUtil", "A network state listener failed: $e")
                    } catch (e: LinkageError) {
                        Logger.e("NetworkUtil", "A network state listener failed: $e")
                    }
            }
        }
        connectivityManager!!.registerDefaultNetworkCallback(callback!!)
    }

    fun addOnNetworkStateChangedListener(listener: (status: Int) -> Unit) {
        listeners.add(listener)
    }

    fun getNetworkStatus(context: Context): Int {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return getConnectivityStatus(connectivityManager)
    }

    private fun getConnectivityStatus(connectivityManager: ConnectivityManager): Int {
        val networkCapabilities = connectivityManager.activeNetwork ?: return NETWORK_STATUS_NOT_CONNECTED
        val actNw = connectivityManager.getNetworkCapabilities(networkCapabilities) ?: return NETWORK_STATUS_NOT_CONNECTED
        return when {
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NETWORK_STATUS_WIFI
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NETWORK_STATUS_MOBILE
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NETWORK_STATUS_ETHERNET
            else -> NETWORK_STATUS_NOT_CONNECTED
        }
    }

    internal fun purge() {
        if (connectivityManager != null && callback != null)
            connectivityManager!!.unregisterNetworkCallback(callback!!)
        listeners.clear()
    }
}