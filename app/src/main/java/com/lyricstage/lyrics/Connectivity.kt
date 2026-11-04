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
        // never let connectivity checks take the app down; assume online if they fail
        runCatching {
            cm?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                // use what each callback reports: activeNetwork can lag behind it, or read
                // null while the app is in the background, which left the app stuck "offline"
                override fun onAvailable(network: Network) { _online.value = true }
                override fun onLost(network: Network) { _online.value = check() }
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    _online.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                }
                override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
                    _online.value = !blocked
                }
            })
        }
    }

    val metered: Boolean get() = runCatching { cm?.isActiveNetworkMetered }.getOrNull() ?: true

    /** Asks the system again now (rather than trusting the last callback) and updates [online]. */
    fun refresh(): Boolean = check().also { _online.value = it }

    private fun check(): Boolean = runCatching {
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork) ?: return@runCatching false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(true)
}
