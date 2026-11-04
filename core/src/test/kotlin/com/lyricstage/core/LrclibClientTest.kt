package com.lyricstage.core

import com.lyricstage.core.lyrics.LrclibClient
import com.lyricstage.core.lyrics.LyricsLookup
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class LrclibClientTest {
    private val server = MockWebServer()
    private val client by lazy { LrclibClient(OkHttpClient(), server.url("/api/")) }
    private val seen = CopyOnWriteArrayList<RecordedRequest>()

    /** Answers /get and /search independently, since the client asks both at once. */
    private fun route(get: () -> MockResponse, search: () -> MockResponse) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                seen += request
                return if (request.path!!.startsWith("/api/get")) get() else search()
            }
        }
    }

    @After fun tearDown() = server.shutdown()

    @Test fun exactHitWithSyncedLyrics() = runBlocking {
        route(
            get = { MockResponse().setBody("""{"id":1,"trackName":"Song","artistName":"A","duration":200,"instrumental":false,"plainLyrics":"x","syncedLyrics":"[00:01.00]x"}""") },
            // a slow search must not delay an exact hit
            search = { MockResponse().setBody("[]").setBodyDelay(5, TimeUnit.SECONDS) },
        )
        val started = System.nanoTime()
        val r = client.find("Song (Official Video)", "A feat. B", "Album", 200_400)
        assertEquals(LyricsLookup.Found(true, "[00:01.00]x", false), r)
        assertTrue("waited for the search", System.nanoTime() - started < 3_000_000_000L)
        val get = seen.first { it.path!!.startsWith("/api/get") }
        assertEquals("/api/get?track_name=Song&artist_name=A&album_name=Album&duration=200", get.path)
        assertTrue(get.getHeader("User-Agent")!!.startsWith("LyricStage/"))
    }

    @Test fun fallsBackToSearchPreferringSyncedAndCloseDuration() = runBlocking {
        route(
            get = { MockResponse().setResponseCode(404) },
            search = {
                MockResponse().setBody(
                    """[{"id":1,"duration":300,"syncedLyrics":"[00:01.00]far"},
                       {"id":2,"duration":201,"plainLyrics":"plain"},
                       {"id":3,"duration":203,"syncedLyrics":"[00:01.00]near"}]""",
                )
            },
        )
        val r = client.find("Song", "A", null, 200_000)
        assertEquals(LyricsLookup.Found(true, "[00:01.00]near", false), r)
        assertTrue(seen.any { it.path == "/api/search?q=A%20Song" })
    }

    @Test fun plainExactLosesToSyncedSearch() = runBlocking {
        route(
            get = { MockResponse().setBody("""{"id":1,"duration":200,"plainLyrics":"plain only"}""") },
            search = { MockResponse().setBody("""[{"id":2,"duration":200,"syncedLyrics":"[00:01.00]synced"}]""") },
        )
        assertEquals(LyricsLookup.Found(true, "[00:01.00]synced", false), client.find("Song", "A", null, 200_000))
    }

    @Test fun notFound() = runBlocking {
        route(get = { MockResponse().setResponseCode(404) }, search = { MockResponse().setBody("[]") })
        assertEquals(LyricsLookup.NotFound, client.find("Song", "A", null, null))
    }

    @Test fun unavailableAfterRetries() = runBlocking {
        route(get = { MockResponse().setResponseCode(503) }, search = { MockResponse().setResponseCode(503) })
        assertEquals(LyricsLookup.Unavailable, client.find("Song", "A", null, null))
    }
}
