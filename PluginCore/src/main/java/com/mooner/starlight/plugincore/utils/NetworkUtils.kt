package com.mooner.starlight.plugincore.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

object NetworkUtil {
    const val NETWORK_STATUS_NOT_CONNECTED = 0
    const val NETWORK_STATUS_WIFI = 1
    const val NETWORK_STATUS_MOBILE = 2
    const val NETWORK_STATUS_ETHERNET = 3

    private val listeners: ArrayList<(status: Int) -> Unit> = arrayListOf()

    fun registerNetworkStatusListener(context: Context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        val status = getConnectivityStatus(connectivityManager)
                        for (listener in listeners) {
                            listener(status)
                        }
                    }

                    override fun onLost(network: Network) {
                        for (listener in listeners) {
                            listener(NETWORK_STATUS_NOT_CONNECTED)
                        }
                    }
                }
        )
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
}