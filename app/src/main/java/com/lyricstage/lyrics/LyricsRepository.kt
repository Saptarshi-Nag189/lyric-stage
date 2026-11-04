package com.lyricstage.lyrics

import com.lyricstage.core.lyrics.LrcParser
import com.lyricstage.core.lyrics.LyricsSources
import com.lyricstage.core.lyrics.Lyrics
import com.lyricstage.core.lyrics.LyricsBuilder
import com.lyricstage.core.lyrics.LyricsLookup
import com.lyricstage.lyrics.db.LyricsDao
import com.lyricstage.lyrics.db.LyricsEntity
import com.lyricstage.lyrics.db.MissDao
import com.lyricstage.lyrics.db.MissEntity
import com.lyricstage.lyrics.db.OffsetDao
import com.lyricstage.lyrics.db.OffsetEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/** What the stage knows about the current track's lyrics. */
sealed interface LyricsState {
    data object Idle : LyricsState
    data object Loading : LyricsState
    data class Ready(val lyrics: Lyrics, val source: String) : LyricsState
    data object NotFound : LyricsState
    data object Unavailable : LyricsState

    /** No connection and nothing cached for this song yet. */
    data object Offline : LyricsState
}

/** The fields of a track that lyric lookups need. */
data class TrackQuery(val key: String, val title: String, val artist: String, val album: String?, val durationMs: Long?)

