package com.lyricstage.core.text

import java.text.BreakIterator
import java.util.Locale

/** Splits a lyric line into the words the stage animates. */
object Tokenizer {
    fun tokenize(text: String, script: Script): List<String> =
        if (script == Script.JAPANESE) splitJapanese(text) else text.split(WS).filter { it.isNotEmpty() }

    private val WS = Regex("\\s+")

    private fun isKana(s: String) = s.all { it in '぀'..'ゟ' }
    private fun isPunct(s: String) = s.all { !Character.isLetterOrDigit(it) }

    /** Particles a phrase usually ends on: ha ga wo ni de to mo he ba ya. */
    private const val PARTICLES = "はがをにでともへばや"

    /** Small kana and the long vowel mark belong to the character before them. */
    private const val TRAILING = "ゃゅょぁぃぅぇぉっ" +
        "ャュョァィゥェォッー"

    /** Japanese has no spaces: group word-break segments into short phrases (2 to 7 chars). */
    private fun splitJapanese(text: String): List<String> {
        val chunks = ArrayList<String>()
        for (part in text.split(WS).filter { it.isNotEmpty() }) {
            if (Script.detect(part) != Script.JAPANESE) {
                chunks += part
                continue
            }
            val it = BreakIterator.getWordInstance(Locale.JAPANESE)
            it.setText(part)
            val segs = ArrayList<String>()
            var start = it.first()
            var end = it.next()
            while (end != BreakIterator.DONE) {
                segs += splitLongKana(part.substring(start, end))
                start = end
                end = it.next()
            }
            var current = ""
            for (seg in segs) {
                val attach = current.isNotEmpty() && (isPunct(seg) || (isKana(seg) && seg.length <= 2) || current.length < 2)
                if (attach && current.length + seg.length <= 7) {
                    current += seg
                } else {
                    if (current.isNotEmpty()) chunks += current
                    current = seg
                }
            }
            if (current.isNotEmpty()) chunks += current
        }
        return chunks
    }

    /**
     * Some word breakers (the JVM's, for one) return a whole run of hiragana as one word.
     * Split runs longer than 7 characters after a particle, or else every 4 characters,
     * never in front of a small kana.
     */
    internal fun splitLongKana(seg: String): List<String> {
        if (seg.length <= 7 || !isKana(seg)) return listOf(seg)
        val out = ArrayList<String>()
        var cur = StringBuilder()
        for ((i, ch) in seg.withIndex()) {
            cur.append(ch)
            val next = seg.getOrNull(i + 1) ?: break
            if (next in TRAILING) continue
            if ((ch in PARTICLES && cur.length >= 2) || cur.length >= 4) {
                out += cur.toString()
                cur = StringBuilder()
            }
        }
        if (cur.isNotEmpty()) out += cur.toString()
        return out
    }
}
