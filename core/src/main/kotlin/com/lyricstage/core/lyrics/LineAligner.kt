package com.lyricstage.core.lyrics

import kotlin.math.abs

/**
 * Word-timed lyrics are sometimes timed against a different release of a song (a longer
 * intro, a radio edit), so every line arrives a second or two early or late. When a
 * line-timed copy matched to the playing release is also at hand, this measures how far the
 * word-timed lines sit from it and shifts them into place.
 */
object LineAligner {
    /** Lines must agree to within this spread for a shift to be trusted. */
    private const val MAX_SPREAD_MS = 350L
    /** Smaller differences are within the usual hand-sync noise; leave them. */
    private const val MIN_SHIFT_MS = 250L
    private const val MAX_SHIFT_MS = 8_000L
    private const val MIN_MATCHES = 4

    /**
     * Milliseconds to add to [wordTimed]'s times so its lines start with [reference]'s, or
     * null when they already agree or the two cannot be matched with confidence.
     */
    fun offset(wordTimed: String, reference: String): Long? {
        val ref = LrcParser.parse(reference).filter { key(it.text).isNotEmpty() }
        val words = LrcParser.parse(wordTimed).filter { key(it.text).isNotEmpty() }
        if (ref.size < MIN_MATCHES || words.isEmpty()) return null
        val diffs = ArrayList<Long>()
        for (r in ref) {
            val k = key(r.text)
            val near = words.filter { key(it.text) == k }
                .minByOrNull { abs(it.startMs - r.startMs) } ?: continue
            val d = r.startMs - near.startMs
            if (abs(d) <= MAX_SHIFT_MS) diffs += d
        }
        if (diffs.size < MIN_MATCHES || diffs.size * 3 < ref.size) return null
        diffs.sort()
        val median = diffs[diffs.size / 2]
        val spread = diffs.map { abs(it - median) }.sorted()[diffs.size / 2]
        return if (spread <= MAX_SPREAD_MS && abs(median) >= MIN_SHIFT_MS) median else null
    }

    /** [raw] with every time moved later by [addMs] (earlier when negative). */
    fun shift(raw: String, addMs: Long): String {
        // LRC's own offset tag: a positive value moves lyrics earlier
        val existing = Regex("""^\s*\[offset:\s*([+-]?\d+)\s*]""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
        val base = existing.find(raw)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val body = raw.replace(existing, "").trimStart('\n')
        return "[offset:${base - addMs}]\n$body"
    }

    /** Line identity for matching: letters and digits only, first 32 of them. */
    private fun key(text: String): String =
        text.lowercase().filter { it.isLetterOrDigit() }.take(32)
}
