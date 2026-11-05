package com.lyricstage.core.lyrics

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Asks every lyrics source at once and keeps the best answer: word-timed lyrics win over
 * line-timed, then an instrumental flag, then plain text. Sources are listed most trusted
 * first. As soon as a source returns word-timed lyrics, more trusted sources get a short
 * moment to answer too, and the others to supply a line-timed copy, which [LineAligner]
 * uses to correct word timings made for a different release; then they are cancelled.
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
                // a more trusted source may still bring word timings, and a line-timed copy
                // checks the timing: wait a moment for either, then stop the rest
                withTimeoutOrNull(ALIGN_WAIT_MS) {
                    while (jobs.isNotEmpty() && (jobs.any { it.first < bestWord(results) } || reference(results) == null)) {
                        val (e, more) = select { jobs.forEach { j -> j.second.onAwait { j to it } } }
                        jobs.remove(e)
                        results += e.first to more
                    }
                }
                jobs.forEach { it.second.cancel() }
                val word = results.first { it.first == bestWord(results) }.second as LyricsLookup.Found
                return@coroutineScope aligned(word, reference(results))
            }
        }
        best(results)
    }

    /** Position of the most trusted word-timed answer so far. */
    private fun bestWord(results: List<Pair<Int, LyricsLookup>>): Int =
        results.filter { (it.second as? LyricsLookup.Found)?.wordSynced == true }.minOf { it.first }

    private fun reference(results: List<Pair<Int, LyricsLookup>>): LyricsLookup.Found? =
        results.sortedBy { it.first }.map { it.second }
            .firstOrNull { it is LyricsLookup.Found && it.synced && !it.wordSynced } as LyricsLookup.Found?

    private fun aligned(word: LyricsLookup.Found, ref: LyricsLookup.Found?): LyricsLookup.Found {
        if (ref == null) return word
        val raw = LineAligner.align(word.raw, ref.raw)
        return if (raw == word.raw) word else word.copy(raw = raw)
    }

    companion object {
        /** LRCLIB can take a while: an exact lookup, then a search when that misses. */
        const val TIMEOUT_MS = 15_000L
        /** How long a word-timed answer waits for a line-timed one to align against. */
        const val ALIGN_WAIT_MS = 4_000L

        fun rank(r: LyricsLookup): Int = when {
            r !is LyricsLookup.Found -> 0
            r.wordSynced -> 4
            r.synced -> 3
            r.instrumental -> 2
            else -> 1
        }

        /**
         * The best answer. "Not found" needs the first source (LRCLIB, the main catalogue) to
         * have said so: when it timed out or failed, the answer is "unavailable", so the miss
         * is not remembered and the song is asked again.
         */
        fun best(results: List<Pair<Int, LyricsLookup>>): LyricsLookup {
            val top = results.sortedBy { it.first }.maxByOrNull { rank(it.second) }?.second
            val primary = results.firstOrNull { it.first == 0 }?.second
            return when {
                top != null && rank(top) > 0 -> top
                results.isEmpty() || primary == null || primary == LyricsLookup.Unavailable -> LyricsLookup.Unavailable
                else -> LyricsLookup.NotFound
            }
        }
    }
}
