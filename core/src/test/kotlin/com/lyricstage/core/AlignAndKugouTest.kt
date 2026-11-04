package com.lyricstage.core

import com.lyricstage.core.lyrics.KugouClient
import com.lyricstage.core.lyrics.LineAligner
import com.lyricstage.core.lyrics.LrcParser
import com.lyricstage.core.lyrics.LyricsLookup
import com.lyricstage.core.lyrics.LyricsSources
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater

class AlignAndKugouTest {
    private val lineTimed = """
        [00:18.00]Never gonna give you up
        [00:20.20]Never gonna let you down
        [00:22.40]Never gonna run around and desert you
        [00:26.80]Never gonna make you cry
        [00:29.00]Never gonna say goodbye
    """.trimIndent()

    /** The same lines, word-timed, but 1.8 s early, as if timed to a release with a shorter intro. */
    private val wordTimedEarly = """
        [00:16.20]<00:16.20>Never <00:16.60>gonna <00:17.00>give <00:17.30>you <00:17.60>up
        [00:18.40]<00:18.40>Never <00:18.80>gonna <00:19.20>let <00:19.50>you <00:19.80>down
        [00:20.60]<00:20.60>Never <00:21.00>gonna <00:21.40>run <00:21.80>around
        [00:25.00]<00:25.00>Never <00:25.40>gonna <00:25.80>make <00:26.10>you <00:26.40>cry
        [00:27.20]<00:27.20>Never <00:27.60>gonna <00:28.00>say <00:28.40>goodbye
    """.trimIndent()

    @Test fun measuresAndCorrectsAConstantOffset() {
        // "run around" differs in text from the reference line, so 4 of 5 lines match
        assertEquals(1800L, LineAligner.offset(wordTimedEarly, lineTimed))
        val fixed = LineAligner.shift(wordTimedEarly, 1800)
        val lines = LrcParser.parse(fixed)
        assertEquals(18_000L, lines[0].startMs)
        assertEquals(18_400L, lines[0].wordStarts!![1].startMs)
        assertEquals(LineAligner.offset(fixed, lineTimed), null)
    }

    @Test fun leavesSmallOrInconsistentDifferencesAlone() {
        val close = LineAligner.shift(wordTimedEarly, 1700) // now 100 ms off: hand-sync noise
        assertNull(LineAligner.offset(close, lineTimed))
        assertNull(LineAligner.offset(wordTimedEarly, "[00:01.00]Something else entirely\n[00:02.00]Other"))
    }

    @Test fun sourcesAlignWordTimingToTheLineTimedCopy() = runBlocking {
        val sources = LyricsSources(
            listOf(
                LyricsSources.Source { _, _, _, _ -> delay(150); LyricsLookup.Found(true, lineTimed, false, "lrclib") },
                LyricsSources.Source { _, _, _, _ -> LyricsLookup.Found(true, wordTimedEarly, false, "musixmatch") },
            ),
        )
        val r = sources.find("t", "a", null, null) as LyricsLookup.Found
        assertEquals("musixmatch", r.source)
        assertEquals(18_000L, LrcParser.parse(r.raw)[0].startMs)
    }

    @Test fun wanderingOffsetsAreFixedLineByLine() {
        // early by 1.5 s at first, then by 0.5 s: no single shift fits
        val wandering = """
            [00:16.50]<00:16.50>Never <00:16.90>gonna <00:17.30>give <00:17.60>you <00:17.90>up
            [00:18.70]<00:18.70>Never <00:19.10>gonna <00:19.50>let <00:19.80>you <00:20.10>down
            [00:21.90]<00:21.90>Never <00:22.30>gonna <00:22.70>run <00:23.10>around and desert you
            [00:26.30]<00:26.30>Never <00:26.70>gonna <00:27.10>make <00:27.40>you <00:27.70>cry
            [00:28.50]<00:28.50>Never <00:28.90>gonna <00:29.30>say <00:29.70>goodbye
        """.trimIndent()
        assertNull(LineAligner.offset(wandering, lineTimed))
        val fixed = LrcParser.parse(LineAligner.align(wandering, lineTimed))
        assertEquals(listOf(18_000L, 20_200L, 22_400L, 26_800L, 29_000L), fixed.map { it.startMs })
        // word spacing inside a line is kept
        assertEquals(18_400L, fixed[0].wordStarts!![1].startMs)
        assertEquals(29_800L, fixed[4].wordStarts!![2].startMs)
    }

    @Test fun waitsForASlowLineTimedCopy() = runBlocking {
        val sources = LyricsSources(
            listOf(
                LyricsSources.Source { _, _, _, _ -> delay(2_500); LyricsLookup.Found(true, lineTimed, false, "lrclib") },
                LyricsSources.Source { _, _, _, _ -> LyricsLookup.Found(true, wordTimedEarly, false, "musixmatch") },
            ),
        )
        val r = sources.find("t", "a", null, null) as LyricsLookup.Found
        assertEquals(18_000L, LrcParser.parse(r.raw)[0].startMs)
    }

    @Test fun krcDecodesAndParsesToWordTiming() {
        val krc = "﻿[ti:Never Gonna Give You Up]\n[language:eyJjb250ZW50IjpbXX0=]\n" +
            "[18000,2100]<0,400,0>Never <400,600,0>gonna <1000,300,0>give <1300,300,0>you <1600,500,0>up\n"
        val lines = KugouClient.parseKrc(KugouClient.decodeKrc(encode(krc)))
        assertEquals(1, lines.size)
        assertEquals(listOf("Never", "gonna", "give", "you", "up"), lines[0].words.map { it.text })
        assertEquals(18_400L, lines[0].words[1].startMs)
        assertEquals(19_000L, lines[0].words[1].endMs)
        assertTrue(lines[0].joinWithSpaces)
    }

    private fun encode(text: String): String {
        val key = intArrayOf(64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45, 206, 210, 110, 105)
        val d = Deflater().apply { setInput(text.toByteArray()); finish() }
        val out = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        val z = out.toByteArray()
        val x = ByteArray(z.size) { i -> (z[i].toInt() xor key[i % 16]).toByte() }
        return Base64.getEncoder().encodeToString("krc1".toByteArray() + x)
    }
}
