package com.lyricstage.core.stage

import com.lyricstage.core.lyrics.LyricLine
import com.lyricstage.core.stage.templates.Filler

/**
 * Picks the words of a line that carry it, the way a singer leans on them, and colours them
 * with an accent. A word scores for being emotionally loaded, for being held long, for
 * ending the line, for being shouted, and for being a hook the song keeps returning to.
 * Filler words never qualify. One word on short lines, up to three on long ones; the play
 * seed only breaks ties, so the choice stays steady within a play.
 */
object Highlights {
    fun pick(line: LyricLine, playSeed: Int, songCounts: Map<String, Int> = emptyMap()): Set<Int> {
        val words = line.words
        val usable = words.indices.filter { !words[it].backing && !Filler.isFiller(words[it].text) && key(words[it].text).isNotEmpty() }
            .ifEmpty { words.indices.filter { !words[it].backing } }
        if (usable.isEmpty()) return emptySet()

        val durations = words.map { it.durationMs }.sorted()
        val median = durations.getOrNull(durations.size / 2)?.coerceAtLeast(1) ?: 1
        val lastContent = usable.last()
        val r = Rng(playSeed xor Rng.hash(line.text) xor 0x51ED27)

        val scored = usable.map { i ->
            val w = words[i]
            val k = key(w.text)
            var score = 0.0
            if (isEmotive(k)) score += 3.0
            val held = w.durationMs.toDouble() / median
            if (held >= 1.6) score += 1.5 + (held - 1.6).coerceAtMost(2.0)
            if (i == lastContent) score += 1.0
            if (w.text.contains('!') || (w.text.length > 2 && w.text == w.text.uppercase() && w.text.any { it.isLetter() && it.isUpperCase() })) score += 1.5
            if ((songCounts[k] ?: 0) >= 3) score += 1.0
            if (k.length >= 6) score += 0.4
            i to score + r.next() * 0.3
        }.sortedByDescending { it.second }

        val count = when {
            words.size <= 5 -> 1
            words.size <= 10 -> 2
            else -> 3
        }
        // the best word always shows; more only when they clearly matter
        val picked = scored.take(count).filterIndexed { rank, (_, s) -> rank == 0 || s >= 1.5 }
        return picked.map { it.first }.toSet()
    }

    /** Which of the three accents a line uses. */
    fun accentIndex(line: LyricLine, playSeed: Int): Int =
        Math.floorMod(Rng.hash(line.text) xor playSeed, 3)

    /** Word counts across a song, for spotting hooks. */
    fun songCounts(lines: List<LyricLine>): Map<String, Int> {
        val counts = HashMap<String, Int>()
        for (l in lines) for (w in l.words) {
            val k = key(w.text)
            if (k.isNotEmpty() && !Filler.isFiller(w.text)) counts.merge(k, 1, Int::plus)
        }
        return counts
    }

    private val STRIP = Regex("[^\\p{L}\\p{M}\\p{N}']")

    fun key(word: String): String = STRIP.replace(word.lowercase(), "")

    private fun isEmotive(k: String): Boolean = k in EMOTIVE || EMOTIVE_CJK.any { k.contains(it) }

    private val EMOTIVE = setOf(
        // English
        "love", "loved", "loving", "heart", "hearts", "heartbeat", "soul", "fire", "burn", "burning", "cry", "crying",
        "tears", "die", "dying", "alive", "forever", "never", "always", "dream", "dreams", "dreaming", "night", "light",
        "dark", "darkness", "pain", "hurt", "broken", "break", "fall", "falling", "fly", "flying", "free", "freedom",
        "wild", "run", "running", "dance", "dancing", "kiss", "touch", "hold", "home", "gone", "lost", "alone", "lonely",
        "together", "believe", "feel", "feeling", "hope", "heaven", "hell", "god", "sky", "stars", "star", "rain", "storm",
        "blood", "gold", "crazy", "baby", "sweet", "beautiful", "angel", "magic", "goodbye", "remember", "mine", "shine",
        "rise", "fight", "scream", "desire", "faith", "truth", "everything", "nothing", "world", "life", "death", "ocean",
        "sun", "moon", "fade", "crown", "king", "queen", "war", "peace", "ghost", "devil", "power", "higher", "shot",
        "opportunity", "moment", "tonight", "heartbreak", "wings", "hands", "eyes", "smile", "breathe", "wake",
        // romanised Hindi
        "dil", "pyaar", "pyar", "ishq", "mohabbat", "jaan", "khwab", "sapne", "aansu", "dard", "zindagi", "rab", "chand",
        "tanha", "saath", "yaad", "dhadkan", "junoon", "sanam", "khuda",
        // Hindi
        "दिल", "प्यार", "इश्क", "इश्क़", "मोहब्बत", "जान", "ख़्वाब", "ख्वाब", "सपने", "आँसू", "दर्द", "ज़िंदगी", "जिंदगी",
        "रब", "चाँद", "तन्हा", "साथ", "याद", "धड़कन", "जुनून", "सनम", "ख़ुदा", "बिन",
        // Bengali
        "ভালোবাসা", "প্রেম", "হৃদয়", "মন", "স্বপ্ন", "একলা", "আলো", "চোখ", "জীবন", "ডাক",
    )

    private val EMOTIVE_CJK = listOf("愛", "夢", "心", "涙", "君", "光", "空", "星", "好き", "永遠", "命", "願", "恋", "사랑", "꿈", "눈물")
}
