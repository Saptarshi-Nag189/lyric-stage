package com.lyricstage.core.lyrics

/** A line with per-word times, from a source that times words (NetEase, Musixmatch). */
data class TimedLine(val startMs: Long, val words: List<TimedWord>, val joinWithSpaces: Boolean = true)

data class TimedWord(val text: String, val startMs: Long, val endMs: Long)

/** Writes word-timed lines as enhanced LRC, so every source is stored and parsed the same way. */
object EnhancedLrc {
    fun write(lines: List<TimedLine>, extraRows: List<Pair<Long, String>> = emptyList()): String {
        val rows = ArrayList<Pair<Long, String>>()
        for (line in lines) {
            val words = line.words.filter { it.text.isNotBlank() }
            if (words.isEmpty()) continue
            val sb = StringBuilder()
            sb.append('[').append(stamp(line.startMs)).append(']')
            for ((i, w) in words.withIndex()) {
                sb.append('<').append(stamp(w.startMs)).append('>').append(w.text.trim())
                val next = words.getOrNull(i + 1)
                // mark the end when the singer pauses before the next word, and after the last
                if (next == null || next.startMs - w.endMs > GAP_MS) sb.append('<').append(stamp(w.endMs)).append('>')
                if (next != null && line.joinWithSpaces) sb.append(' ')
            }
            rows += line.startMs to sb.toString()
        }
        for ((t, text) in extraRows) rows += t to "[${stamp(t)}]$text"
        return rows.sortedBy { it.first }.joinToString("\n") { it.second }
    }

    /** `mm:ss.xx`, as LRC expects. */
    fun stamp(ms: Long): String {
        val t = ms.coerceAtLeast(0)
        val m = t / 60_000
        val s = (t % 60_000) / 1000
        val cs = (t % 1000) / 10
        return "%02d:%02d.%02d".format(m, s, cs)
    }

    private const val GAP_MS = 40L
}