class LyricsRepository(
    private val dao: LyricsDao,
    private val offsets: OffsetDao,
    private val misses: MissDao,
    private val client: LyricsSources,
    private val online: StateFlow<Boolean>,
    private val scope: CoroutineScope,
    /** A fresh check of the connection; the flow alone can be stale. */
    private val checkOnline: () -> Boolean = { online.value },
) {
    /** Keys already checked for a word-timed upgrade in this run. */
    private val upgradeTried = ConcurrentHashMap.newKeySet<String>()

    /** Lookups in flight, so the stage and the background prefetcher never fetch twice. */
    private val inFlight = ConcurrentHashMap<String, Deferred<LyricsLookup>>()

    /**
     * Lyrics for [track]. A cached row (also one stored under a duration a few seconds off)
     * shows at once. Otherwise LRCLIB is asked straight away, and missing lyrics are retried
     * every [RETRY_MS] while [isPlaying]. A manual import updates the flow immediately.
     */
    fun observe(track: TrackQuery, isPlaying: StateFlow<Boolean>): Flow<LyricsState> = channelFlow {
        send(LyricsState.Loading)
        val key = cachedRow(track)?.trackKey ?: track.key
        if (key == track.key && dao.get(key) == null) {
            launch {
                // players often send title first and duration a moment later; let it settle
                delay(SETTLE_MS)
                // known miss: say so at once and only retry on the slow schedule
                var attempt = if (recentMiss(track.key)) 1.also { send(LyricsState.NotFound) } else 0
                while (dao.get(track.key) == null) {
                    if (attempt++ > 0) {
                        delay(RETRY_MS)
                        isPlaying.first { it }
                    }
                    // always try: only a lookup that fails with no connection means offline
                    when (fetch(track)) {
                        is LyricsLookup.Found -> break
                        LyricsLookup.NotFound -> send(LyricsState.NotFound)
                        LyricsLookup.Unavailable -> if (checkOnline()) {
                            send(LyricsState.Unavailable)
                        } else {
                            // offline: wait for the connection, then try again at once
                            send(LyricsState.Offline)
                            while (!checkOnline()) withTimeoutOrNull(OFFLINE_RECHECK_MS) { online.first { it } }
                            attempt = 0
                        }
                    }
                }
            }
        }
        // cached with line timing only: look once (per app run) for word timing to upgrade to
        val cached = dao.get(key)
        // word timing saved before 1.0.5 may sit a second or two off (weaker alignment then): refresh once
        val stale = cached != null && rankOf(cached) == WORD_RANK && cached.updatedAt < ALIGNED_SINCE
        if (cached != null && (rankOf(cached) < WORD_RANK || stale) && cached.source != SOURCE_IMPORT && checkOnline() && upgradeTried.add(key)) {
            launch { runCatching { fetch(track.copy(key = key), replaceStale = stale) } }
        }
        dao.observe(key).collect { e ->
            if (e != null) send(LyricsState.Ready(toLyrics(e, track.durationMs), e.source))
        }
    }.flowOn(Dispatchers.Default)

    /**
     * Fetches and caches lyrics for a track that may play soon (background and queue
     * prefetch). Does nothing when they are cached already.
     */
    suspend fun prefetch(track: TrackQuery) {
        if (!checkOnline() || cachedRow(track) != null || recentMiss(track.key)) return
        fetch(track)
    }

    /** True when no source had lyrics for [key] within [MISS_TTL_MS]. */
    private suspend fun recentMiss(key: String): Boolean =
        misses.at(key)?.let { System.currentTimeMillis() - it < MISS_TTL_MS } == true

    /** One shared LRCLIB lookup per track key; stores a hit in the cache. */
    private suspend fun fetch(track: TrackQuery, replaceStale: Boolean = false): LyricsLookup {
        val job = inFlight.computeIfAbsent(track.key) {
            // lazy, so it cannot finish (and unregister) before computeIfAbsent stores it
            scope.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
                try {
                    val r = client.find(track.title, track.artist, track.album, track.durationMs)
                    val existing = dao.get(track.key)
                    // never replace an import, and only replace a row with something better
                    if (r is LyricsLookup.Found && (existing == null || (existing.source != SOURCE_IMPORT && (LyricsSources.rank(r) > rankOf(existing) || (replaceStale && LyricsSources.rank(r) == rankOf(existing)))))) {
                        dao.upsert(entity(track, r.raw, r.synced, r.instrumental, r.source))
                    }
                    when (r) {
                        LyricsLookup.NotFound -> misses.upsert(MissEntity(track.key, System.currentTimeMillis()))
                        is LyricsLookup.Found -> misses.delete(track.key)
                        LyricsLookup.Unavailable -> Unit
                    }
                    r
                } finally {
                    inFlight.remove(track.key)
                }
            }
        }
        return job.await()
    }

    /**
     * The cached row for [track]: its exact key, or the same artist and title stored with a
     * duration within [DURATION_SLACK_S] seconds, or with no duration at all (queue prefetch).
     */
    private suspend fun cachedRow(track: TrackQuery): LyricsEntity? {
        dao.get(track.key)?.let { return it }
        val prefix = track.key.substringBeforeLast('|') + "|"
        val wanted = track.key.substringAfterLast('|').toLongOrNull() ?: 0
        return dao.withPrefix(prefix)
            .map { it to (it.trackKey.substringAfterLast('|').toLongOrNull() ?: 0) }
            .filter { (_, d) -> d == 0L || wanted == 0L || abs(d - wanted) <= DURATION_SLACK_S }
            .minByOrNull { (_, d) -> if (d == 0L || wanted == 0L) DURATION_SLACK_S + 1 else abs(d - wanted) }
            ?.first
    }

    fun cachedCount(): Flow<Int> = dao.count()

    /** Bytes of downloaded lyrics. */
    fun cachedBytes(): Flow<Long> = dao.size()

    /** Deletes downloaded lyrics and the "no lyrics" memory; imports stay. */
    suspend fun clearFetched() {
        dao.clearFetched()
        misses.clear()
    }

    /** Bind a user-picked .lrc (or plain text) to [track]. Returns false if it is empty. */
    suspend fun import(track: TrackQuery, raw: String): Boolean {
        val text = raw.removePrefix(BOM).trim()
        if (text.isEmpty()) return false
        dao.upsert(entity(track, text, LrcParser.isSynced(text), instrumental = false, source = SOURCE_IMPORT))
        return true
    }

    fun offset(key: String): Flow<Long> = offsets.observe(key).map { it ?: 0L }

    suspend fun setOffset(key: String, offsetMs: Long) = offsets.upsert(OffsetEntity(key, offsetMs))

    suspend fun cached(): List<LyricsEntity> = dao.all()

    private fun entity(t: TrackQuery, raw: String, synced: Boolean, instrumental: Boolean, source: String) =
        LyricsEntity(t.key, raw, synced, instrumental, source, t.title, t.artist, t.durationMs, System.currentTimeMillis())

    companion object {
        const val RETRY_MS = 60_000L
        const val SETTLE_MS = 250L
        const val MISS_TTL_MS = 24 * 60 * 60_000L
        const val DURATION_SLACK_S = 3L
        private val BOM = Char(0xFEFF).toString()
        const val SOURCE_LRCLIB = "lrclib"
        private const val WORD_RANK = 4
        private const val OFFLINE_RECHECK_MS = 10_000L
        /** Word timing cached before this time (1.0.5) was aligned less well or not at all; it is fetched again once. */
        private const val ALIGNED_SINCE = 1_793_808_000_000L

        /** Same scale as [LyricsSources.rank]. */
        fun rankOf(e: LyricsEntity): Int = when {
            e.instrumental -> 2
            e.synced && LrcParser.hasWordTags(e.raw) -> WORD_RANK
            e.synced || LrcParser.isSynced(e.raw) -> 3
            else -> 1
        }
        const val SOURCE_IMPORT = "import"

        fun toLyrics(e: LyricsEntity, durationMs: Long?): Lyrics = when {
            e.instrumental -> Lyrics.Instrumental
            e.synced || LrcParser.isSynced(e.raw) -> LyricsBuilder.fromLrc(e.raw, durationMs ?: e.durationMs)
            else -> LyricsBuilder.plain(e.raw)
        }
    }
}
