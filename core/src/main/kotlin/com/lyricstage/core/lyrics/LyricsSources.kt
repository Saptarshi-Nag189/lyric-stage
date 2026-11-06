package com.lyricstage.core.lyrics

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The lookup order for one song, fixed. Musixmatch is asked first; then NetEase; Kugou is
 * asked only when both of them came back with nothing, whether a miss or an error, so a
 * less trusted source can never win the race by being faster. LRCLIB is the last resort
 * for the lyrics themselves, but it starts at the same moment as Musixmatch, because its
 * line timing has a second job: word timings are sometimes made against a different
 * release of the song, and they are moved onto LRCLIB's lines - one shift when the lines
 * agree, line by line when they do not ([LineAligner]).
 *
 * The first source in [sources] is the LRCLIB one; the rest are the word-timing chain in
 * trust order.
 */
class LyricsSources(private val sources: List<Source>) {
    fun interface Source {
        suspend fun find(title: String, artist: String, album: String?, durationMs: Long?): LyricsLookup
    }

    suspend fun find(title: String, artist: String, album: String?, durationMs: Long?): LyricsLookup = coroutineScope {
        // LRCLIB starts at once; it is the timing reference and the last resort
        val reference = async { ask(sources.first(), title, artist, album, durationMs) }
        val seen = ArrayList<Pair<Int, LyricsLookup>>()
        for (i in 1 until sources.size) {
            val r = ask(sources[i], title, artist, album, durationMs)
            seen += i to r
            if (r is LyricsLookup.Found && r.wordSynced) {
                // correct its timing against LRCLIB's line copy, but never hold the answer
                // for it: the reference gets a short moment, then the answer goes out as it is
                val ref = withTimeoutOrNull(ALIGN_WAIT_MS) { reference.await() as? LyricsLookup.Found }
                if (ref == null && reference.isActive) reference.cancel()
                return@coroutineScope aligned(r, ref)
            }
        }
        // nothing word-timed anywhere: LRCLIB's own answer is the last resort
        seen += 0 to reference.await()
        best(seen)
    }

    private suspend fun ask(s: Source, title: String, artist: String, album: String?, durationMs: Long?): LyricsLookup =
        withTimeoutOrNull(TIMEOUT_MS) { runCatching { s.find(title, artist, album, durationMs) }.getOrNull() }
            ?: LyricsLookup.Unavailable

    private fun aligned(word: LyricsLookup.Found, ref: LyricsLookup.Found?): LyricsLookup.Found {
        if (ref == null) return word
        val raw = LineAligner.align(word.raw, ref.raw)
        return if (raw == word.raw) word else word.copy(raw = raw)
    }

    companion object {
        /** LRCLIB can take a while: an exact lookup, then a search when that misses. */
        const val TIMEOUT_MS = 15_000L
        /** How long a word-timed answer waits for LRCLIB's line copy to align against. */
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
