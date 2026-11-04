package com.lyricstage.media

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.lyricstage.core.lyrics.TrackKey
import com.lyricstage.lyrics.Connectivity
import com.lyricstage.lyrics.LyricsRepository
import com.lyricstage.lyrics.TrackQuery
import com.lyricstage.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Runs inside the notification listener, which Android keeps alive while access is granted,
 * so lyrics are cached before the app is opened. Whenever a player changes track it fetches
 * that track's lyrics, then the next [QUEUE_AHEAD] songs in the player's queue when the player
 * shares one. Only the LRCLIB lookups the app would make anyway, one per new track.
 */
class LyricsPrefetcher(
    private val context: Context,
    private val repository: LyricsRepository,
    private val settings: SettingsStore,
    private val connectivity: Connectivity,
    private val scope: CoroutineScope,
) {
    private val manager: MediaSessionManager? = context.getSystemService(MediaSessionManager::class.java)
    private val component = ComponentName(context, LyricListenerService::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val callbacks = HashMap<MediaSession.Token, Pair<MediaController, MediaController.Callback>>()
    private val pending = HashMap<MediaSession.Token, Job>()
    private val seen = LinkedHashSet<String>()

    private val listener = MediaSessionManager.OnActiveSessionsChangedListener { onSessions(it.orEmpty()) }

    fun start() {
        val m = manager ?: return
        try {
            m.addOnActiveSessionsChangedListener(listener, component, handler)
            onSessions(m.getActiveSessions(component))
        } catch (e: SecurityException) {
            Log.w(TAG, "No notification access", e)
        }
    }

    fun stop() {
        manager?.removeOnActiveSessionsChangedListener(listener)
        callbacks.values.forEach { (c, cb) -> c.unregisterCallback(cb) }
        callbacks.clear()
        pending.values.forEach { it.cancel() }
        pending.clear()
    }

    private fun onSessions(list: List<MediaController>) {
        val live = list.map { it.sessionToken }.toSet()
        callbacks.keys.filter { it !in live }.forEach { k -> callbacks.remove(k)?.let { (c, cb) -> c.unregisterCallback(cb) } }
        for (c in list) {
            val k = c.sessionToken
            if (k in callbacks) continue
            val cb = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) = schedule(k, c)
                override fun onQueueChanged(queue: MutableList<MediaSession.QueueItem>?) = schedule(k, c)
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    if (state?.state == PlaybackState.STATE_PLAYING) schedule(k, c)
                }
            }
            c.registerCallback(cb, handler)
            callbacks[k] = c to cb
            schedule(k, c)
        }
    }

    /** Waits for the player's metadata to settle, then prefetches. */
    private fun schedule(k: MediaSession.Token, c: MediaController) {
        if (!settings.settings.value.backgroundPrefetch) return
        pending.remove(k)?.cancel()
        pending[k] = scope.launch {
            delay(SETTLE_MS)
            for (q in tracksToFetch(c)) {
                if (!seen.add(q.key)) continue
                if (seen.size > 200) seen.remove(seen.first())
                try {
                    repository.prefetch(q)
                } catch (e: Exception) {
                    Log.d(TAG, "Prefetch failed for ${q.key}: ${e.message}")
                    seen.remove(q.key)
                }
            }
        }
    }

    private fun tracksToFetch(c: MediaController): List<TrackQuery> {
        val out = ArrayList<TrackQuery>()
        c.metadata?.let { m ->
            val title = m.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
            val artist = (m.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)).orEmpty()
            val duration = m.getLong(MediaMetadata.METADATA_KEY_DURATION).takeIf { it > 0 }
            if (title.isNotBlank() && artist.isNotBlank()) {
                out += TrackQuery(TrackKey.of(artist, title, duration), title, artist, m.getString(MediaMetadata.METADATA_KEY_ALBUM), duration)
            }
        }
        // queue items carry title and artist but no duration; the cache matches them later
        if (settings.settings.value.lookAheadWifiOnly && connectivity.metered) return out
        val queue = c.queue.orEmpty()
        val active = c.playbackState?.activeQueueItemId ?: -1
        val at = queue.indexOfFirst { it.queueId == active }
        if (at >= 0) {
            for (item in queue.drop(at + 1).take(QUEUE_AHEAD)) {
                val title = item.description.title?.toString().orEmpty()
                val artist = item.description.subtitle?.toString().orEmpty()
                if (title.isNotBlank() && artist.isNotBlank()) out += TrackQuery(TrackKey.of(artist, title, null), title, artist, null, null)
            }
        }
        return out
    }

    private companion object {
        const val TAG = "LyricsPrefetcher"
        const val SETTLE_MS = 800L
        const val QUEUE_AHEAD = 2
    }
}
