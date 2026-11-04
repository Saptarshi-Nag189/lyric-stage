package com.lyricstage.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether sound currently goes to Bluetooth (earphones, speaker or hearing aid). */
class AudioOutput(context: Context) {
    private val am = context.getSystemService(AudioManager::class.java)
    private val _bluetooth = MutableStateFlow(check())
    val bluetooth: StateFlow<Boolean> = _bluetooth.asStateFlow()

    init {
        runCatching {
            am?.registerAudioDeviceCallback(object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) { _bluetooth.value = check() }
                override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) { _bluetooth.value = check() }
            }, Handler(Looper.getMainLooper()))
        }
    }

    private fun check(): Boolean = runCatching {
        am?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.any { it.type in BLUETOOTH } == true
    }.getOrDefault(false)

    private companion object {
        // A2DP, hearing aid, and the LE Audio headset, speaker and broadcast types (API 31+)
        val BLUETOOTH = setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, 23, 26, 27, 30)
    }
}
