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
    @Test fun musixmatchRefusesRetiredIdentityToken() = runBlocking {
        val server = okhttp3.mockwebserver.MockWebServer()
        val zeros = "0".repeat(56)
        server.enqueue(okhttp3.mockwebserver.MockResponse().setBody("""{"message":{"header":{"status_code":200},"body":{"user_token":"$zeros"}}}"""))
        server.start()
        val store = MusixmatchClient.TokenStore.InMemory().apply { token = "old-desktop-token" }
        val client = MusixmatchClient(okhttp3.OkHttpClient(), server.url("/ws/1.1/"), store = store)
        assertEquals(LyricsLookup.Unavailable, client.find("Song", "Artist", null, 200_000))
        val req = server.takeRequest()
        // the stored desktop-era token is not reused; a new one is asked for under the Android identity
        assertTrue(req.path!!.startsWith("/ws/1.1/token.get"))
        assertEquals("android-player-v1.0", req.requestUrl!!.queryParameter("app_id"))
        assertEquals(null, req.requestUrl!!.queryParameter("usertoken"))
        // the all-zero token is never kept
        assertEquals("old-desktop-token", store.token)
        server.shutdown()
    }

    @Test fun neteaseAsksForLatestLyricVersions() = runBlocking {
        val server = okhttp3.mockwebserver.MockWebServer()
        server.enqueue(okhttp3.mockwebserver.MockResponse().setBody("""{"result":{"songs":[{"id":7,"name":"Never Gonna Give You Up","artists":[{"name":"Rick Astley"}],"duration":214018}]},"code":200}"""))
        server.enqueue(okhttp3.mockwebserver.MockResponse().setBody("""{"code":200,"yrc":{"lyric":"[18000,2100](18000,400,0)Never (18400,600,0)gonna (19000,300,0)give"}}"""))
        server.start()
        val r = NeteaseClient(okhttp3.OkHttpClient(), server.url("/")).find("Never Gonna Give You Up", "Rick Astley", 214_000)
        assertTrue(r is LyricsLookup.Found && r.synced)
        server.takeRequest()
        val lyric = server.takeRequest().requestUrl!!
        for (k in listOf("lv", "kv", "rv", "yv")) assertEquals(k, "-1", lyric.queryParameter(k))
        server.shutdown()
    }

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

    @Test fun wordTimedSourceWinsAndTheRestAreNeverAsked() = runBlocking {
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

    @Test fun kugouIsAskedOnlyWhenMusixmatchAndNeteaseHaveNothing() = runBlocking {
        val asked = ArrayList<String>()
        val fromKugou = word.copy(source = "kugou")
        val sources = LyricsSources(
            listOf(
                LyricsSources.Source { _, _, _, _ -> asked += "lrclib"; line },
                LyricsSources.Source { _, _, _, _ -> asked += "musixmatch"; LyricsLookup.NotFound },
                LyricsSources.Source { _, _, _, _ -> asked += "netease"; LyricsLookup.Unavailable },
                LyricsSources.Source { _, _, _, _ -> asked += "kugou"; fromKugou },
            ),
        )
        assertEquals(fromKugou, sources.find("t", "a", null, null))
        // the chain runs in trust order; LRCLIB runs beside it, so only its presence is fixed
        assertEquals(listOf("musixmatch", "netease", "kugou"), asked.filter { it != "lrclib" })
        assertTrue(asked.contains("lrclib"))
    }

    @Test fun musixmatchWinsEvenWhenKugouWouldAnswerFirst() = runBlocking {
        val asked = ArrayList<String>()
        val sources = LyricsSources(
            listOf(
                LyricsSources.Source { _, _, _, _ -> asked += "lrclib"; line },
                LyricsSources.Source { _, _, _, _ -> asked += "musixmatch"; delay(80); word },
                LyricsSources.Source { _, _, _, _ -> asked += "netease"; LyricsLookup.NotFound },
                LyricsSources.Source { _, _, _, _ -> asked += "kugou"; word.copy(source = "kugou") },
            ),
        )
        assertEquals(word, sources.find("t", "a", null, null))
        // the chain stops at Musixmatch: NetEase and Kugou are never asked. LRCLIB runs
        // beside it, so only its presence is fixed, not its place in the log
        assertEquals(listOf("musixmatch"), asked.filter { it != "lrclib" })
        assertTrue(asked.contains("lrclib"))
    }

    @Test fun aSlowReferenceDoesNotHoldTheAnswer() = runBlocking {
        // LRCLIB never answers; the word-timed answer still goes out after the wait
        val sources = LyricsSources(
            listOf(
                LyricsSources.Source { _, _, _, _ -> delay(30_000); line },
                LyricsSources.Source { _, _, _, _ -> word },
            ),
        )
        val started = System.nanoTime()
        assertEquals(word, sources.find("t", "a", null, null))
        // 4 s wait for the reference, not the 30 s it would take
        assertTrue(System.nanoTime() - started < 6_000_000_000L)
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
