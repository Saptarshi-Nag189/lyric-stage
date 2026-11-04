package com.lyricstage.core.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.roundToLong

@Serializable
data class LrclibRecord(
    val id: Long = 0,
    @SerialName("trackName") val trackName: String = "",
    @SerialName("artistName") val artistName: String = "",
    @SerialName("albumName") val albumName: String? = null,
    val duration: Double = 0.0,
    val instrumental: Boolean = false,
    @SerialName("plainLyrics") val plainLyrics: String? = null,
    @SerialName("syncedLyrics") val syncedLyrics: String? = null,
)

/** What a lookup found. */
sealed interface LyricsLookup {
    /** Raw LRC or plain text, ready for [LyricsBuilder]. */
    data class Found(
        val synced: Boolean,
        val raw: String,
        val instrumental: Boolean,
        /** "lrclib", "netease" or "musixmatch". */
        val source: String = "lrclib",
    ) : LyricsLookup {
        /** Per-word timings (enhanced LRC tags), the best a source can offer. */
        val wordSynced: Boolean get() = synced && LrcParser.hasWordTags(raw)
    }
    data object NotFound : LyricsLookup
    /** LRCLIB itself failed (network, 5xx): worth retrying later. */
    data object Unavailable : LyricsLookup
}

/** LRCLIB client. The only network access the app makes. */
class LrclibClient(
    private val http: OkHttpClient,
    private val baseUrl: HttpUrl = "https://lrclib.net/api/".toHttpUrl(),
    private val userAgent: String = USER_AGENT,
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private class UpstreamDown : IOException("LRCLIB unavailable")

    /**
     * Looks a track up. The exact `/get` lookup and the fuzzy `/search` run in parallel, so a
     * miss on `/get` costs no extra round trip; the search is cancelled when `/get` already
     * returns synced lyrics.
     */
    suspend fun find(title: String, artist: String, album: String?, durationMs: Long?): LyricsLookup = coroutineScope {
        val cleanTitle = TitleCleaner.cleanTitle(title)
        val cleanArtist = TitleCleaner.cleanArtist(artist)
        val durationSec = durationMs?.takeIf { it > 0 }?.let { (it / 1000.0).roundToLong() }

        val getUrl = baseUrl.newBuilder().addPathSegment("get")
            .addQueryParameter("track_name", cleanTitle)
            .addQueryParameter("artist_name", cleanArtist)
            .apply {
                if (!album.isNullOrBlank()) addQueryParameter("album_name", album)
                if (durationSec != null) addQueryParameter("duration", durationSec.toString())
            }.build()
        val searchUrl = baseUrl.newBuilder().addPathSegment("search")
            .addQueryParameter("q", "$cleanArtist $cleanTitle")
            .build()

        // Each result is null for "not found" and a failure for "LRCLIB unreachable".
        // The exact match answers most songs, so the search is only sent when it does not.
        val exact = runCatching { getJson(getUrl)?.let { json.decodeFromString(LrclibRecord.serializer(), it) } }
        val exactResult = exact.getOrNull()?.let(::toResult)
        if (exactResult != null && (exactResult.synced || exactResult.instrumental)) {
            return@coroutineScope exactResult
        }

        val search = runCatching { getJson(searchUrl)?.let { json.decodeFromString<List<LrclibRecord>>(it) } ?: emptyList() }
        val results = search.getOrNull() ?: return@coroutineScope exactResult ?: LyricsLookup.Unavailable
        if (exact.isFailure && results.isEmpty()) return@coroutineScope LyricsLookup.Unavailable

        // prefer synced results closest in length to the playing track
        val candidates = results
            .filter { durationSec == null || abs(it.duration - durationSec) <= 8 }
            .sortedWith(
                compareBy<LrclibRecord> { if (it.syncedLyrics.isNullOrBlank()) 1 else 0 }
                    .thenBy { if (durationSec == null) 0.0 else abs(it.duration - durationSec) },
            )
        for (c in candidates) toResult(c)?.let { return@coroutineScope it }
        exactResult ?: LyricsLookup.NotFound
    }

    private fun toResult(rec: LrclibRecord): LyricsLookup.Found? = when {
        rec.instrumental -> LyricsLookup.Found(synced = false, raw = "", instrumental = true)
        !rec.syncedLyrics.isNullOrBlank() -> LyricsLookup.Found(synced = true, raw = rec.syncedLyrics, instrumental = false)
        !rec.plainLyrics.isNullOrBlank() -> LyricsLookup.Found(synced = false, raw = rec.plainLyrics, instrumental = false)
        else -> null
    }

    /** Body text, null on 404, [UpstreamDown] after retries on errors. Cancellable. */
    private suspend fun getJson(url: HttpUrl, attempts: Int = 2): String? {
        repeat(attempts) { attempt ->
            try {
                val req = Request.Builder().url(url).header("User-Agent", userAgent).build()
                val (code, body) = http.fetch(req)
                if (code == 404) return null
                if (code in 200..299) return body
            } catch (e: IOException) {
                // network error or timeout: retry
            }
            if (attempt < attempts - 1) delay(400L * (attempt + 1))
        }
        throw UpstreamDown()
    }

    companion object {
        const val USER_AGENT = "LyricStage/1.0 (+https://github.com/Saptarshi-Nag189/lyric-stage; personal Android lyrics viewer)"
    }
}
