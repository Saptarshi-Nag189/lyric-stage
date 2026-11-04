package com.lyricstage.spotify

import com.lyricstage.core.lyrics.TrackKey
import com.lyricstage.lyrics.Connectivity
import com.lyricstage.lyrics.LyricsRepository
import com.lyricstage.lyrics.TrackQuery
import com.lyricstage.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.coroutines.coroutineContext

/** Where the library sync is. */
sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Reading : SyncStatus
    data class Saving(val done: Int, val total: Int) : SyncStatus
    data class Done(val total: Int, val at: Long) : SyncStatus
    /** Stopped before the end: no Wi-Fi, battery saver, or Spotify could not be reached. */
    data class Paused(val done: Int, val total: Int) : SyncStatus
}

/**
 * Saves lyrics ahead of time for the songs in the linked account's Liked Songs and saved
 * playlists. Runs at most once a day by itself (from the notification listener, which
 * Android keeps alive), and on demand from Settings. Gentle on the network: one lookup at
 * a time with a pause between them, only songs not already saved, and Wi-Fi only when
 * "Look ahead on Wi-Fi only" is on. Stops when battery saver is turned on.
 */
class LibrarySync(
    private val account: SpotifyAccount,
    private val repository: LyricsRepository,
    private val settings: SettingsStore,
    private val connectivity: Connectivity,
    private val store: SecureStore,
    private val scope: CoroutineScope,
) {
    private val _status = MutableStateFlow<SyncStatus>(
        store.getLong(K_LAST).takeIf { it > 0 }?.let { SyncStatus.Done(store.getLong(K_TOTAL).toInt(), it) } ?: SyncStatus.Idle,
    )
    val status: StateFlow<SyncStatus> = _status.asStateFlow()
    private var job: Job? = null

    val running: Boolean get() = job?.isActive == true

    /** Called as songs change in the background: syncs when a day has passed. */
    fun maybeAutoSync() {
        if (running || !account.linked || !allowed()) return
        if (System.currentTimeMillis() - store.getLong(K_LAST) < DAY_MS) return
        start()
    }

    fun syncNow() {
        if (running || !account.linked) return
        start()
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /** Clears the record of past syncs, as when unlinking. */
    fun reset() {
        stop()
        _status.value = SyncStatus.Idle
    }

    private fun allowed(): Boolean {
        val s = settings.settings.value
        if (s.batterySaver || !connectivity.refresh()) return false
        return !(s.lookAheadWifiOnly && connectivity.metered)
    }

    private fun start() {
        job = scope.launch {
            _status.value = SyncStatus.Reading
            val tracks = runCatching { readLibrary() }.getOrElse {
                _status.value = SyncStatus.Paused(0, 0)
                return@launch
            }
            var done = 0
            for (t in tracks) {
                if (!coroutineContext.isActive) return@launch
                if (!account.linked || !allowed()) {
                    _status.value = SyncStatus.Paused(done, tracks.size)
                    return@launch
                }
                _status.value = SyncStatus.Saving(done, tracks.size)
                val looked = runCatching { repository.prefetch(t) }.getOrDefault(false)
                done++
                // pace real lookups (four lyrics services each); saved songs pass straight on
                if (looked) delay(PACE_MS)
            }
            val now = System.currentTimeMillis()
            store.putLong(K_LAST, now)
            store.putLong(K_TOTAL, tracks.size.toLong())
            _status.value = SyncStatus.Done(tracks.size, now)
        }
    }

    /** Liked Songs first, then each saved playlist; duplicates and local files dropped. */
    private suspend fun readLibrary(): List<TrackQuery> {
        val out = LinkedHashMap<String, TrackQuery>()
        fun add(track: JSONObject?) {
            if (track == null || track.optBoolean("is_local") || track.optString("type", "track") != "track") return
            val title = track.optString("name").takeIf { it.isNotBlank() } ?: return
            val artists = track.optJSONArray("artists")
            val artist = (0 until (artists?.length() ?: 0)).joinToString(", ") { artists!!.getJSONObject(it).optString("name") }
            val duration = track.optLong("duration_ms").takeIf { it > 0 }
            val album = track.optJSONObject("album")?.optString("name")?.takeIf { it.isNotBlank() }
            val key = TrackKey.of(artist, title, duration)
            out.putIfAbsent(key, TrackQuery(key, title, artist, album, duration))
        }
        var next: String? = "${SpotifyAccount.API}/me/tracks?limit=50"
        while (next != null) {
            val page = getPaced(next)
            val items = page.optJSONArray("items")
            for (i in 0 until (items?.length() ?: 0)) add(items!!.getJSONObject(i).optJSONObject("track"))
            next = page.optString("next").takeIf { it.isNotBlank() && it != "null" }
        }
        var lists: String? = "${SpotifyAccount.API}/me/playlists?limit=50"
        val playlistTracks = ArrayList<String>()
        while (lists != null) {
            val page = getPaced(lists)
            val items = page.optJSONArray("items")
            for (i in 0 until (items?.length() ?: 0)) {
                val p = items!!.optJSONObject(i) ?: continue
                p.optJSONObject("tracks")?.optString("href")?.takeIf { it.isNotBlank() }?.let { playlistTracks += it }
            }
            lists = page.optString("next").takeIf { it.isNotBlank() && it != "null" }
        }
        for (href in playlistTracks) {
            var url: String? = "$href?limit=100&fields=items(track(name,duration_ms,is_local,type,artists(name),album(name))),next"
            while (url != null) {
                val page = runCatching { getPaced(url) }.getOrNull() ?: break // a playlist we may not read: skip it
                val items = page.optJSONArray("items")
                for (i in 0 until (items?.length() ?: 0)) add(items!!.getJSONObject(i).optJSONObject("track"))
                url = page.optString("next").takeIf { it.isNotBlank() && it != "null" }
            }
        }
        return out.values.toList()
    }

    /** One Web API page, waiting out a rate limit when Spotify asks. */
    private suspend fun getPaced(url: String): JSONObject {
        repeat(3) {
            try {
                return account.get(url)
            } catch (e: SpotifyAccount.RateLimited) {
                delay(e.retryAfterSeconds.coerceIn(1, 120) * 1000)
            }
        }
        return account.get(url)
    }

    private companion object {
        const val K_LAST = "sync_last"
        const val K_TOTAL = "sync_total"
        const val DAY_MS = 24 * 60 * 60 * 1000L
        const val PACE_MS = 1_500L
    }
}
