package com.lyricstage.core.lyrics

import com.lyricstage.core.text.Graphemes
import com.lyricstage.core.text.Script
import com.lyricstage.core.text.Tokenizer

/**
 * Estimates word timings for lines that only have a line timestamp.
 *
 * Each word gets a weight that approximates how long it takes to sing: vowel groups for
 * Latin text, grapheme clusters (aksharas) for Indic scripts, characters (roughly morae)
 * for Japanese and Korean. A line's sung span is split in proportion to those weights,
 * leaving a short breath before the line ends.
 */
object WordTimingEstimator {
    private val VOWEL_GROUP = Regex("[aeiouyàáâãäåèéêëìíîïòóôõöùúûüýÿæœ]+", RegexOption.IGNORE_CASE)

    /** Rough seconds per weight unit at a typical pace, per script. */
    fun msPerWeight(script: Script): Double = when (script) {
        Script.JAPANESE -> 160.0
        Script.KOREAN -> 200.0
        Script.DEVANAGARI, Script.BENGALI -> 210.0
        else -> 320.0
    }

    private const val LAST_WORD_HOLD = 1.6
    private const val FILLER_SHARE = 0.7
    private val FILLERS = setOf("a", "an", "the", "to", "of", "in", "on", "at", "and", "or", "but", "i", "me", "my", "we", "it", "is", "be", "you", "your")

    fun weight(token: String, script: Script): Double {
        val letters = token.filter { Character.isLetterOrDigit(it) || Character.getType(it).let { t -> t == Character.NON_SPACING_MARK.toInt() || t == Character.COMBINING_SPACING_MARK.toInt() } }
        if (letters.isEmpty()) return 0.3
        return when {
            script.isCjk -> letters.length.toDouble()
            script.isIndic -> {
                // clusters ending in a vowel sign or bare consonant each carry one syllable
                Graphemes.count(letters).toDouble().coerceAtLeast(1.0)
            }
            else -> VOWEL_GROUP.findAll(letters).count().coerceAtLeast(1).toDouble()
        }
    }

    fun lineWeight(text: String, script: Script): Double =
        Tokenizer.tokenize(text, script).sumOf { weight(it, script) }

    /**
     * Split [tokens] across [startMs, startMs + spanMs). The last 8 % (at least 120 ms,
     * at most 400 ms) is kept free so the line breathes before the next one.
     */
    fun estimate(tokens: List<String>, script: Script, startMs: Long, spanMs: Long): List<LyricWord> {
        if (tokens.isEmpty()) return emptyList()
        val pause = (spanMs * 0.08).toLong().coerceIn(120, 400).coerceAtMost(spanMs / 4)
        val sung = (spanMs - pause).coerceAtLeast(tokens.size * 60L)
        val weights = tokens.mapIndexed { i, tok ->
            var w = weight(tok, script)
            // singers hold the end of a line; little function words go by quickly
            if (i == tokens.lastIndex && tokens.size > 1) w *= LAST_WORD_HOLD
            else if (script == Script.LATIN && FILLERS.contains(tok.lowercase().trim(',', '.', '!', '?', '\'', '"'))) w *= FILLER_SHARE
            w
        }
        val total = weights.sum().takeIf { it > 0 } ?: 1.0
        val out = ArrayList<LyricWord>(tokens.size)
        var t = startMs.toDouble()
        for ((i, tok) in tokens.withIndex()) {
            val d = weights[i] / total * sung
            out += LyricWord(tok, t.toLong(), (t + d).toLong())
            t += d
        }
        return out
    }
}
