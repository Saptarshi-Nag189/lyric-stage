package com.lyricstage.media

import android.service.notification.NotificationListenerService
import com.lyricstage.LyricStageApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Granting this listener notification access is what lets the app call
 * MediaSessionManager.getActiveSessions(). It ignores every notification. While Android keeps
 * it connected it also prefetches lyrics for the songs being played (see [LyricsPrefetcher]).
 */
class LyricListenerService : NotificationListenerService() {
    private var scope: CoroutineScope? = null
    private var prefetcher: LyricsPrefetcher? = null

    override fun onListenerConnected() {
        super.onListenerConnected()
        val container = (application as LyricStageApp).container
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = s
        prefetcher = LyricsPrefetcher(this, container.lyrics, container.settings, s).also { it.start() }
    }

    override fun onListenerDisconnected() {
        shutdown()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun shutdown() {
        prefetcher?.stop()
        prefetcher = null
        scope?.cancel()
        scope = null
    }
}
