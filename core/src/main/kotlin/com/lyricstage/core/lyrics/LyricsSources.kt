package com.lyricstage.core.lyrics

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Asks every lyrics source at once and keeps the best answer: word-timed lyrics win over
 * line-timed, then an instrumental flag, then plain text. As soon as a source returns
 * word-timed lyrics the others are cancelled, so the common case is as fast as the fastest
 * source.
 */
class LyricsSources(private val sources: List<Source>) {
    fun interface Source {
        suspend fun find(title: String, artist: String, album: String?, durationMs: Long?): LyricsLookup
    }

    suspend fun find(title: String, artist: String, album: String?, durationMs: Long?): LyricsLookup = coroutineScope {
        val jobs = sources.mapIndexed { i, s ->
            i to async { withTimeoutOrNull(TIMEOUT_MS) { runCatching { s.find(title, artist, album, durationMs) }.getOrNull() } ?: LyricsLookup.Unavailable }
        }.toMutableList()
        // keep the source's position so ties go to the earlier (more trusted) source
        val results = ArrayList<Pair<Int, LyricsLookup>>()
        while (jobs.isNotEmpty()) {
            val (entry, r) = select {
                jobs.forEach { e -> e.second.onAwait { e to it } }
            }
            jobs.remove(entry)
            results += entry.first to r
            if (r is LyricsLookup.Found && r.wordSynced) {
                jobs.forEach { it.second.cancel() }
                return@coroutineScope r
            }
        }
        best(results)
    }

    companion object {
        const val TIMEOUT_MS = 9_000L

        fun rank(r: LyricsLookup): Int = when {
            r !is LyricsLookup.Found -> 0
            r.wordSynced -> 4
            r.synced -> 3
            r.instrumental -> 2
            else -> 1
        }

        fun best(results: List<Pair<Int, LyricsLookup>>): LyricsLookup {
            val top = results.sortedBy { it.first }.maxByOrNull { rank(it.second) }?.second
            return when {
                top != null && rank(top) > 0 -> top
                results.isNotEmpty() && results.all { it.second == LyricsLookup.Unavailable } -> LyricsLookup.Unavailable
                else -> LyricsLookup.NotFound
            }
        }
    }
}
