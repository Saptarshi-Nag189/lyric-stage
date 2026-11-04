package com.lyricstage.lyrics

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether the phone is online, and whether that connection is metered (mobile data). */
class Connectivity(context: Context) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val _online = MutableStateFlow(check())
    val online: StateFlow<Boolean> = _online.asStateFlow()

    init {
        cm?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { _online.value = check() }
            override fun onLost(network: Network) { _online.value = check() }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { _online.value = check() }
        })
    }

    val metered: Boolean get() = cm?.isActiveNetworkMetered ?: true

    private fun check(): Boolean {
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
