package com.lyricstage.core

import com.lyricstage.core.lyrics.EnhancedLrc
import com.lyricstage.core.lyrics.LyricsBuilder
import com.lyricstage.core.lyrics.LyricsKind
import com.lyricstage.core.lyrics.LyricsLookup
import com.lyricstage.core.lyrics.LyricsSources
import com.lyricstage.core.lyrics.Matching
import com.lyricstage.core.lyrics.MusixmatchClient
import com.lyricstage.core.lyrics.NeteaseClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourcesTest {
    @Test fun yrcBecomesWordTimedLyrics() {
        val yrc = """
            {"t":0,"c":[{"tx":"作词: "},{"tx":"Someone"}]}
            [18000,2100](18000,400,0)Never (18400,350,0)gon(18750,250,0)na (19000,300,0)give (19300,300,0)you (19600,500,0)up
            [20200,2000](20200,400,0)Never (20600,600,0)gonna (21300,300,0)let (21600,300,0)you (21900,300,0)down
        """.trimIndent()
        val lines = NeteaseClient.parseYrc(yrc)
        assertEquals(2, lines.size)
        assertEquals(listOf("Never", "gonna", "give", "you", "up"), lines[0].words.map { it.text })
        assertEquals(18400L, lines[0].words[1].startMs)
        assertEquals(19000L, lines[0].words[1].endMs)

        val lyrics = LyricsBuilder.fromLrc(EnhancedLrc.write(lines), 213_000)
        assertEquals(LyricsKind.WORD_SYNCED, lyrics.kind)
        val second = lyrics.lines[1].words
        assertEquals(listOf("Never", "gonna", "let", "you", "down"), second.map { it.text })
        // the 100 ms pause after "gonna" survives the trip through enhanced LRC
        assertEquals(21200L, second[1].endMs)
        assertEquals(22200L, second.last().endMs)
    }

    @Test fun richsyncBecomesWordTimedLyrics() {
        val body = """[{"ts":29.26,"te":31.81,"l":[{"c":"We're","o":0},{"c":" ","o":0.36},{"c":"no","o":0.4},{"c":" ","o":0.7},{"c":"strangers","o":0.75}],"x":"We're no strangers"}]"""
        val lines = MusixmatchClient.parseRichsync(body)
        assertEquals(listOf("We're", "no", "strangers"), lines.single().words.map { it.text })
        assertEquals(29_660L, lines.single().words[1].startMs)
        assertEquals(31_810L, lines.single().words.last().endMs)
        val lyrics = LyricsBuilder.fromLrc(EnhancedLrc.write(lines), 200_000)
        assertEquals(29_260L, lyrics.lines.single().startMs)
        assertEquals(31_810L, lyrics.lines.single().words.last().endMs)
    }

    @Test fun japaneseCharactersRegroupIntoPhrases() {
        val chars = "夢ならばどれほどよかったでしょう".toList()
        val lrc = "[00:12.00]" + chars.mapIndexed { i, c -> "<${EnhancedLrc.stamp(12_000L + i * 200)}>$c" }.joinToString("") + "<${EnhancedLrc.stamp(12_000L + chars.size * 200)}>"
        val line = LyricsBuilder.fromLrc(lrc, 200_000).lines.single()
        assertTrue("got ${line.words.map { it.text }}", line.words.size in 3..6)
        assertEquals(chars.joinToString(""), line.words.joinToString("") { it.text })
        assertEquals(12_000L, line.words.first().startMs)
    }

    @Test fun matchingRules() {
        assertTrue(Matching.titleMatches("Never Gonna Give You Up (Official Video)", "Never Gonna Give You Up"))
        assertFalse(Matching.titleMatches("Lemon", "Orange"))
        assertTrue(Matching.artistMatches("Rick Astley", listOf("Rick Astley")))
        assertTrue(Matching.durationMatches(213_000, 214_500))
        assertFalse(Matching.durationMatches(213_000, 240_000))
        assertTrue(Matching.isCredit("作词 : 米津玄師"))
        assertTrue(Matching.isCredit("Lyrics by: Someone"))
        assertFalse(Matching.isCredit("Never gonna give you up"))
    }

    private val word = LyricsLookup.Found(true, "[00:01.00]<00:01.00>a <00:01.50>b", false, "musixmatch")
    private val line = LyricsLookup.Found(true, "[00:01.00]a b", false, "lrclib")

    @Test fun wordTimedSourceWinsAndCancelsTheRest() = runBlocking {
        var slowFinished = false
        val sources = LyricsSources(
            listOf(
                LyricsSources.Source { _, _, _, _ -> line },
                LyricsSources.Source { _, _, _, _ -> delay(50); word },
                LyricsSources.Source { _, _, _, _ -> delay(5_000); slowFinished = true; LyricsLookup.NotFound },
            ),
        )
        val started = System.nanoTime()
        assertEquals(word, sources.find("t", "a", null, null))
        assertTrue(System.nanoTime() - started < 2_000_000_000L)
        assertFalse(slowFinished)
    }

    @Test fun bestFallsBackInOrder() = runBlocking {
        val plain = LyricsLookup.Found(false, "a\nb", false, "netease")
        val s = LyricsSources(
            listOf(
                LyricsSources.Source { _, _, _, _ -> LyricsLookup.NotFound },
                LyricsSources.Source { _, _, _, _ -> plain },
                LyricsSources.Source { _, _, _, _ -> throw IllegalStateException("broken source") },
            ),
        )
        assertEquals(plain, s.find("t", "a", null, null))
        val down = LyricsSources(listOf(LyricsSources.Source { _, _, _, _ -> LyricsLookup.Unavailable }))
        assertEquals(LyricsLookup.Unavailable, down.find("t", "a", null, null))
        // two line-timed answers: the first source (LRCLIB) wins the tie
        val other = line.copy(source = "netease")
        assertEquals(line, LyricsSources.best(listOf(1 to other, 0 to line)))
    }
}
