package com.lyricstage.core.lyrics

import java.text.Normalizer
import kotlin.math.abs

/** Decides whether a search result from another service is the song that is playing. */
object Matching {
    private val NON_WORD = Regex("[^\\p{L}\\p{M}\\p{N}]+")

    fun norm(s: String): String {
        val n = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFKD).replace(Regex("\\p{Mn}+"), "")
        return NON_WORD.replace(n, " ").trim()
    }

    /** Titles match when one contains the other after cleaning ("Song" vs "Song (Live)"). */
    fun titleMatches(wanted: String, candidate: String): Boolean {
        val a = norm(TitleCleaner.cleanTitle(wanted))
        val b = norm(TitleCleaner.cleanTitle(candidate))
        return a.isNotEmpty() && b.isNotEmpty() && (a == b || a.contains(b) || b.contains(a))
    }

    /** Artists match when any credited artist on either side shares a name with the other. */
    fun artistMatches(wanted: String, candidates: List<String>): Boolean {
        val a = norm(TitleCleaner.cleanArtist(wanted))
        if (a.isEmpty()) return true
        return candidates.any { c ->
            val b = norm(c)
            b.isNotEmpty() && (a == b || a.contains(b) || b.contains(a))
        }
    }

    fun durationMatches(wantedMs: Long?, candidateMs: Long?, slackMs: Long = 4_000): Boolean =
        wantedMs == null || wantedMs <= 0 || candidateMs == null || candidateMs <= 0 || abs(wantedMs - candidateMs) <= slackMs

    /** Credits some services put at the top of the lyrics ("Lyrics by: ...", "作词 : ..."). */
    private val CREDIT = Regex(
        """^\s*(作词|作曲|编曲|制作人|混音|母带|和声|吉他|贝斯|鼓|录音|监制|出品|词|曲|lyrics?\s*by|written\s*by|composed\s*by|produced\s*by|arranged\s*by|composer|lyricist|producer)\s*[:：]""",
        RegexOption.IGNORE_CASE,
    )

    fun isCredit(text: String): Boolean = CREDIT.containsMatchIn(text)
}
