package com.lyricstage.core.lyrics

import kotlin.math.abs

/**
 * Word-timed lyrics are sometimes timed against a different release of a song (a longer
 * intro, a radio edit), so lines arrive a second or two early or late, by the same amount
 * or by an amount that wanders. When a line-timed copy matched to the playing release is
 * at hand, this measures where each word-timed line sits against it and moves it there:
 * by one shift when all lines agree, otherwise line by line, keeping the word spacing
 * inside each line.
 */
object LineAligner {
    /** Lines within this of the common shift count as agreeing with it. */
    private const val MAX_SPREAD_MS = 350L
    /** Smaller differences are within the usual hand-sync noise; leave them. */
    private const val MIN_SHIFT_MS = 250L
    private const val MAX_SHIFT_MS = 8_000L
    private const val MIN_MATCHES = 4

    private val TIME_TAG = Regex("""\[(\d{1,3}):(\d{1,2}(?:[.:]\d{1,3})?)]""")
    private val WORD_TAG = Regex("""<(\d{1,3}):(\d{1,2}(?:[.:]\d{1,3})?)>""")
    private val OFFSET_TAG = Regex("""^\s*\[offset:\s*([+-]?\d+)\s*]""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))

    /**
     * Milliseconds to add to [wordTimed]'s times so its lines start with [reference]'s, or
     * null when they already agree or do not agree on one shift.
     */
    fun offset(wordTimed: String, reference: String): Long? {
        val diffs = diffs(wordTimed, reference)?.map { it.second } ?: return null
        val median = diffs.sorted()[diffs.size / 2]
        return if (agree(diffs, median) && abs(median) >= MIN_SHIFT_MS) median else null
    }

    /** Most lines (all but a fifth, for stray matches) sit within [MAX_SPREAD_MS] of [median]. */
    private fun agree(diffs: List<Long>, median: Long): Boolean =
        diffs.count { abs(it - median) > MAX_SPREAD_MS } * 5 <= diffs.size

    /**
     * [wordTimed] moved onto [reference]: one shift when the lines agree, line by line when
     * they do not, unchanged when the two cannot be matched with confidence.
     */
    fun align(wordTimed: String, reference: String): String {
        offset(wordTimed, reference)?.let { return shift(wordTimed, it) }
        val matched = diffs(wordTimed, reference) ?: return wordTimed
        val median = matched.map { it.second }.sorted()[matched.size / 2]
        if (agree(matched.map { it.second }, median)) return wordTimed // agree, and too close to bother
        // per line: matched lines take their own difference; the rest the nearest matched one's
        val byStart = matched.toMap()
        val starts = matched.map { it.first }.sorted()
        val base = OFFSET_TAG.find(wordTimed)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        return wordTimed.replace(OFFSET_TAG, "").lineSequence().joinToString("\n") { row ->
            val t = TIME_TAG.find(row) ?: return@joinToString row
            val start = LrcParser.toMs(t.groupValues[1], t.groupValues[2]) - base
            val d = byStart[start] ?: byStart.getValue(starts.minBy { abs(it - start) })
            retime(row, d - base)
        }.trimStart('\n')
    }

    /** [raw] with every time moved later by [addMs] (earlier when negative). */
    fun shift(raw: String, addMs: Long): String {
        // LRC's own offset tag: a positive value moves lyrics earlier
        val base = OFFSET_TAG.find(raw)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val body = raw.replace(OFFSET_TAG, "").trimStart('\n')
        return "[offset:${base - addMs}]\n$body"
    }

    /** (word-timed line start, reference start minus it) for each line both share. */
    private fun diffs(wordTimed: String, reference: String): List<Pair<Long, Long>>? {
        val ref = LrcParser.parse(reference).filter { key(it.text).length >= 3 }
        val words = LrcParser.parse(wordTimed).filter { key(it.text).length >= 3 }
        if (ref.size < MIN_MATCHES || words.isEmpty()) return null
        val out = ArrayList<Pair<Long, Long>>()
        for (r in ref) {
            val k = key(r.text)
            val near = words.filter { same(key(it.text), k) && abs(r.startMs - it.startMs) <= MAX_SHIFT_MS }
                .minByOrNull { abs(it.startMs - r.startMs) } ?: continue
            out += near.startMs to (r.startMs - near.startMs)
        }
        // one reference line can match a repeated word-timed line once only
        val unique = out.distinctBy { it.first }
        return if (unique.size >= MIN_MATCHES && unique.size * 3 >= ref.size) unique else null
    }

    /** Same line: equal, or one is the start of the other (a line split differently). */
    private fun same(a: String, b: String): Boolean =
        a == b || (minOf(a.length, b.length) >= 12 && (a.startsWith(b) || b.startsWith(a)))

    /** Line identity for matching: letters and digits only, first 32 of them. */
    private fun key(text: String): String =
        text.lowercase().filter { it.isLetterOrDigit() }.take(32)

    private fun retime(row: String, addMs: Long): String {
        fun move(m: MatchResult, open: String, close: String): String {
            val ms = (LrcParser.toMs(m.groupValues[1], m.groupValues[2]) + addMs).coerceAtLeast(0)
            return open + EnhancedLrc.stamp(ms) + close
        }
        return row.replace(TIME_TAG) { move(it, "[", "]") }.replace(WORD_TAG) { move(it, "<", ">") }
    }
}
